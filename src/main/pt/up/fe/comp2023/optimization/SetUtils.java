package pt.up.fe.comp2023.optimization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetUtils {

    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
        Set<T> difference = new HashSet<>(set1);
        difference.removeAll(set2);
        return difference;
    }

    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        Set<T> union = new HashSet<>(set1);
        union.addAll(set2);
        return union;
    }

    public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
        Set<T> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        return intersection;
    }

    public static <T> boolean isSubset(Set<T> set1, Set<T> set2) {
        return set2.containsAll(set1);
    }

    public static <T> boolean areDisjoint(Set<T> set1, Set<T> set2) {
        return intersection(set1, set2).isEmpty();
    }

    public static <T> Set<List<T>> generateCombinations(Set<T> set) {
        Set<List<T>> combinations = new HashSet<>();

        List<T> elements = new ArrayList<>(set);
        int size = elements.size();

        for (int i = 0; i < size - 1; i++) {
            for (int j = i + 1; j < size; j++) {
                List<T> combination = new ArrayList<>();
                combination.add(elements.get(i));
                combination.add(elements.get(j));

                if (combination.get(0) != combination.get(1))
                    combinations.add(combination);
            }
        }

        return combinations;
    }
}
