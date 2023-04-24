package pt.up.fe.comp2023;

public final class Utils {
    public static <T> boolean in(T[] arr, T i) {
        for (var x : arr)
            if (x.equals(i)) return true;
        return false;
    }
}
