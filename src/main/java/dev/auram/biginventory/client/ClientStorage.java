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

import java.util.ArrayList;
import java.util.List;

public final class ClientStorage {
    private static Hud hud;
    private static int tokens;
    private static View view;

    static Hud hud() { return hud; }

    public static void hud(Hud message) { hud = message; }

    static View open(int menu) {
        if (view != null) close(view);
        view = new View(menu, ++tokens);
        StorageNetwork.request(new ViewRequest(menu, view.token, true));
        return view;
    }

    static void close(View closing) {
        if (closing != null && view == closing) {
            StorageNetwork.request(new ViewRequest(view.menu, view.token, false));
            view = null;
        }
    }

    static void reset() {
        view = null;
        hud = null;
    }

    public static void start(ViewStart start) {
        if (view != null && view.token == start.token()) {
            view.revision = start.revision();
            view.total = start.entries();
            view.entries.clear();
            view.changes++;
        }
    }

    public static void page(ViewPage page) {
        if (view != null && view.token == page.token() && view.revision == page.revision()) {
            view.entries.addAll(page.entries());
            view.changes++;
        }
    }

    public static void ack(ViewAck ack) {
        if (view != null && view.token == ack.token()) view.pending = false;
    }

    static final class View {
        final int menu;
        final int token;
        final List<Entry> entries = new ArrayList<>();
        int revision;
        int total;
        int changes;
        boolean pending;

        View(int menu, int token) {
            this.menu = menu;
            this.token = token;
        }

        void act(int id, Gesture gesture) {
            if (view != this || pending || revision == 0) return;
            pending = true;
            StorageNetwork.action(new Action(menu, token, revision, id, gesture));
        }
    }

    private ClientStorage() {}
}
