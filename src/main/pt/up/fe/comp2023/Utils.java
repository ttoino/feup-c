package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.ast.JmmNode;

public final class Utils {
    public static <T> boolean in(T[] arr, T i) {
        for (var x : arr)
            if (x.equals(i)) return true;
        return false;
    }

    public static String getFromAncestor(JmmNode node, String attribute) {
        while (node != null) {
            if (node.hasAttribute(attribute))
                return node.get(attribute);

            node = node.getJmmParent();
        }

        return null;
    }
}
