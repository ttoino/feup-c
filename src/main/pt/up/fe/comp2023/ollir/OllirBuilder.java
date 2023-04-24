package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.Optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OllirBuilder extends AJmmVisitor<Integer, String> {

    private final StringBuilder code = new StringBuilder();

    private final List<Report> reports = new ArrayList<>();

    private final SymbolTable table;

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

        // Statement
        addVisit("ExpressionStatement", this::visitExpressionStatement);

        // Expression
        addVisit("ExplicitPriority", this::visitChildren);
        addVisit("NewObject", this::visitNewObject);
        addVisit("NewArray", this::visitChildren);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("PropertyAccess", this::visitChildren);
        addVisit("ArrayAccess", this::visitChildren);
        addVisit("UnaryPostOp", this::visitChildren);
        addVisit("UnaryPreOp", this::visitChildren);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("TernaryOp", this::visitChildren);
        addVisit("AssigmentExpression", this::visitChildren);
        addVisit("LiteralExpression", this::visitLiteral);
        addVisit("IdentifierExpression", this::visitChildren);
        addVisit("ThisExpression", this::visitChildren);
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
                "(", method.getParameters().stream().map(Optimization.this::toOllirSymbol).collect(Collectors.joining(", ")), ").",
                toOllirType(method.getReturnType()), " {");

        visitChildren(node, indentation + 4);

        emitLine(indentation, "}");

        return null;
    }

    protected String visitImportDeclaration(JmmNode node, Integer indentation) {
        emit("import ");

        var classPackageOptional = node.getOptionalObject("classPackage");

        if (classPackageOptional.isPresent()) {
            if (classPackageOptional.get() instanceof List<String>) {
                var classPackage = (List<String>) classPackageOptional.get();

                for (var _package : classPackage) {
                    emit(_package);
                    emit(".");
                }
            }
        }

        var className = node.get("className");
        emit(className);
        emit";\n");
    }

    private ExprToOllir visitBinaryOp(JmmNode jmmNode, Integer indentation) {

        StringBuilder code = new StringBuilder();

        String ollirCodeLhs = visit(jmmNode.getJmmChild(0), indentation);
        String ollirCodeRhs = visit(jmmNode.getJmmChild(1), indentation);

        String operator = jmmNode.get("op") + ".i32";
        StringBuilder temp = new StringBuilder(nextTemp()).append(".i32");

        code.append(ollirCodeLhs.prefix);
        code.append(ollirCodeRhs.prefix);

        code.append(temp).append(" :=.i32 ").append(ollirCodeLhs.value).append(" ").append(operator).append(" ").append(ollirCodeRhs.value).append(";\n");


        return new ExprToOllir(code.toString(), temp.toString());
    }

    protected String visitMethodCall(JmmNode node, Integer indentation) {

    }

    protected String visitLiteral(JmmNode node, Integer indentation) {
        return node.get("value") + "." + OllirUtils.toOllirType(node.get("type"));
    }

    protected String visitExpressionStatement(JmmNode node, Integer indentation) {
        emitLine(visit(node.getJmmChild(0)));

        return null;
    }

    public String getOllirCode() {
        return code.toString();
    }
}
