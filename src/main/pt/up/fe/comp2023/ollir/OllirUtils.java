package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class OllirUtils {
    private static int temporaryVarCounter = 0;
    private static int ifLabelCounter = 0;
    private static int whileLabelCounter = 0;
    private static int doWhileLabelCounter = 0;

    public static String getNextTemp() {
        return "__temp__" + temporaryVarCounter++;
    }

    public static String[] getNextIfLabels() {
        return new String[] { "__else__" + ifLabelCounter, "__endif__" + ifLabelCounter++ };
    }

    public static String[] getNextWhileLabels() {
        return new String[] { "__while__" + whileLabelCounter, "__endwhile__" + whileLabelCounter++ };
    }

    public static String[] getNextDoWhileLabels() {
        return new String[] { "__dowhile__" + doWhileLabelCounter, "__enddowhile__" + doWhileLabelCounter++ };
    }

    public static String toOllirType(Type type) {
        return toOllirType(type.print());
    }

    public static String toOllirType(String type) {
        StringBuilder s = new StringBuilder();

        while (type.contains("[]")) {
            s.append("array.");
            type = type.replace("[]", "");
        }

        return s.append(switch (type) {
            case "void" -> "V";
            case "byte", "short", "int", "long", "float", "double", "Integer" -> "i32";
            case "boolean" -> "bool";
            default -> type;
        }).toString();
    }

    public static String toOllirSymbol(Symbol symbol) {
        return symbol.getName() + "." + toOllirType(symbol.getType());
    }
}
