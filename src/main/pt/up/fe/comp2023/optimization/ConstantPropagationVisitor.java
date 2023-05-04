package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

import java.util.Map;
import java.util.TreeMap;

public class ConstantPropagationVisitor extends PreorderJmmVisitor<Void, Boolean> {
    public ConstantPropagationVisitor() {
        super();
        buildVisitor();
    }

    private final Map<String, String> values = new TreeMap<>();

    @Override
    protected void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethodDeclaration);
        addVisit("AssignmentExpression", this::visitAssignment);
        addVisit("VariableDeclaration", this::visitVariableDeclaration);
        addVisit("IdentifierExpression", this::visitIdentifier);

        setDefaultVisit(this::visitOther);
        setReduceSimple(Boolean::logicalOr);
    }

    private Boolean visitOther(JmmNode node, Void context) {
        return false;
    }

    private Boolean visitMethodDeclaration(JmmNode node, Void context) {
        values.clear();
        return false;
    }

    private Boolean visitAssignment(JmmNode node, Void context) {
        var left = node.getJmmChild(0);
        var right = node.getJmmChild(1);
        var op = node.get("op");

        if (!left.getKind().equals("IdentifierExpression"))
            return false;

        left.put("isLeft", "true");

        if (!right.getKind().equals("LiteralExpression") || !op.equals("="))
            return false;

        var id = left.get("id");
        var origin = left.get("origin");
        var value = right.get("value");

        if (!origin.equals("local"))
            return false;

        values.put(id, value);

        var parent = node.getJmmParent();
        if (parent.getKind().equals("ExpressionStatement"))
            parent.delete();

        return true;
    }

    private Boolean visitVariableDeclaration(JmmNode node, Void context) {
        if (node.getNumChildren() != 2)
            return false;

        var right = node.getJmmChild(1);
        var op = node.get("op");

        if (!right.getKind().equals("LiteralExpression") || !op.equals("="))
            return false;

        var id = node.get("id");
        var origin = node.get("origin");
        var value = right.get("value");

        if (!origin.equals("local"))
            return false;

        values.put(id, value);
        node.getAncestor("AssignmentStatement").ifPresent(JmmNode::delete);

        return true;
    }

    protected Boolean visitIdentifier(JmmNode node, Void context) {
        if (node.getOptional("isLeft").isPresent())
            return false;

        var id = node.get("id");

        if (!values.containsKey(id))
            return false;

        var value = values.get(id);
        var literalNode = new JmmNodeImpl("LiteralExpression");
        literalNode.put("value", value);
        literalNode.put("type", node.get("type"));
        node.replace(literalNode);

        return true;
    }
}