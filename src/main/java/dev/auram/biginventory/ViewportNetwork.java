package dev.auram.biginventory;

import dev.auram.biginventory.client.ClientViewport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class ViewportNetwork {
    static final int INDEX_BATCH_SIZE = 24;

    static void register(SimpleChannel channel) {
        channel.messageBuilder(Access.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Access::encode).decoder(Access::decode).consumerMainThread((msg, ctx) ->
                        ViewportSessions.access(ctx.get().getSender(), msg)).add();
        channel.messageBuilder(PageRequest.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PageRequest::encode).decoder(PageRequest::decode).consumerMainThread((msg, ctx) ->
                        ViewportSessions.page(ctx.get().getSender(), msg)).add();
        channel.messageBuilder(Index.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Index::encode).decoder(Index::decode).consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientViewport.index(msg))).add();
        channel.messageBuilder(Page.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Page::encode).decoder(Page::decode).consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientViewport.page(msg))).add();
        channel.messageBuilder(Ready.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Ready::encode).decoder(Ready::decode).consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientViewport.ready(msg))).add();
        channel.messageBuilder(CreativeTrash.class, 11, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreativeTrash::encode).decoder(CreativeTrash::decode).consumerMainThread((msg, ctx) ->
                        ViewportSessions.trash(ctx.get().getSender(), msg)).add();
        channel.messageBuilder(BulkMove.class, 12, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BulkMove::encode).decoder(BulkMove::decode).consumerMainThread((msg, ctx) ->
                        ViewportSessions.bulkMove(ctx.get().getSender(), msg)).add();
    }

    public static void request(Access message) {
        StorageNetwork.sendToServer(message);
    }

    public static void request(PageRequest message) {
        StorageNetwork.sendToServer(message);
    }

    public record Access(int menu, int token, boolean open, @Nullable ItemStack creativeCursor) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(menu).writeVarInt(token).writeBoolean(open).writeBoolean(creativeCursor != null);
            if (creativeCursor != null) buf.writeNbt(creativeCursor.save(new CompoundTag()));
        }

        private static Access decode(FriendlyByteBuf buf) {
            return new Access(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean() ? ItemStack.of(buf.readNbt()) : null);
        }
    }

    public record CreativeTrash(int token, boolean clear) {
        public void send() { StorageNetwork.sendToServer(this); }

        private void encode(FriendlyByteBuf buf) { buf.writeVarInt(token).writeBoolean(clear); }

        private static CreativeTrash decode(FriendlyByteBuf buf) {
            return new CreativeTrash(buf.readVarInt(), buf.readBoolean());
        }
    }

    public record BulkMove(int token, int sequence, int cell) {
        public void send() { StorageNetwork.sendToServer(this); }

        private void encode(FriendlyByteBuf buf) { buf.writeVarInt(token).writeVarInt(sequence).writeVarInt(cell); }

        private static BulkMove decode(FriendlyByteBuf buf) {
            return new BulkMove(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    public record PageRequest(int menu, int token, int sequence, int[] slots, boolean sort) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(menu).writeVarInt(token).writeVarInt(sequence).writeVarIntArray(slots);
            buf.writeBoolean(sort);
        }

        private static PageRequest decode(FriendlyByteBuf buf) {
            return new PageRequest(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray(BackpackViewport.MAX_CELLS), buf.readBoolean());
        }
    }

    public record Update(int slot, ItemStack item) {}

    public record Index(int token, boolean complete, List<Update> updates) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(token).writeBoolean(complete);
            buf.writeVarInt(updates.size());
            for (Update update : updates) buf.writeVarInt(update.slot()).writeItem(update.item());
        }

        private static Index decode(FriendlyByteBuf buf) {
            int token = buf.readVarInt();
            boolean complete = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > INDEX_BATCH_SIZE) throw new IllegalArgumentException("Invalid backpack index batch");
            List<Update> updates = new ArrayList<>(count);
            for (int i = 0; i < count; i++) updates.add(new Update(buf.readVarInt(), buf.readItem()));
            return new Index(token, complete, updates);
        }
    }

    public record Page(int token, int sequence, boolean closed, int[] slots) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(token).writeVarInt(sequence).writeBoolean(closed);
            buf.writeVarIntArray(slots);
        }

        private static Page decode(FriendlyByteBuf buf) {
            return new Page(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarIntArray(BackpackViewport.MAX_CELLS));
        }
    }

    public record Ready(int token, int sequence) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(token).writeVarInt(sequence);
        }

        private static Ready decode(FriendlyByteBuf buf) {
            return new Ready(buf.readVarInt(), buf.readVarInt());
        }
    }

    private ViewportNetwork() {}
}
