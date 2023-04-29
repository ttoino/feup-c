package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.Utils;
import pt.up.fe.comp2023.analysis.JmmSymbolTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OllirBuilder extends AJmmVisitor<Integer, String> {

    private final StringBuilder code = new StringBuilder();

    private final List<Report> reports = new ArrayList<>();

    private final JmmSymbolTable table;

    private boolean visitedConstructor = false;

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
        addVisit("ForStatement", this::doNothing);
        addVisit("ForEachStatement", this::doNothing);
        addVisit("SwitchStatement", this::doNothing);
        addVisit("ReturnStatement", this::visitReturnStatement);
        addVisit("BreakStatement", this::visitBreakOrContinueStatement);
        addVisit("ContinueStatement", this::visitBreakOrContinueStatement);
        addVisit("ExpressionStatement", this::visitExpressionStatement);

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
        var condition = visit(node.getChildren().get(0), indentation);
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

        var condition = visit(node.getChildren().get(0), indentation + 4);
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
        conditionNode.put("topLevel", "true");
        var condition = visit(conditionNode, indentation);
        emitLine(indentation + 4, "if(", condition, ") goto ", doWhileLabels[0], ";");
        emitLine(indentation, doWhileLabels[1], ":");

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

    private String visitUnaryPostOp(JmmNode jmmNode, Integer indentation) {
        var lhs = visit(jmmNode.getJmmChild(0), indentation);

        var type = OllirUtils.toOllirType(jmmNode.get("type"));
        var operator = jmmNode.get("op") + "." + type;

        var line = lhs + " " + operator;

        if (jmmNode.getOptional("topLevel").isPresent())
            return line;

        var temp = OllirUtils.getNextTemp() + "." + type;
        emitLine(indentation, temp, " :=.", type, " ", line, ";");

        return temp;
    }

    private String visitUnaryPreOp(JmmNode jmmNode, Integer indentation) {
        var rhs = visit(jmmNode.getJmmChild(0), indentation);

        var type = OllirUtils.toOllirType(jmmNode.get("type"));
        var operator = jmmNode.get("op") + "." + type;
        if (operator.startsWith("-."))
            operator = "0.i32 " + operator;

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

        type = OllirUtils.toOllirType(type);
        var operator = jmmNode.get("op") + "." + type;

        var line = lhs + " " + operator + " " + rhs;

        if (jmmNode.getOptional("topLevel").isPresent())
            return line;

        type = OllirUtils.toOllirType(jmmNode.get("type"));
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
        var type = OllirUtils.toOllirType(commonType(lhsNode.get("type"), rhsNode.get("type")));

        lhsNode.put("type", type);
        rhsNode.put("type", type);

        lhsNode.put("topLevel", "true");
        var lhs = visit(lhsNode, indentation);

        if (lhs.startsWith("getfield(") || lhs.startsWith("getstatic(")) {
            lhs = "put" + lhs.substring(3);

            var rhs = visit(rhsNode, indentation);
            lhs = lhs.substring(0, lhs.lastIndexOf(")")) + ", " + rhs + ").V";
            return lhs;
        }

        rhsNode.put("topLevel", "true");
        var rhs = visit(rhsNode, indentation);

        if (rhs.endsWith("*"))
            rhs = rhs.substring(0, rhs.length() - 1) + type;

        emitLine(indentation, lhs, " :=.", type, " ", rhs, ";");

        if (rhs.startsWith("new(") && !rhs.startsWith("new(array,"))
            emitInvokeSpecialInit(indentation, lhs);

        return node.getOptional("topLevel").isPresent() ? null : lhs;
    }

    protected String visitLiteral(JmmNode node, Integer indentation) {
        var type = OllirUtils.toOllirType(node.get("type"));
        var value = node.get("value");

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
