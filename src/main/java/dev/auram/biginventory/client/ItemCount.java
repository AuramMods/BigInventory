package dev.auram.biginventory.client;

public final class ItemCount {
    public static String format(long count) {
        long thousands = count / 1_000;
        return thousands == 0 ? Long.toString(count) : thousands + "k";
    }

    public static String compactLabel(String label) {
        int start = 0;
        while (start + 1 < label.length() && label.charAt(start) == '\u00a7') start += 2;
        if (label.length() - start < 4) return label;
        for (int i = start; i < label.length(); i++) {
            if (label.charAt(i) < '0' || label.charAt(i) > '9') return label;
        }
        try {
            return label.substring(0, start) + format(Long.parseLong(label.substring(start)));
        } catch (NumberFormatException ignored) {
            return label;
        }
    }

    private ItemCount() {}
}
