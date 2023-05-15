package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.*;

public class ConstantPropagationVisitor extends AJmmVisitor<Void, Boolean> {
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
            || node.getAncestor("ForEachStatement").isPresent()
            || node.getAncestor("SwitchStatement").isPresent();
    }

    @Override
    protected void buildVisitor() {
        addVisit("AssignmentExpression", this::visitAssignment);
        addVisit("VariableDeclaration", this::visitVariableDeclaration);
        addVisit("IdentifierExpression", this::visitIdentifier);
        addVisit("UnaryPostOp", this::visitUnaryOp);
        addVisit("UnaryPreOp", this::visitUnaryOp);

        addVisit("IfStatement", this::visitConditional);
        addVisit("SwitchStatement", this::visitConditional);
        addVisit("WhileStatement", this::visitLoop);
        addVisit("DoStatement", this::visitLoop);
        addVisit("ForStatement", this::visitLoop);
        addVisit("ForEachStatement", this::visitLoop);

        addVisit("MethodDeclaration", this::visitMethod);
        addVisit("ConstructorDeclaration", this::visitMethod);

        setDefaultVisit(this::visitChildren);
    }

    private Boolean visitChildren(JmmNode node, Void context) {
        boolean r = false;

        for (var child : node.getChildren())
            r |= visit(child, context);

        return r;
    }

    private Boolean visitMethod(JmmNode node, Void context) {
        var r = visitChildren(node, context);

        for (var var : variables.values())
            if (var.usages == 0 && var.node != null)
                var.node.delete();

        variables.clear();

        return r;
    }

    private Boolean visitAssignment(JmmNode node, Void context) {
        var left = node.getJmmChild(0);
        var right = node.getJmmChild(1);

        boolean r = visit(right, context);

        var op = node.get("op");

        if (!left.getKind().equals("IdentifierExpression"))
            return r;

        var id = left.get("id");
        var origin = left.get("origin");
        var var = variables.get(id);

        if (var != null) {
            if (var.usages == 0 && var.node != null && op.equals("="))
                var.node.delete();

            variables.remove(id);
        }

        if (!right.getKind().equals("LiteralExpression"))
            return r;

        var value = right.get("value");

        if (!op.equals("=")) {
            var type = right.get("type");
            var leftValue = var == null ? ConstantUtils.defaultValue(type) : var.value;

            value = ConstantUtils.calculate(leftValue, value, op.replace("=", ""), type);
            node.put("op", "=");
            right.replace(ConstantUtils.literal(value, type));
        }

        if (!origin.equals("local") || isInControlFlow(node))
            return r;

        variables.put(id, new Variable(node.getJmmParent().getKind().equals("ExpressionStatement") ? node.getJmmParent() : null, value));

        return r;
    }

    private Boolean visitVariableDeclaration(JmmNode node, Void context) {
        var id = node.get("id");
        var var = variables.get(id);
        var origin = node.get("origin");
        var type = node.get("type");
        var value = ConstantUtils.defaultValue(type);
        var r = false;

        if (var != null) {
            if (var.usages == 0 && var.node != null)
                var.node.delete();

            variables.remove(id);
        }

        if (node.getNumChildren() == 2) {
            var right = node.getJmmChild(1);

            r = visit(right, context);

            if (!right.getKind().equals("LiteralExpression"))
                return false;

            value = right.get("value");
        }

        if (!origin.equals("local") || isInControlFlow(node))
            return false;

        variables.put(id, new Variable(node.getJmmParent().getJmmParent(), value));

        return r;
    }

    private Boolean visitUnaryOp(JmmNode node, Void context) {
        var child = node.getJmmChild(0);
        var op = node.get("op");

        if (!op.equals("++") && !op.equals("--"))
            return visit(child);

        var id = child.get("id");
        variables.remove(id);

        return false;
    }

    protected Boolean visitIdentifier(JmmNode node, Void context) {
        var id = node.get("id");
        var var = variables.get(id);

        if (var == null)
            return false;

        node.replace(ConstantUtils.literal(var.value, node.get("type")));

        return true;
    }

    protected Boolean visitConditional(JmmNode node, Void context) {
        var condition = node.getJmmChild(0);
        var r = visit(condition, context);

        var tempVars = new TreeMap<>(variables);
        var newVars = new HashSet<TreeMap<String, Variable>>();

        for (int i = 1; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            variables.clear();
            variables.putAll(tempVars);
            r |= visit(child, context);
            newVars.add(new TreeMap<>(variables));
        }

        variables.clear();

        Set<String> keys = null;

        for (var vars : newVars) {
            if (keys == null)
                keys = vars.keySet();
            else
                keys.retainAll(vars.keySet());
        }

        if (keys != null)
            outer: for (var key : keys) {
                Variable var = null;

                for (var vars : newVars)
                    if (var == null)
                        var = vars.get(key);
                    else if (!var.value.equals(vars.get(key).value))
                        continue outer;

                if (var != null)
                    variables.put(key, var);
            }

        return r;
    }

    protected Boolean visitLoop(JmmNode node, Void context) {
        var modifiedVariables = modifiedVariables(node);
        modifiedVariables.forEach(variables::remove);

        return visitChildren(node, context);
    }

    private Set<String> modifiedVariables(JmmNode node) {
        var r = new HashSet<String>();
        Stack<JmmNode> stack = new Stack<>();
        stack.push(node);

        while (!stack.isEmpty()) {
            var n = stack.pop();

            switch (n.getKind()) {
                case "VariableDeclaration" -> r.add(n.get("id"));
                case "AssignmentExpression" -> {
                    var left = n.getJmmChild(0);

                    if (left.getKind().equals("IdentifierExpression"))
                        r.add(left.get("id"));
                }
                case "UnaryPreOp", "UnaryPostOp" -> {
                    var child = n.getJmmChild(0);

                    if (child.getKind().equals("IdentifierExpression")
                        && (n.get("op").equals("++") || n.get("op").equals("--")))
                        r.add(child.get("id"));
                }
            }

            for (var child : n.getChildren())
                stack.push(child);
        }

        return r;
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
