package pt.up.fe.comp2023.optimization;

import java.util.HashSet;
import java.util.Set;

public class SetUtils {

    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
        Set<T> difference = new HashSet<>(set1);
        difference.removeAll(set2);
        return difference;
    }
}
