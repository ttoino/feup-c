package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;

public class ConstantFoldingVisitor extends PostorderJmmVisitor<Void, Boolean> {
    public ConstantFoldingVisitor() {
        super();
        buildVisitor();
    }

    @Override
    protected void buildVisitor() {
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("UnaryPreOp", this::visitUnaryPreOp);
        addVisit("TernaryOp", this::visitTernaryOp);

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

        left.put("type", node.get("type"));
        left.put("value", ConstantUtils.calculate(leftValue, rightValue, op, leftType));
        node.replace(left);

        return true;
    }

    protected Boolean visitUnaryPreOp(JmmNode node, Void context) {
        var child = node.getJmmChild(0);

        if (!child.getKind().equals("LiteralExpression"))
            return false;

        var value = child.get("value");
        var type = child.get("type");
        var op = node.get("op");

        child.put("value", ConstantUtils.calculate(value, op, type));
        node.replace(child);

        return true;
    }

    protected Boolean visitTernaryOp(JmmNode node, Void context) {
        var first = node.getJmmChild(0);
        var second = node.getJmmChild(1);
        var third = node.getJmmChild(2);

        if (!first.getKind().equals("LiteralExpression"))
            return false;

        var firstValue = Boolean.parseBoolean(first.get("value"));
        node.replace(firstValue ? second : third);

        return true;
    }
}
