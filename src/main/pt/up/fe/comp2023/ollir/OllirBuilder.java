package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.JmmSymbolTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OllirBuilder extends AJmmVisitor<Integer, String> {

    private final StringBuilder code = new StringBuilder();

    private final List<Report> reports = new ArrayList<>();

    private final JmmSymbolTable table;

    public OllirBuilder(JmmSymbolTable table) {
        this.table = table;
    }

    private void emit(String ...code) {
        Arrays.stream(code).forEach(this.code::append);
    }

    private void emitIndentation(int indentation) {
        emit(" ".repeat(indentation));
    }

    private void emitLine(int indentation, String ...code) {
        emitIndentation(indentation);
        emit(code);
        emit("\n");
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::visitChildren);
        addVisit("ClassDeclaration", this::visitClassDeclaration);
        addVisit("MethodDeclaration", this::visitMethodDeclaration);
        addVisit("ImportStatement", this::visitImportDeclaration);
        addVisit("ConstructorDeclaration", this::visitConstructorDeclaration);
        addVisit("FieldDeclaration", this::visitFieldDeclaration);

        // Statement
        addVisit("StatementBlock", this::visitChildren);
        addVisit("IfStatement", this::doNothing);
        addVisit("WhileStatement", this::doNothing);
        addVisit("DoStatement", this::doNothing);
        addVisit("ForStatement", this::doNothing);
        addVisit("ForEachStatement", this::doNothing);
        addVisit("SwitchStatement", this::doNothing);
        addVisit("ReturnStatement", this::visitReturnStatement);
        addVisit("BreakStatement", this::doNothing);
        addVisit("ContinueStatement", this::doNothing);
        addVisit("ExpressionStatement", this::visitExpressionStatement);
        addVisit("AssignmentStatement", this::doNothing);

        // Expression
        addVisit("ExplicitPriority", this::visitChildren);
//        addVisit("NewObject", this::visitNewObject);
        addVisit("NewArray", this::visitChildren);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("ArgumentList", this::visitArgumentList);
        addVisit("PropertyAccess", this::visitChildren);
        addVisit("ArrayAccess", this::visitChildren);
        addVisit("UnaryPostOp", this::visitChildren);
        addVisit("UnaryPreOp", this::visitChildren);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("TernaryOp", this::visitChildren);
        addVisit("AssignmentExpression", this::visitAssignment);
        addVisit("LiteralExpression", this::visitLiteral);
        addVisit("IdentifierExpression", this::visitIdentifier);
        addVisit("ThisExpression", this::visitThis);
    }

    protected String doNothing(JmmNode node, Integer indentation) {
        return null;
    }

    protected String visitChildren(JmmNode node, Integer indentation) {
        StringBuilder sb = new StringBuilder();

        for (var child: node.getChildren()) {
            var s = visit(child, indentation);
            if (s != null)
                sb.append(s);
        }

        return sb.toString();
    }

    protected String visitClassDeclaration(JmmNode node, Integer indentation) {
        emitIndentation(indentation);
        emit(table.getClassName());

        if (table.getSuper() != null)
            emit(" extends ", table.getSuper());

        emit(" {\n");

        visitChildren(node, indentation + 4);

        emitLine(indentation, "}");

        return null;
    }

    protected String visitMethodDeclaration(JmmNode node, Integer indentation) {
        var method = table.getMethod(node.get("methodName"));
        emitLine(indentation,
                ".method ",
                String.join(" ", method.getModifiers()), " ",
                method.getName(),
                "(", method.getParameters().stream().map(OllirUtils::toOllirSymbol).collect(Collectors.joining(", ")), ").",
                OllirUtils.toOllirType(method.getReturnType()), " {");

        visitChildren(node, indentation + 4);

        emitLine(indentation, "}");

        return null;
    }

    protected String visitConstructorDeclaration(JmmNode node, Integer indentation) {
        var method = table.getMethod("<constructor>");
        var className = node.get("className");
        emitLine(indentation,
                ".construct ",
                String.join(" ", method.getModifiers()), " ",
                className,
                "(", method.getParameters().stream().map(OllirUtils::toOllirSymbol).collect(Collectors.joining(", ")), ").",
                OllirUtils.toOllirType(method.getReturnType()), " {");
        emitLine(indentation + 4, "invokespecial(this, \"<init>\").V;");

        visitChildren(node, indentation + 4);

        emitLine(indentation, "}");

        return null;
    }

    protected String visitFieldDeclaration(JmmNode node, Integer indentation) {
        var name = node.getJmmChild(0).get("id");
        var type = node.getJmmChild(0).get("type");

        if (indentation == 4)
            emitLine(
                indentation,
                ".field private ",
                name, ".",
                OllirUtils.toOllirType(type),
                ";"
            );
        else
            visit(node.getJmmChild(0), indentation);

        return null;
    }

    protected String visitImportDeclaration(JmmNode node, Integer indentation) {
        emit("import ");

        var classPackage = node.getObjectAsList("classPackage", String.class);

        for (var _package : classPackage) {
            emit(_package);
            emit(".");
        }

        var className = node.get("className");
        emit(className);
        emit(";\n");

        return null;
    }

    private String visitBinaryOp(JmmNode jmmNode, Integer indentation) {
        var lhs = visit(jmmNode.getJmmChild(0), indentation);
        var rhs = visit(jmmNode.getJmmChild(1), indentation);

        var type = OllirUtils.toOllirType(jmmNode.get("type"));

        var operator = jmmNode.get("op") + "." + type;
        var temp = OllirUtils.getNextTemp() + "." + type;

        emitLine(indentation, temp, " :=.", type, " ", lhs, " ", operator, " ", rhs, ";");

        return temp;
    }

    protected String visitMethodCall(JmmNode node, Integer indentation) {
        var method = node.get("member");
        var lhs = "this";
        var args = "";
        var returnType = "V";
        var fn = "invokevirtual";

        if (node.getNumChildren() > 0) {
            if (node.getJmmChild(0).getKind().equals("ArgumentList"))
                args = visit(node.getJmmChild(0), indentation);
            else {
                lhs = visit(node.getJmmChild(0), indentation);
                if (node.getNumChildren() > 1)
                    args = visit(node.getJmmChild(1), indentation);
            }
        }

        if (lhs.equals("this") || lhs.endsWith("." + table.getClassName())) {
            var methodTable = table.getMethod(method);
            if (methodTable != null)
                returnType = OllirUtils.toOllirType(methodTable.getReturnType());
        } else if (!lhs.contains(".")) {
            fn = "invokestatic";
        }

        return fn + "(" + lhs + ", \"" + method + "\"" + args + ")." + returnType;
    }

    protected String visitArgumentList(JmmNode node, Integer indentation) {
        return node.getChildren().stream()
            .map(child -> ", " + visit(child, indentation))
            .collect(Collectors.joining());
    }

    protected String visitLiteral(JmmNode node, Integer indentation) {
        return node.get("value") + "." + OllirUtils.toOllirType(node.get("type"));
    }

    protected String visitIdentifier(JmmNode node, Integer indentation) {
        var method = node.getAncestor("MethodDeclaration");

        if (method.isPresent()) {
            var methodTable = table.getMethod(method.get().get("methodName"));

            for (var variable : methodTable.getLocalVariables())
                if (variable.getName().equals(node.get("id")))
                    return OllirUtils.toOllirSymbol(variable);

            for (int i = 0; i < methodTable.getParameters().size(); i++) {
                var parameter = methodTable.getParameters().get(i);
                if (parameter.getName().equals(node.get("id")))
                    return "$" + (i + 1) + "." + OllirUtils.toOllirSymbol(parameter);
            }
        }

        for (var variable : table.getFields())
            if (variable.getName().equals(node.get("id"))) {
                var temp = OllirUtils.getNextTemp();
                var type = OllirUtils.toOllirType(variable.getType());
                var symbol = OllirUtils.toOllirSymbol(variable);

                emitLine(indentation, temp, ".", type, " :=.", type, "getfield(this, ", symbol, ")", type, ";");

                return temp;
            }

        return node.get("id");
    }

    protected String visitThis(JmmNode node, Integer indentation) {
        return "this";
    }

    protected String visitAssignment(JmmNode node, Integer indentation) {
        var lhs = visit(node.getJmmChild(0), indentation);
        var rhs = visit(node.getJmmChild(1), indentation);

        var type = OllirUtils.toOllirType(node.get("type"));

        emitLine(indentation, lhs, " :=.", type, " ", rhs, ";");

        return node.getOptional("topLevel").isPresent() ? null : lhs;
    }

    protected String visitExpressionStatement(JmmNode node, Integer indentation) {
        var child = node.getJmmChild(0);
        child.put("topLevel", "true");
        var s = visit(child, indentation);

        if (s != null)
            emitLine(indentation, s, ";");

        return null;
    }

    protected String visitReturnStatement(JmmNode node, Integer indentation) {
        var child = node.getJmmChild(0);
        var s = visit(child, indentation);
        var type = OllirUtils.toOllirType(child.get("type"));

        if (s != null)
            emitLine(indentation, "ret.", type, " ", s, ";");

        return null;
    }

    public String getOllirCode() {
        return code.toString();
    }
}
