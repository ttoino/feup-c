package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

import java.util.Map;
import java.util.TreeMap;

public class ConstantPropagationVisitor extends PreorderJmmVisitor<Void, Boolean> {
    private final Map<String, Variable> variables = new TreeMap<>();

    public ConstantPropagationVisitor() {
        super();
        buildVisitor();
    }

    private boolean isInControlFlow(JmmNode node) {
        return node.getAncestor("IfStatement").isPresent()
            || node.getAncestor("WhileStatement").isPresent()
            || node.getAncestor("DoStatement").isPresent()
            || node.getAncestor("ForStatement").isPresent()
            || node.getAncestor("ForEachStatement").isPresent();
    }

    @Override
    public Boolean visit(JmmNode jmmNode, Void data) {
        var r = super.visit(jmmNode, data);

        if (jmmNode.getKind().equals("MethodDeclaration")) {
            for (var var : variables.values()) {
                if (var.usages == 0 && var.node != null)
                    var.node.delete();
            }

            variables.clear();
        }

        return r;
    }

    @Override
    protected void buildVisitor() {
        addVisit("Program", this::visitProgram);
        addVisit("AssignmentExpression", this::visitAssignment);
        addVisit("VariableDeclaration", this::visitVariableDeclaration);
        addVisit("IdentifierExpression", this::visitIdentifier);
        addVisit("UnaryPostOp", this::visitUnaryOp);
        addVisit("UnaryPreOp", this::visitUnaryOp);

        setDefaultVisit(this::visitOther);
        setReduceSimple(Boolean::logicalOr);
    }

    private Boolean visitOther(JmmNode node, Void context) {
        return false;
    }

    private Boolean visitProgram(JmmNode node, Void context) {
        variables.clear();
        return false;
    }

    private Boolean visitAssignment(JmmNode node, Void context) {
        var left = node.getJmmChild(0);
        var right = node.getJmmChild(1);
        var op = node.get("op");

        if (!left.getKind().equals("IdentifierExpression"))
            return false;

        left.put("isLeft", "true");

        var id = left.get("id");
        var origin = left.get("origin");
        var var = variables.get(id);

        if (var != null) {
            if (var.usages == 0 && var.node != null && op.equals("="))
                var.node.delete();

            variables.remove(id);
        }

        if (!right.getKind().equals("LiteralExpression"))
            return false;

        var value = right.get("value");

        if (!op.equals("=")) {
            var type = right.get("type");
            var leftValue = var == null ? ConstantUtils.defaultValue(type) : var.value;

            value = ConstantUtils.calculate(leftValue, value, op.replace("=", ""), type);
            node.put("op", "=");
            right.replace(ConstantUtils.literal(value, type));
        }

        if (!origin.equals("local") || isInControlFlow(node))
            return false;

        variables.put(id, new Variable(node.getJmmParent().getKind().equals("ExpressionStatement") ? node.getJmmParent() : null, value));

        return false;
    }

    private Boolean visitVariableDeclaration(JmmNode node, Void context) {
        var id = node.get("id");
        var var = variables.get(id);
        var origin = node.get("origin");
        var type = node.get("type");
        var value = ConstantUtils.defaultValue(type);

        if (var != null) {
            if (var.usages == 0 && var.node != null)
                var.node.delete();

            variables.remove(id);
        }

        if (node.getNumChildren() == 2) {
            var right = node.getJmmChild(1);

            if (!right.getKind().equals("LiteralExpression"))
                return false;

            value = right.get("value");
        }

        if (!origin.equals("local") || isInControlFlow(node))
            return false;

        variables.put(id, new Variable(node.getJmmParent().getJmmParent(), value));

        return false;
    }

    private Boolean visitUnaryOp(JmmNode node, Void context) {
        var op = node.get("op");

        if (!op.equals("++") && !op.equals("--"))
            return false;

        var id = node.getJmmChild(0).get("id");
        variables.remove(id);

        node.getJmmChild(0).put("isLeft", "true");

        return false;
    }

    protected Boolean visitIdentifier(JmmNode node, Void context) {
        var id = node.get("id");
        var var = variables.get(id);

        if (var == null)
            return false;

        if (node.getOptional("isLeft").isPresent())
            return false;

        var.usages++;

        if (isInControlFlow(node))
            return false;

        node.replace(ConstantUtils.literal(var.value, node.get("type")));
        var.usages--;

        return true;
    }

    private static class Variable {
        public final JmmNode node;
        public final String value;
        public int usages;

        public Variable(JmmNode node, String value) {
            this.node = node;
            this.value = value;
            this.usages = 0;
        }
    }
}
