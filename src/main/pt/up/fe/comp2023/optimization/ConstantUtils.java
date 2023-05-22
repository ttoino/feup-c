package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2023.Constants;
import pt.up.fe.comp2023.Utils;

public final class ConstantUtils {
    public static String calculate(String left, String right, String op, String type) {
        if (Utils.in(Constants.INTEGER_TYPES, type)) {
            var leftInt = Integer.parseInt(left);
            var rightInt = Integer.parseInt(right);

            return String.valueOf(switch (op) {
                case "+" -> leftInt + rightInt;
                case "-" -> leftInt - rightInt;
                case "*" -> leftInt * rightInt;
                case "/" -> leftInt / rightInt;
                case "%" -> leftInt % rightInt;
                case "<<" -> leftInt << rightInt;
                case ">>" -> leftInt >> rightInt;
                case ">>>" -> leftInt >>> rightInt;
                case "&" -> leftInt & rightInt;
                case "|" -> leftInt | rightInt;
                case "^" -> leftInt ^ rightInt;
                case "<" -> leftInt < rightInt;
                case ">" -> leftInt > rightInt;
                case "<=" -> leftInt <= rightInt;
                case ">=" -> leftInt >= rightInt;
                case "==" -> leftInt == rightInt;
                case "!=" -> leftInt != rightInt;
                default -> throw new RuntimeException("Unknown operator: " + op);
            });
        } else if (type.equals("boolean")) {
            var leftBool = Boolean.parseBoolean(left);
            var rightBool = Boolean.parseBoolean(right);

            return String.valueOf(switch (op) {
                case "&&" -> leftBool && rightBool;
                case "||" -> leftBool || rightBool;
                case "&" -> leftBool & rightBool;
                case "|" -> leftBool | rightBool;
                case "^" -> leftBool ^ rightBool;
                case "==" -> leftBool == rightBool;
                case "!=" -> leftBool != rightBool;
                default -> throw new RuntimeException("Unknown operator: " + op);
            });
        } else if (type.equals("String")) {
            return String.valueOf(switch (op) {
                case "+" -> left.substring(0, left.length() - 1) + right.substring(1);
                case "==" -> left.equals(right);
                case "!=" -> !left.equals(right);
                default -> throw new RuntimeException("Unknown operator: " + op);
            });
        } else {
            throw new RuntimeException("Unknown type: " + type);
        }
    }

    public static String calculate(String value, String op, String type) {
        if (Utils.in(Constants.INTEGER_TYPES, type)) {
            var intVal = Integer.parseInt(value);

            return String.valueOf(switch (op) {
                case "~" -> ~intVal;
                case "+" -> intVal;
                case "-" -> -intVal;
                case "++" -> intVal + 1;
                case "--" -> intVal - 1;
                default -> throw new RuntimeException("Unknown operator: " + op);
            });
        } else if (type.equals("boolean")) {
            var boolVal = Boolean.parseBoolean(value);

            return String.valueOf(switch (op) {
                case "!" -> !boolVal;
                default -> throw new RuntimeException("Unknown operator: " + op);
            });
        } else {
            throw new RuntimeException("Unknown type: " + type);
        }
    }

    public static String defaultValue(String type) {
        if (Utils.in(Constants.INTEGER_TYPES, type))
            return "0";
        else if (type.equals("boolean"))
            return "false";
        else if (type.equals("String"))
            return "\"\"";
        else
            return "null";
    }

    public static JmmNode literal(String value, String type) {
        var literalNode = new JmmNodeImpl("LiteralExpression");
        literalNode.put("value", value);
        literalNode.put("type", type);
        return literalNode;
    }
}
