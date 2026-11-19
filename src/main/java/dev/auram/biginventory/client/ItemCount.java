package dev.auram.biginventory.client;

final class ItemCount {
    static String format(long count) {
        long thousands = count / 1_000;
        return thousands == 0 ? Long.toString(count) : thousands + "k";
    }

    private ItemCount() {}
}
