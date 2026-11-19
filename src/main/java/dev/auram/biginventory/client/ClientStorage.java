package dev.auram.biginventory.client;

import dev.auram.biginventory.StorageNetwork;
import dev.auram.biginventory.StorageNetwork.Action;
import dev.auram.biginventory.StorageNetwork.Entry;
import dev.auram.biginventory.StorageNetwork.Gesture;
import dev.auram.biginventory.StorageNetwork.Hud;
import dev.auram.biginventory.StorageNetwork.ViewAck;
import dev.auram.biginventory.StorageNetwork.ViewPage;
import dev.auram.biginventory.StorageNetwork.ViewRequest;
import dev.auram.biginventory.StorageNetwork.ViewStart;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class ClientStorage {
    private static Hud hud;
    private static int tokens;
    private static View view;

    static Hud hud() { return hud; }

    public static void hud(Hud message) { hud = message; }

    static View open(int menu) {
        return open(menu, null);
    }

    static View open(int menu, @Nullable ItemStack creativeCursor) {
        if (view != null) close(view);
        view = new View(menu, ++tokens);
        StorageNetwork.request(new ViewRequest(menu, view.token, true, creativeCursor));
        return view;
    }

    static void close(View closing) {
        if (closing != null && view == closing) {
            StorageNetwork.request(new ViewRequest(view.menu, view.token, false, null));
            view = null;
        }
    }

    static void reset() {
        view = null;
        hud = null;
    }

    public static void start(ViewStart start) {
        if (view != null && view.token == start.token()) {
            view.incomingRevision = start.revision();
            view.total = start.entries();
            view.incoming.clear();
            view.applyCompleteView();
        }
    }

    public static void page(ViewPage page) {
        if (view != null && view.token == page.token() && view.incomingRevision == page.revision()) {
            view.incoming.addAll(page.entries());
            view.applyCompleteView();
        }
    }

    public static void ack(ViewAck ack) {
        if (view != null && view.token == ack.token()) view.pending = false;
    }

    public static void accountOverflow(StackedContents contents) {
        if (view == null) return;
        for (Entry entry : view.entries) {
            ItemStack stack = entry.icon();
            if (entry.overflowCount() > 0 && !stack.isDamaged() && !stack.isEnchanted() && !stack.hasCustomHoverName()) {
                int count = (int) Math.min(Integer.MAX_VALUE, entry.overflowCount());
                contents.accountStack(stack.copyWithCount(count), count);
            }
        }
    }

    static final class View {
        final int menu;
        final int token;
        final List<Entry> incoming = new ArrayList<>();
        List<Entry> entries = List.of();
        int incomingRevision;
        int revision;
        int total;
        int changes;
        boolean pending;

        View(int menu, int token) {
            this.menu = menu;
            this.token = token;
        }

        void act(int id, Gesture gesture) {
            if (view != this || pending || revision == 0 || revision != incomingRevision) return;
            pending = true;
            StorageNetwork.action(new Action(menu, token, revision, id, gesture));
        }

        void applyCompleteView() {
            if (incoming.size() == total) {
                entries = List.copyOf(incoming);
                revision = incomingRevision;
                changes++;
                var player = Minecraft.getInstance().player;
                if (player != null) player.getInventory().setChanged();
            }
        }
    }

    private ClientStorage() {}
}
