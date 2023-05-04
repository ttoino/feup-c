package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2023.Constants;
import pt.up.fe.comp2023.Utils;

public class ConstantFoldingVisitor extends PostorderJmmVisitor<Void, Boolean> {
    public ConstantFoldingVisitor() {
        super();
        buildVisitor();
    }

    @Override
    protected void buildVisitor() {
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("UnaryPreOp", this::visitUnaryPreOp);
        addVisit("UnaryPostOp", this::visitUnaryPostOp);

        setDefaultVisit(this::visitOther);
        setReduceSimple(Boolean::logicalOr);
    }

    private Boolean visitOther(JmmNode node, Void context) {
        return false;
    }

    protected Boolean visitBinaryOp(JmmNode node, Void context) {
        var left = node.getJmmChild(0);
        var right = node.getJmmChild(1);

        if (!left.getKind().equals("LiteralExpression") || !right.getKind().equals("LiteralExpression"))
            return false;

        var leftValue = left.get("value");
        var leftType = left.get("type");
        var rightValue = right.get("value");
        var op = node.get("op");

        if (Utils.in(Constants.INTEGER_TYPES, leftType)) {
            var leftInt = Integer.parseInt(leftValue);
            var rightInt = Integer.parseInt(rightValue);

            var computed = switch (op) {
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
            };

            left.put("value", String.valueOf(computed));
            node.replace(left);
        } else if (leftType.equals("boolean")) {
            var leftBool = Boolean.parseBoolean(leftValue);
            var rightBool = Boolean.parseBoolean(rightValue);

            var computed = switch (op) {
                case "&&" -> leftBool && rightBool;
                case "||" -> leftBool || rightBool;
                case "&" -> leftBool & rightBool;
                case "|" -> leftBool | rightBool;
                case "^" -> leftBool ^ rightBool;
                case "==" -> leftBool == rightBool;
                case "!=" -> leftBool != rightBool;
                default -> throw new RuntimeException("Unknown operator: " + op);
            };

            left.put("value", String.valueOf(computed));
            node.replace(left);
        } else if (leftType.equals("String")) {
            var computed = switch (op) {
                case "+" -> leftValue.substring(0, leftValue.length() - 1) + rightValue.substring(1);
                case "==" -> leftValue.equals(rightValue);
                case "!=" -> !leftValue.equals(rightValue);
                default -> throw new RuntimeException("Unknown operator: " + op);
            };

            left.put("value", String.valueOf(computed));
            node.replace(left);
        } else {
            throw new RuntimeException("Can't fold type: " + leftType);
        }

        return true;
    }

    protected Boolean visitUnaryPreOp(JmmNode node, Void context) {

        return false;
    }

    protected Boolean visitUnaryPostOp(JmmNode node, Void context) {

        return false;
    }
}
