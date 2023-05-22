package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.Utils;
import pt.up.fe.comp2023.analysis.JmmSymbolTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OllirVisitor extends AJmmVisitor<Integer, String> {

    private final StringBuilder code = new StringBuilder();

    private final List<Report> reports = new ArrayList<>();

    private final JmmSymbolTable table;

    private boolean visitedConstructor = false;

    public OllirVisitor(JmmSymbolTable table) {
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

    private void emitInvokeSpecialInit(int indentation, String symbol) {
        emitLine(indentation, "invokespecial(" + symbol + ", \"<init>\").V;");
    }

    private String commonType(String type1, String type2) {
        if (type1.equals("*"))
            if (type2.equals("*"))
                return "int";
            else
                return type2;
        else return type1;
    }

    public String getOllirCode() {
        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::visitChildren);

        addVisit("ImportStatement", this::visitImportStatement);

        addVisit("ClassDeclaration", this::visitClassDeclaration);

        addVisit("MethodDeclaration", this::visitMethodDeclaration);
        addVisit("FieldDeclaration", this::visitFieldDeclaration);
        addVisit("ConstructorDeclaration", this::visitConstructorDeclaration);

        addVisit("ArgumentList", this::visitArgumentList);

        addVisit("VariableDeclaration", this::visitVariableDeclaration);

        // Statement
        addVisit("IfStatement", this::visitIfStatement);
        addVisit("WhileStatement", this::visitWhileStatement);
        addVisit("DoStatement", this::visitDoWhileStatement);
        addVisit("ForStatement", this::visitForStatement);
        addVisit("ForEachStatement", this::visitForEachStatement);
        addVisit("SwitchStatement", this::visitSwitchStatement);
        addVisit("ReturnStatement", this::visitReturnStatement);
        addVisit("BreakStatement", this::visitBreakOrContinueStatement);
        addVisit("ContinueStatement", this::visitBreakOrContinueStatement);
        addVisit("ExpressionStatement", this::visitExpressionStatement);

        addVisit("ForTerm", this::visitForTerminal);

        // Expression
        addVisit("ExplicitPriority", this::visitExplicitPriority);
        addVisit("NewObject", this::visitNewObject);
        addVisit("NewArray", this::visitNewArray);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("PropertyAccess", this::visitPropertyAccess);
        addVisit("ArrayAccess", this::visitArrayAccess);
        addVisit("UnaryPostOp", this::visitUnaryPostOp);
        addVisit("UnaryPreOp", this::visitUnaryPreOp);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("TernaryOp", this::visitTernaryOp);
        addVisit("AssignmentExpression", this::visitAssignment);
        addVisit("LiteralExpression", this::visitLiteral);
        addVisit("IdentifierExpression", this::visitIdentifier);
        addVisit("ThisExpression", this::visitThis);
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

    protected String doNothing(JmmNode node, Integer indentation) {
        return null;
    }

    protected String visitImportStatement(JmmNode node, Integer indentation) {
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

    protected String visitClassDeclaration(JmmNode node, Integer indentation) {
        emitIndentation(indentation);
        emit(table.getClassName());

        if (table.getSuper() != null)
            emit(" extends ", table.getSuper());

        emit(" {\n");

        for (var field : table.getFields())
            emitLine(indentation + 4, ".field ", OllirUtils.toOllirSymbol(field), ";");

        visitChildren(node, indentation + 4);

        if (!visitedConstructor) {
            emitLine(indentation + 4, ".construct ", table.getClassName(), "().V {");
            emitInvokeSpecialInit(indentation + 8, "this");
            emitLine(indentation + 4, "}");
        }

        emitLine(indentation, "}");

        return null;
    }

    protected String visitMethodDeclaration(JmmNode node, Integer indentation) {
        var method = table.getMethod(node.get("methodName"));
        var returnType = OllirUtils.toOllirType(method.getReturnType());
        emitLine(indentation,
                ".method ",
                String.join(" ", method.getModifiers()), " ",
                method.getName(),
                "(", method.getParameters().stream().map(OllirUtils::toOllirSymbol).collect(Collectors.joining(", ")), ").",
                returnType, " {");

        visitChildren(node, indentation + 4);

        if (!node.getJmmChild(node.getNumChildren() - 1).getKind().equals("ReturnStatement"))
            emitLine(indentation + 4, "ret.", returnType, ";");

        emitLine(indentation, "}");

        return null;
    }

    protected String visitFieldDeclaration(JmmNode node, Integer indentation) {
        if (indentation != 4)
            visit(node.getJmmChild(0), indentation);

        return null;
    }

    protected String visitConstructorDeclaration(JmmNode node, Integer indentation) {
        visitedConstructor = true;

        var method = table.getMethod("<constructor>");
        var className = node.get("className");
        emitLine(indentation,
                ".construct ",
                String.join(" ", method.getModifiers()), " ",
                className,
                "(", method.getParameters().stream().map(OllirUtils::toOllirSymbol).collect(Collectors.joining(", ")), ").",
                OllirUtils.toOllirType(method.getReturnType()), " {");
        emitInvokeSpecialInit(indentation + 4, "this");

        visitChildren(node, indentation + 4);

        emitLine(indentation, "}");

        return null;
    }

    protected String visitArgumentList(JmmNode node, Integer indentation) {
        return node.getChildren().stream()
                .map(child -> {
                    var type = child.get("type");
                    if (type.equals("*"))
                        child.put("type", "int");
                    return ", " + visit(child, indentation);
                })
                .collect(Collectors.joining());
    }

    protected String visitVariableDeclaration(JmmNode node, Integer indentation) {
        if (node.getNumChildren() < 2)
            return null;

        var type = OllirUtils.toOllirType(node.get("type"));
        var name = node.get("id") + "." + type;
        var rhsNode = node.getJmmChild(1);
        rhsNode.put("topLevel", "true");
        var rhs = visit(rhsNode, indentation);

        if (rhs.endsWith("*"))
            rhs = rhs.substring(0, rhs.length() - 1) + type;

        emitLine(indentation, name, " :=.", type, " ", rhs, ";");

        if (rhs.startsWith("new(") && !rhs.startsWith("new(array,"))
            emitInvokeSpecialInit(indentation, name);

        return null;
    }

    protected String visitIfStatement(JmmNode node, Integer indentation) {
        var conditionNode = node.getJmmChild(0);
        conditionNode.put("type", "boolean");
        var condition = visit(conditionNode, indentation);
        var ifLabels = OllirUtils.getNextIfLabels();

        emitLine(indentation, "if(!.bool ", condition, ") goto ", ifLabels[0], ";");

        visit(node.getChildren().get(1), indentation + 4);

        emitLine(indentation + 4, "goto ", ifLabels[1], ";");
        emitLine(indentation, ifLabels[0], ":");

        visit(node.getChildren().get(2), indentation + 4);

        emitLine(indentation, ifLabels[1], ":");

        return null;
    }

    protected String visitWhileStatement(JmmNode node, Integer indentation) {
        var whileLabels = OllirUtils.getNextWhileLabels();

        node.put("continueLabel", whileLabels[0]);
        node.put("breakLabel", whileLabels[1]);

        emitLine(indentation, whileLabels[0], ":");

        var conditionNode = node.getJmmChild(0);
        conditionNode.put("type", "boolean");
        var condition = visit(conditionNode, indentation + 4);
        emitLine(indentation + 4, "if(!.bool ", condition, ") goto ", whileLabels[1], ";");

        visit(node.getChildren().get(1), indentation + 4);

        emitLine(indentation + 4, "goto ", whileLabels[0], ";");
        emitLine(indentation, whileLabels[1], ":");

        return null;
    }

    protected String visitDoWhileStatement(JmmNode node, Integer indentation) {
        var doWhileLabels = OllirUtils.getNextDoWhileLabels();

        node.put("continueLabel", doWhileLabels[0]);
        node.put("breakLabel", doWhileLabels[1]);

        emitLine(indentation, doWhileLabels[0], ":");

        visit(node.getChildren().get(0), indentation + 4);

        var conditionNode = node.getChildren().get(1);
        conditionNode.put("type", "boolean");
        conditionNode.put("topLevel", "true");
        var condition = visit(conditionNode, indentation);
        emitLine(indentation + 4, "if(", condition, ") goto ", doWhileLabels[0], ";");
        emitLine(indentation, doWhileLabels[1], ":");

        return null;
    }

    protected String visitForStatement(JmmNode node, Integer indentation) {
        var forLabels = OllirUtils.getNextForLabels();

        node.put("continueLabel", forLabels[0]);
        node.put("breakLabel", forLabels[1]);

        visit(node.getJmmChild(0), indentation);
        emitLine(indentation, forLabels[0], ":");

        var terminal = visit(node.getJmmChild(1), indentation + 4);
        emitLine(indentation + 4, "if(!.bool ", terminal, ") goto ", forLabels[1], ";");

        visit(node.getJmmChild(3), indentation + 4);

        visit(node.getJmmChild(2), indentation + 4);
        emitLine(indentation + 4, "goto ", forLabels[0], ";");
        emitLine(indentation, forLabels[1], ":");

        return null;
    }

    protected String visitForTerminal(JmmNode node, Integer indentation) {
        if (node.getNumChildren() == 0)
            return "1.bool";

        var child = node.getJmmChild(0);
        child.put("topLevel", node.get("topLevel"));
        child.put("type", node.get("type"));

        return visit(child, indentation);
    }

    protected String visitForEachStatement(JmmNode node, Integer indentation) {
        var forEachLabels = OllirUtils.getNextForEachLabels();

        node.put("continueLabel", forEachLabels[0]);
        node.put("breakLabel", forEachLabels[1]);

        var type = node.getJmmChild(0).get("type");
        var id = node.get("id");
        var ollirType = OllirUtils.toOllirType(type);
        var arrayType = OllirUtils.toOllirType(type + "[]");

        var arrayNode = node.getJmmChild(1);
        arrayNode.put("topLevel", "true");
        arrayNode.put("type", type + "[]");
        var array = visit(arrayNode, indentation);

        emitLine(indentation, forEachLabels[2], ".", arrayType, " :=.", arrayType, " ", array, ";");
        emitLine(indentation, forEachLabels[3], ".i32 :=.i32 arraylength(", forEachLabels[2], ".", arrayType, ").i32;");
        emitLine(indentation, forEachLabels[4], ".i32 :=.i32 0.i32;");

        emitLine(indentation, forEachLabels[0], ":");
        emitLine(indentation + 4, "if(", forEachLabels[4], ".i32 >=.bool ", forEachLabels[3], ".i32) goto ", forEachLabels[1], ";");
        emitLine(indentation + 4, id, ".", ollirType, " :=.", ollirType, " ", forEachLabels[2], "[", forEachLabels[4], ".i32].", ollirType, ";");

        visit(node.getJmmChild(2), indentation + 4);

        emitLine(indentation + 4, forEachLabels[4], ".i32 :=.i32 ", forEachLabels[4], ".i32 +.i32 1.i32;");
        emitLine(indentation + 4, "goto ", forEachLabels[0], ";");
        emitLine(indentation, forEachLabels[1], ":");

        return null;
    }


    protected String visitSwitchStatement(JmmNode node, Integer indentation) {
        var switchLabels = OllirUtils.getNextSwitchLabels();

        node.put("breakLabel", switchLabels[1]);

        var expressionNode = node.getJmmChild(0);
        var expression = visit(expressionNode, indentation);

        var defaultCase = switchLabels[1];
        var cases = node.getChildren().subList(1, node.getNumChildren());

        for (int i = 0; i < cases.size(); i++) {
            var _case = cases.get(i);
            if (_case.getKind().equals("DefaultStatement")) {
                defaultCase = switchLabels[0] + i;
                continue;
            }

            var value = visitLiteral(_case, indentation);
            emitLine(indentation, "if(", expression, ".i32 ==.bool ", value, ".i32) goto ", switchLabels[0] + i, ";");
        }

        emitLine(indentation, "goto ", defaultCase, ";");

        for (int i = 0; i < cases.size(); i++) {
            var _case = cases.get(i);

            emitLine(indentation, switchLabels[0] + i, ":");
            visit(_case, indentation + 4);
        }

        emitLine(indentation, switchLabels[1], ":");

        return null;
    }

    protected String visitReturnStatement(JmmNode node, Integer indentation) {
        if (node.getNumChildren() == 0) {
            emitLine(indentation, "ret.V;");
            return null;
        }

        var child = node.getJmmChild(0);
        var s = visit(child, indentation);
        var type = OllirUtils.toOllirType(child.get("type"));

        if (s != null)
            emitLine(indentation, "ret.", type, " ", s, ";");

        return null;
    }

    protected String visitBreakOrContinueStatement(JmmNode node, Integer indentation) {
        var labelName = (node.getKind().equals("BreakStatement") ? "break" : "continue") + "Label";
        var label = Utils.getFromAncestor(node, labelName);
        emitLine(indentation, "goto ", label, ";");
        return null;
    }

    protected String visitExpressionStatement(JmmNode node, Integer indentation) {
        var child = node.getJmmChild(0);
        if (child.get("type").equals("*"))
            child.put("type", child.getKind().equals("MethodCall") ? "void" : "int");
        child.put("topLevel", "true");
        var s = visit(child, indentation);

        if (s != null)
            emitLine(indentation, s, ";");

        return null;
    }

    protected String visitExplicitPriority(JmmNode node, Integer indentation) {
        var child = node.getChildren().get(0);
        child.put("type", node.get("type"));

        if (node.getOptional("topLevel").isPresent())
            child.put("topLevel", "true");

        return visit(child, indentation);
    }

    protected String visitNewObject(JmmNode node, Integer indentation) {
        var type = node.get("type");
        var args = "";

        if (node.getNumChildren() > 0) {
            if (type.equals(table.getClassName())) {
                var params = table.getParameters("<constructor>");
                var argsNode = node.getJmmChild(0);
                for (int i = 0; i < argsNode.getNumChildren(); i++) {
                    var arg = argsNode.getJmmChild(i);
                    var param = params.get(i);
                    arg.put("type", commonType(param.getType().print(), arg.get("type")));
                }
            }

            args = visit(node.getJmmChild(0), indentation);
        }

        var line = "new(" + type + args + ")." + type;

        if (node.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=.", type, " ", line, ";");
        emitInvokeSpecialInit(indentation, temp);

        return temp;
    }

    protected String visitNewArray(JmmNode node, Integer indentation) {
        var type = OllirUtils.toOllirType(node.get("type"));
        var size = visit(node.getJmmChild(1), indentation);

        var line = "new(array, " + size + ")." + type;

        if (node.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=." + type + " ", line, ";");

        return temp;
    }

    protected String visitMethodCall(JmmNode node, Integer indentation) {
        var method = node.get("member");
        var lhs = "this." + table.getClassName();
        var args = "";
        var returnType = OllirUtils.toOllirType(node.get("type"));
        var fn = "invokevirtual";

        if (node.getNumChildren() > 0) {
            var argsNode = node.getJmmChild(0);

            if (!argsNode.getKind().equals("ArgumentList")) {
                if (argsNode.get("type").equals("*"))
                    argsNode.put("type", "Object");
                lhs = visit(argsNode, indentation);

                argsNode = node.getNumChildren() > 1 ? node.getJmmChild(1) : null;
            }

            if (argsNode != null) {
                if (lhs.endsWith("." + table.getClassName())) {
                    var params = table.getParameters(method);
                    for (int i = 0; i < argsNode.getNumChildren(); i++) {
                        var arg = argsNode.getJmmChild(i);
                        var param = params.get(i);
                        arg.put("type", commonType(param.getType().print(), arg.get("type")));
                    }
                }

                args = visit(argsNode, indentation);
            }
        }

        if (!lhs.contains("."))
            fn = "invokestatic";

        var line = fn + "(" + lhs + ", \"" + method + "\"" + args + ")." + returnType;

        if (node.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + returnType;
        emitLine(indentation, temp, " :=.", returnType, " ", line, ";");
        return temp;
    }

    protected String visitPropertyAccess(JmmNode node, Integer indentation) {
        var lhsNode = node.getJmmChild(0);
        if (lhsNode.get("type").equals("*"))
            lhsNode.put("type", "Object");
        var lhs = visit(lhsNode, indentation);
        var type = OllirUtils.toOllirType(node.get("type"));
        var member = node.get("member");

        var fn = lhs.contains(".") ? "getfield" : "getstatic";
        var line = fn + "(" + lhs + ", " + member + "." + type + ")." + type;

        if (lhsNode.get("type").endsWith("[]"))
            line = "arraylength(" + lhs + ")." + type;

        if (node.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=.", type, " ", line, ";");

        return temp;
    }

    protected String visitArrayAccess(JmmNode node, Integer indentation) {
        var lhsNode = node.getJmmChild(0);
        var indexNode = node.getJmmChild(1);
        lhsNode.put("type", node.get("type") + "[]");
        indexNode.put("type", "int");

        var lhs = visit(lhsNode, indentation);
        var index = visit(indexNode, indentation);
        var type = OllirUtils.toOllirType(node.get("type"));

        var line = lhs + "[" + index + "]." + type;

        if (node.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=.", type, " ", line, ";");

        return temp;
    }

    // TODO
    private String visitUnaryPostOp(JmmNode jmmNode, Integer indentation) {
        var lhsNode = jmmNode.getJmmChild(0);
        lhsNode.put("type", jmmNode.get("type"));
        lhsNode.put("topLevel", "true");
        var lhs = visit(lhsNode, indentation);

        var type = OllirUtils.toOllirType(jmmNode.get("type"));
        var operator = jmmNode.get("op").charAt(0) + "." + type;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=.", type, " ", lhs, ";");

        if (lhs.startsWith("getfield(") || lhs.startsWith("getstatic(")) {
            var temp2 = OllirUtils.getNextTemp() + "." + type;
            emitLine(indentation, temp2, " :=.", type, " ", temp, " ", operator, " 1.", type, ";");
            emitLine(indentation, "put", lhs.substring(3, lhs.lastIndexOf(")")), ", ", temp2, ").V;");
        } else {
            emitLine(indentation, lhs, " :=.", type, " ", temp, " ", operator, " 1.", type, ";");
        }

        return temp;
    }

    private String visitUnaryPreOp(JmmNode jmmNode, Integer indentation) {
        var rhsNode = jmmNode.getJmmChild(0);
        rhsNode.put("type", jmmNode.get("type"));

        var type = OllirUtils.toOllirType(jmmNode.get("type"));
        var operator = jmmNode.get("op") + "." + type;
        if (operator.matches("[-+]\\..*"))
            operator = "0.i32 " + operator;

        // ++ and -- are special boys
        else if (operator.charAt(0) == operator.charAt(1)) {
            rhsNode.put("topLevel", "true");
            var rhs = visit(rhsNode, indentation);

            operator = operator.substring(1);

            if (rhs.startsWith("getfield(") || rhs.startsWith("getstatic(")) {
                var temp = OllirUtils.getNextTemp() + "." + type;
                emitLine(indentation, temp, " :=.", type, " ", rhs, ";");
                emitLine(indentation, temp, " :=.", type, " ", temp, " ", operator, " 1.", type, ";");
                emitLine(indentation, "put", rhs.substring(3, rhs.lastIndexOf(")")), ", ", temp, ").V;");
                return temp;
            } else {
                emitLine(indentation, rhs, " :=.", type, " ", rhs, " ", operator, " 1.", type, ";");
                return rhs;
            }
        }

        var rhs = visit(rhsNode, indentation);
        var line = operator + " " + rhs;

        if (jmmNode.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=.", type, " ", line, ";");

        return temp;
    }

    private String visitBinaryOp(JmmNode jmmNode, Integer indentation) {
        var lhsNode = jmmNode.getJmmChild(0);
        var rhsNode = jmmNode.getJmmChild(1);

        var type = commonType(lhsNode.get("type"), rhsNode.get("type"));
        lhsNode.put("type", type);
        rhsNode.put("type", type);

        var lhs = visit(lhsNode, indentation);
        var rhs = visit(rhsNode, indentation);

        type = OllirUtils.toOllirType(jmmNode.get("type"));
        var operator = jmmNode.get("op") + "." + type;

        var line = lhs + " " + operator + " " + rhs;

        if (jmmNode.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=.", type, " ", line, ";");

        return temp;
    }

    protected String visitTernaryOp(JmmNode node, Integer indentation) {
        var condition = visit(node.getJmmChild(0), indentation);
        var ifLabels = OllirUtils.getNextIfLabels();
        var type = OllirUtils.toOllirType(node.get("type"));
        var temp = OllirUtils.getNextTemp() + "." + type;

        emitLine(indentation, "if (!.bool ", condition, ") goto ", ifLabels[0], ";");

        var lhsNode = node.getJmmChild(1);
        lhsNode.put("topLevel", "true");
        var lhs = visit(lhsNode, indentation + 4);
        emitLine(indentation + 4, temp, " :=.", type, " ", lhs, ";");

        emitLine(indentation + 4, "goto ", ifLabels[1], ";");
        emitLine(indentation, ifLabels[0], ":");

        var rhsNode = node.getJmmChild(2);
        rhsNode.put("topLevel", "true");
        var rhs = visit(rhsNode, indentation + 4);
        emitLine(indentation + 4, temp, " :=.", type, " ", rhs, ";");

        emitLine(indentation, ifLabels[1], ":");

        return temp;
    }

    protected String visitAssignment(JmmNode node, Integer indentation) {
        var lhsNode = node.getJmmChild(0);
        var rhsNode = node.getJmmChild(1);
        var operator = node.get("op").substring(0, node.get("op").length() - 1);
        var type = OllirUtils.toOllirType(commonType(lhsNode.get("type"), rhsNode.get("type")));

        lhsNode.put("type", type);
        rhsNode.put("type", type);

        lhsNode.put("topLevel", "true");
        var lhs = visit(lhsNode, indentation);

        if (lhs.startsWith("getfield(") || lhs.startsWith("getstatic(")) {
            if (!operator.isEmpty()) {
                var temp1 = OllirUtils.getNextTemp() + "." + type;
                emitLine(indentation, temp1, " :=.", type, " ", lhs, ";");

                var temp2 = OllirUtils.getNextTemp() + "." + type;
                var rhs = visit(rhsNode, indentation);
                emitLine(indentation, temp2, " :=.", type, " ", temp1, " ", operator, ".", type, " ", rhs, ";");

                lhs = "put" + lhs.substring(3, lhs.lastIndexOf(")")) + ", " + temp2 + ").V";
            } else {
                var rhs = visit(rhsNode, indentation);
                lhs = "put" + lhs.substring(3, lhs.lastIndexOf(")")) + ", " + rhs + ").V";
            }

            return lhs;
        }

        if (operator.isEmpty())
            rhsNode.put("topLevel", "true");
        var rhs = visit(rhsNode, indentation);

        if (operator.isEmpty())
            emitLine(indentation, lhs, " :=.", type, " ", rhs, ";");
        else
            emitLine(indentation, lhs, " :=.", type, " ", lhs, " ", operator, ".", type, " ", rhs, ";");

        if (rhs.startsWith("new(") && !rhs.startsWith("new(array,"))
            emitInvokeSpecialInit(indentation, lhs);

        return node.getOptional("topLevel").isPresent() ? null : lhs;
    }

    protected String visitLiteral(JmmNode node, Integer indentation) {
        var type = OllirUtils.toOllirType(node.get("type"));
        var value = node.get("value");

        if (value.equals("null"))
            return "null." + type;

        return switch (type) {
            case "String" -> {
                var line = "ldc(" + value + ").String";

                if (node.getOptional("topLevel").isPresent())
                    yield line;

                var temp = OllirUtils.getNextTemp() + ".String";
                emitLine(indentation, temp, " :=.String " + line + ";");
                yield temp;
            }

            case "bool" -> (value.equals("true") ? "1" : "0") + ".bool";

            case "char" -> ((int) value.charAt(value.length() - 1)) + ".i32";

            default -> value.split("\\.")[0] + "." + type;
        };
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

        var constructor = node.getAncestor("ConstructorDeclaration");

        if (constructor.isPresent()) {
            var constructorTable = table.getMethod("<constructor>");

            for (var variable : constructorTable.getLocalVariables())
                if (variable.getName().equals(node.get("id")))
                    return OllirUtils.toOllirSymbol(variable);

            for (int i = 0; i < constructorTable.getParameters().size(); i++) {
                var parameter = constructorTable.getParameters().get(i);
                if (parameter.getName().equals(node.get("id")))
                    return "$" + (i + 1) + "." + OllirUtils.toOllirSymbol(parameter);
            }
        }

        for (var variable : table.getFields())
            if (variable.getName().equals(node.get("id"))) {
                var type = OllirUtils.toOllirType(variable.getType());
                var symbol = OllirUtils.toOllirSymbol(variable);

                var line = "getfield(this." + table.getClassName() + ", " + symbol + ")." + type;

                if (node.getOptional("topLevel").isPresent())
                    return line;

                var temp = OllirUtils.getNextTemp() + "." + type;
                emitLine(indentation, temp, " :=.", type, " ", line, ";");
                return temp;
            }

        return node.get("id");
    }

    protected String visitThis(JmmNode node, Integer indentation) {
        return "this." + table.getClassName();
    }
}
