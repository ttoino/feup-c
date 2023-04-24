package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class OllirUtils {
    private static int temporaryVarCounter = 0;

    public static String getNextTemp() {
        temporaryVarCounter++;
        return t + temporaryVarCounter;
    }

    public static String toOllirType(String type) {
        return (type.isArray() ? "array." : "") + switch (type.getName()) {
            case "void", "static void" -> "V";
            case "byte", "short", "int", "long", "Integer" -> "i32";
            case "boolean" -> "bool";
            default -> type.getName();
        };
    }

    public static String getCode(String value, Type type) {
        return value + "." + getCode(type);
    }

    public static String getCode(Type type) {
        StringBuilder name = new StringBuilder();

        if (type.isArray())
            name.append("array.");

        name.append(toOllirType(type.getName()));

        return name.toString();
    }

    public static Type getType(JmmNode jmmNode) {
        boolean isArray = jmmNode.getAttributes().contains("isArray") && jmmNode.get("isArray").equals("true");
        return new Type(jmmNode.get("value"), isArray);
    }
}
