package dev.auram.biginventory.client;

public final class ItemCount {
    public static String format(long count) {
        long thousands = count / 1_000;
        return thousands == 0 ? Long.toString(count) : thousands + "k";
    }

    private ItemCount() {}
}
