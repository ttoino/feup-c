package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;

public class ASTSimplificationVisitor extends PostorderJmmVisitor<Void, Boolean> {
    public ASTSimplificationVisitor() {
        super();
        buildVisitor();
    }

    @Override
    protected void buildVisitor() {
        addVisit("ExplicitPriority", this::visitExplicitPriority);
        addVisit("IfStatement", this::visitIfStatement);
        addVisit("ReturnStatement", this::visitReturnStatement);
        addVisit("BreakStatement", this::visitReturnStatement);
        addVisit("ContinueStatement", this::visitReturnStatement);

        setDefaultVisit(this::visitOther);
        setReduceSimple(Boolean::logicalOr);
    }

    private Boolean visitOther(JmmNode node, Void context) {
        return false;
    }

    protected Boolean visitExplicitPriority(JmmNode node, Void context) {
        node.replace(node.getJmmChild(0));
        return false;
    }

    protected Boolean visitIfStatement(JmmNode node, Void context) {
        var condition = node.getJmmChild(0);
        var then = node.getJmmChild(1);
        var otherwise = node.getJmmChild(2);

        if (condition.getKind().equals("LiteralExpression")) {
            var value = condition.get("value");

            if (value.equals("true"))
                node.replace(then);
            else
                node.replace(otherwise);

            return true;
        }

        return false;
    }

    protected boolean visitReturnStatement(JmmNode node, Void context) {
        var parent = node.getJmmParent();
        var nodeIndex = node.getIndexOfSelf();

        if (parent.getNumChildren() == nodeIndex + 1)
            return false;

        for (int i = parent.getNumChildren() - 1; i > nodeIndex; --i)
            parent.removeJmmChild(i);

        return true;
    }
}
