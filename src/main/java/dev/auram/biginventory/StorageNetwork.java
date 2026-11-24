package dev.auram.biginventory;

import dev.auram.biginventory.client.ClientStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class StorageNetwork {
    private static final String VERSION = "5";
    private static final int PAGE_SIZE = 24;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BigInventory.MOD_ID, "storage"), () -> VERSION, VERSION::equals, VERSION::equals);

    static void register() {
        CHANNEL.messageBuilder(Hud.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Hud::encode).decoder(Hud::decode).consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStorage.hud(msg))).add();
        CHANNEL.messageBuilder(ViewRequest.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ViewRequest::encode).decoder(ViewRequest::decode).consumerMainThread((msg, ctx) ->
                        StorageSessions.view(ctx.get().getSender(), msg)).add();
        CHANNEL.messageBuilder(ViewStart.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ViewStart::encode).decoder(ViewStart::decode).consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStorage.start(msg))).add();
        CHANNEL.messageBuilder(ViewPage.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ViewPage::encode).decoder(ViewPage::decode).consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStorage.page(msg))).add();
        CHANNEL.messageBuilder(Action.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Action::encode).decoder(Action::decode).consumerMainThread((msg, ctx) ->
                        StorageSessions.action(ctx.get().getSender(), msg)).add();
        CHANNEL.messageBuilder(ViewAck.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ViewAck::encode).decoder(ViewAck::decode).consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStorage.ack(msg))).add();
        ViewportNetwork.register(CHANNEL);
    }

    public static void request(ViewRequest request) { CHANNEL.sendToServer(request); }

    public static void action(Action action) { CHANNEL.sendToServer(action); }

    static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    static void send(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public record Hud(long used, long capacity, int slot, long heldCount, int footprintFactor, boolean endant) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(used).writeVarLong(capacity).writeVarInt(slot).writeVarLong(heldCount);
            buf.writeVarInt(footprintFactor).writeBoolean(endant);
        }
        static Hud decode(FriendlyByteBuf buf) {
            return new Hud(buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(),
                    buf.readVarInt(), buf.readBoolean());
        }
    }

    public record ViewRequest(int menu, int token, boolean open, @Nullable ItemStack creativeCursor) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(menu).writeVarInt(token).writeBoolean(open).writeBoolean(creativeCursor != null);
            if (creativeCursor != null) buf.writeNbt(creativeCursor.save(new CompoundTag()));
        }
        static ViewRequest decode(FriendlyByteBuf buf) {
            return new ViewRequest(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean() ? ItemStack.of(buf.readNbt()) : null);
        }
    }

    public record ViewStart(int token, int revision, int entries) {
        void encode(FriendlyByteBuf buf) { buf.writeVarInt(token).writeVarInt(revision).writeVarInt(entries); }
        static ViewStart decode(FriendlyByteBuf buf) {
            return new ViewStart(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    public record Entry(int id, ItemStack icon, long count, long overflowCount) {}

    public record ViewPage(int token, int revision, List<Entry> entries) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(token).writeVarInt(revision).writeVarInt(entries.size());
            for (Entry entry : entries) {
                buf.writeVarInt(entry.id());
                buf.writeNbt(entry.icon().save(new CompoundTag()));
                buf.writeVarLong(entry.count());
                buf.writeVarLong(entry.overflowCount());
            }
        }
        static ViewPage decode(FriendlyByteBuf buf) {
            int token = buf.readVarInt();
            int revision = buf.readVarInt();
            int len = buf.readVarInt();
            if (len < 0 || len > PAGE_SIZE) throw new IllegalArgumentException("Invalid BigInventory page size");
            List<Entry> entries = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                entries.add(new Entry(buf.readVarInt(), ItemStack.of(buf.readNbt()), buf.readVarLong(), buf.readVarLong()));
            }
            return new ViewPage(token, revision, entries);
        }
    }

    public enum Gesture { DEPOSIT, DEPOSIT_ONE, TAKE, TAKE_HALF, SHIFT_TAKE, CREATIVE_TRASH, CREATIVE_CLEAR, STORE_HOTBAR }

    public record Action(int menu, int token, int revision, int entry, Gesture gesture) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(menu).writeVarInt(token).writeVarInt(revision).writeVarInt(entry);
            buf.writeEnum(gesture);
        }
        static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readEnum(Gesture.class));
        }
    }

    public record ViewAck(int token, int revision) {
        void encode(FriendlyByteBuf buf) { buf.writeVarInt(token).writeVarInt(revision); }
        static ViewAck decode(FriendlyByteBuf buf) { return new ViewAck(buf.readVarInt(), buf.readVarInt()); }
    }

    static int pageEnd(int start, int size) {
        return Math.min(size, start + PAGE_SIZE);
    }

    private StorageNetwork() {}
}
