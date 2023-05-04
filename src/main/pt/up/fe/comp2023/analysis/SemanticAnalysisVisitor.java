package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static pt.up.fe.comp2023.Constants.*;
import static pt.up.fe.comp2023.Utils.in;

class SemanticAnalysisVisitor extends AJmmVisitor<String, String> {
    private final List<Report> reports;
    private final JmmSymbolTable table;

    SemanticAnalysisVisitor(JmmSymbolTable table, List<Report> reports) {
        this.table = table;
        this.reports = reports;
        buildVisitor();
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::visitDefault);
        addVisit("IdentifierExpression", this::checkDeclared);
        addVisit("VariableDeclaration", this::checkDeclared);
        addVisit("ThisExpression", this::checkThis);
        addVisit("UnaryPreOp", this::checkUnary);
        addVisit("UnaryPostOp", this::checkUnary);
        addVisit("BinaryOp", this::checkBinary);
        addVisit("TernaryOp", this::checkTernary);
        addVisit("LiteralExpression", this::checkLiteral);
        addVisit("ArrayAccess", this::checkArrayAccess);
        addVisit("ExplicitPriority", this::checkExplicitPriority);
        addVisit("AssignmentExpression", this::checkAssignment);
        addVisit("PropertyAccess", this::checkPropertyAccess);
        addVisit("MethodCall", this::checkMethodCall);
        addVisit("NewObject", this::checkNewObject);
        addVisit("NewArray", this::checkNewArray);
        addVisit("ClassDeclaration", this::checkModifiers);
        addVisit("MethodDeclaration", this::checkModifiers);
        addVisit("ConstructorDeclaration", this::checkConstructor);
        addVisit("FieldDeclaration", this::checkModifiers);
        addVisit("ReturnStatement", this::checkReturn);
        addVisit("BreakStatement", this::checkBreak);
        addVisit("ContinueStatement", this::checkContinue);
        addVisit("IfStatement", this::checkIf);
        addVisit("WhileStatement", this::checkWhile);
        addVisit("DoStatement", this::checkDo);
        addVisit("ForStatement", this::checkFor);
        addVisit("ForTerm", this::checkForTerminal);
        addVisit("ForEachStatement", this::checkForEach);
        addVisit("SwitchStatement", this::checkSwitch);
        addVisit("CaseStatement", this::checkCase);
        addVisit("DefaultStatement", this::checkDefault);
        addVisit("ComplexType", this::checkComplexType);
    }

    @Override
    public String visit(JmmNode jmmNode, String data) {
        data = jmmNode.getOptional("methodName").orElse(jmmNode.getKind().equals("ConstructorDeclaration") ? "<constructor>" : data);

        for (var child : jmmNode.getChildren())
            visit(child, data);

        return super.visit(jmmNode, data);
    }

    protected String visitDefault(JmmNode node, String context) {
        return context;
    }

    protected String checkThis(JmmNode node, String context) {
        node.put("type", table.getClassName());
        var method = table.getMethod(context);
        if (method == null)
            error(node, "Cannot use 'this' outside a method");
        else if (method.getModifiers().contains("static"))
            error(node, "Cannot use 'this' in static method");
        return context;
    }

    protected String checkDeclared(JmmNode node, String context) {
        var id = node.get("id");
        String type = null;
        var method = table.getMethod(context);
        var isStatic = method != null && method.getModifiers().contains("static");

        for (var imp : table.getImports()) {
            var split = imp.split("\\.");
            if (split[split.length - 1].equals(id)) {
                type = id;
                node.put("origin", "import");
                break;
            }
        }
        // TODO: static fields
        if (!isStatic)
            for (var field : table.getFields())
                if (field.getName().equals(id)) {
                    type = field.getType().print();
                    node.put("origin", "field");
                    break;
                }
        for (var parameter : table.getParametersTry(context).orElse(new ArrayList<>()))
            if (parameter.getName().equals(id)) {
                type = parameter.getType().print();
                node.put("origin", "parameter");
                break;
            }
        for (var local : table.getLocalVariablesTry(context).orElse(new ArrayList<>()))
            if (local.getName().equals(id)) {
                type = local.getType().print();
                node.put("origin", "local");
                break;
            }

        if (in(UNIVERSAL_IMPORTS, id)) {
            type = id;
            node.put("origin", "import");
        }

        if (type == null) {
            error(node, "Cannot access variable '" + id + "' without declaration");
            type = "{unknown}";
        }

        node.put("type", type);

        if (node.getKind().equals("VariableDeclaration") && node.get("type").equals("void"))
            error(node, "Cannot declare variable of type 'void'");

        if (node.getNumChildren() > 1) {
            var expType = node.getJmmChild(1).get("type");

            if (!typesMatch(type, expType))
                error(node, "Cannot assign expression of type '" + expType + "' to variable '" + id + "' of type '" + type + "'");
        }

        node.put("canAssign", "true");

        return context;
    }

    protected String checkUnary(JmmNode node, String context) {
        var child = node.getJmmChild(0);
        var type = child.get("type");
        var op = node.get("op");

        if (!(typesMatch(type, "int") && in(INTEGER_OPS, op)
                || typesMatch(type, "float") && in(FLOAT_OPS, op)
                || typesMatch(type, "boolean") && in(BOOLEAN_OPS, op)
                || in(UNIVERSAL_OPS, op)))
            error(node, "Cannot use '" + op + "' on expression of type '" + type + "'");

        if ((op.equals("++") || op.equals("--")) && child.getOptional("canAssign").isEmpty())
            error(node, "Cannot use '" + op + "' on this expression");

        node.put("type", type);

        return context;
    }

    protected String checkBinary(JmmNode node, String context) {
        var type1 = node.getJmmChild(0).get("type");
        var type2 = node.getJmmChild(1).get("type");
        var op = node.get("op");

        if (!(typesMatch(type1, "int") && typesMatch(type2, "int") && in(INTEGER_OPS, op)
                || typesMatch(type1, "float") && (typesMatch(type2, "float") || typesMatch(type2, "int")) && in(FLOAT_OPS, op)
                || typesMatch(type1, "boolean") && typesMatch(type2, "boolean") && in(BOOLEAN_OPS, op)
                || typesMatch(type1, "String") && typesMatch(type2, "String") && op.equals("+")
                || (typesMatch(type1, type2) || typesMatch(type2, type1)) && in(UNIVERSAL_OPS, op)))
            error(node, "Cannot use '" + op + "' on expressions of type '" + type1 + "' and '" + type2 + "'");

        node.put("type", type2.equals("*") ? type2 : type1);
        if (in(COMPARISON_OPS, op))
            node.put("type", "boolean");

        return context;
    }

    protected String checkTernary(JmmNode node, String context) {
        var type1 = node.getJmmChild(0).get("type");
        var type2 = node.getJmmChild(1).get("type");
        var type3 = node.getJmmChild(2).get("type");

        if (!typesMatch(type1, "boolean"))
            error(node, "Cannot use expression of type '" + type1 + "' as ternary expression");

        if (!(typesMatch(type2, type3) || typesMatch(type3, type2)))
            error(node, "Cannot use expressions of type '" + type2 + "' and '" + type3 + "' as ternary arms");

        node.put("type", type2);

        return context;
    }

    protected String checkLiteral(JmmNode node, String context) {
        var value = node.get("value");

        if (value.equals("false") || value.equals("true"))
            node.put("type", "boolean");
        else if (value.equals("null"))
            node.put("type", "Object");
        else if (value.startsWith("\""))
            node.put("type", "String");
        else if (value.startsWith("'"))
            node.put("type", "char");
        else if (value.contains("."))
            node.put("type", "double");
        else
            node.put("type", "int");

        return context;
    }

    protected String checkArrayAccess(JmmNode node, String context) {
        var type1 = node.getJmmChild(0).get("type");
        var type2 = node.getJmmChild(1).get("type");

        if (!type1.endsWith("[]") && !type1.equals("*"))
            error(node, "Cannot index expression of type '" + type1 + "'");

        if (!typesMatch(type2, "int"))
            error(node, "Cannot use expression of type '" + type2 + "' as index");

        node.put("type", type1.endsWith("[]") ? type1.substring(0, type1.length() - 2) : "*");
        node.put("canAssign", "true");

        return context;
    }

    protected String checkPropertyAccess(JmmNode node, String context) {
        node.put("canAssign", "true");

        var type = node.getJmmChild(0).get("type");
        var prop = node.get("member");

        if (typesMatch(type, table.getClassName())) {
            var field = table.getFields().stream().filter(s -> s.getName().equals(prop)).findFirst();

            if (field.isEmpty() && table.getSuper().isEmpty()) {
                error(node, "Cannot access property '" + prop + "' on object of type '" + type + "'");
                node.put("type", "*");
            } else if (field.isPresent()) {
                node.put("type", field.get().getType().print());
            } else {
                node.put("type", "*");
            }
        } else if (type.endsWith("[]")) {
            if (prop.equals("length")) {
                node.put("type", "int");
            } else {
                error(node, "Cannot access property '" + prop + "' on object of type '" + type + "'");
                node.put("type", "{unknown}");
            }
        } else {
            node.put("type", "*");
        }

        return context;
    }

    protected String checkExplicitPriority(JmmNode node, String context) {
        node.put("type", node.getJmmChild(0).get("type"));
        if (node.getJmmChild(0).getOptional("canAssign").isPresent())
            node.put("canAssign", "true");
        return context;
    }

    protected String checkAssignment(JmmNode node, String context) {
        var type1 = node.getJmmChild(0).get("type");
        var type2 = node.getJmmChild(1).get("type");
        var op = node.get("op");

        if (!(typesMatch(type1, "int") && typesMatch(type2, "int") && in(INTEGER_OPS, op)
                || typesMatch(type1, "float") && (typesMatch(type2, "float") || typesMatch(type2, "int")) && in(FLOAT_OPS, op)
                || typesMatch(type1, type2) && (in(UNIVERSAL_OPS, op) || type1.equals("String") && op.equals("+="))))
            error(node, "Cannot assign expression of type '" + type2 + "' to expression of type '" + type1 + "' with operation '" + op + "'");

        if (node.getJmmChild(0).getOptional("canAssign").isEmpty())
            error(node, "Cannot assign to that expression");

        node.put("type", type1);

        return context;
    }

    protected String checkMethodCall(JmmNode node, String context) {
        String type = table.getClassName();
        var method = node.get("member");
        List<String> actual = new ArrayList<>();

        for (var child : node.getChildren()) {
            if (child.getKind().equals("ArgumentList")) {
                actual = child.getChildren().stream().map(n -> n.get("type")).toList();
            } else {
                type = child.get("type");
            }
        }

        node.put("type", "*");

        if (in(PRIMITIVE_TYPES, type))
            error(node, "Cannot call method '" + method + "' on expression of type '" + type + "'");

        if (table.getClassName().equals(type)) {
            if (table.getMethods().contains(method)) {
                var expected = table.getParameters(method).stream().map(s -> s.getType().print()).toList();

                node.put("type", table.getReturnType(method).print());

                if (expected.size() != actual.size())
                    error(node, "Cannot call '" + type + "::" + method + "' with " + actual.size() + " arguments, expected " + expected.size());

                for (int i = 0; i < Math.min(expected.size(), actual.size()); ++i)
                    if (!typesMatch(expected.get(i), actual.get(i)))
                        error(node, "Cannot call '" + type + "::" + method + "' with " + (i + 1) + "th argument of type '" + actual.get(i) + "', expected '" + expected.get(i) + "'");
            } else if (table.getSuper() == null) {
                error(node, "Cannot call method '" + method + "' on expression of type '" + type + "'");
            }
        }

        return context;
    }

    protected String checkNewObject(JmmNode node, String context) {
        var id = node.get("id");
        node.put("type", id);

        return context;
    }

    protected String checkNewArray(JmmNode node, String context) {
        var type = node.getJmmChild(0).get("type");
        var index = node.getJmmChild(1).get("type");

        node.put("type", type + "[]");

        if (!typesMatch(index, "int"))
            error(node, "Cannot create new array with expression of type '" + index + "' as length");

        return context;
    }

    protected String checkModifiers(JmmNode node, String context) {
        var modifiers = node.getObjectAsList("modifiers", String.class);
        Set<String> used = new TreeSet<>();
        var allowed = switch (node.getKind()) {
            case "ClassDeclaration" -> CLASS_MODIFIERS;
            case "MethodDeclaration" -> METHOD_MODIFIERS;
            case "ConstructorDeclaration" -> CONSTRUCTOR_MODIFIERS;
            case "FieldDeclaration" -> context == null ? FIELD_MODIFIERS : VARIABLE_MODIFIERS;
            default -> new String[]{};
        };

        for (String modifier : modifiers) {
            if (used.contains(modifier))
                error(node, "Duplicated modifier '" + modifier + "'");

            used.add(modifier);

            if (!in(allowed, modifier))
                error(node, "Cannot use modifier '" + modifier + "' here");
        }

        return context;
    }

    protected String checkConstructor(JmmNode node, String context) {
        checkModifiers(node, context);

        var className = table.getClassName();
        var constructorName = node.get("className");
        if (!constructorName.equals(className))
            error(node, "Constructor for class '" + className + "' cannot be called '" + constructorName + "'");

        return context;
    }

    protected String checkReturn(JmmNode node, String context) {
        var returnType = table.getReturnType(context).print();
        var type = "void";

        if (node.getNumChildren() > 0)
            type = node.getJmmChild(0).get("type");

        if (!typesMatch(returnType, type))
            error(node, "Cannot return expression of type '" + type + "' in method with return type '" + returnType + "'");

        return context;
    }

    protected String checkBreak(JmmNode node, String context) {
        if (node.getAncestor("ForStatement")
                .or(() -> node.getAncestor("ForEachStatement"))
                .or(() -> node.getAncestor("WhileStatement"))
                .or(() -> node.getAncestor("DoStatement"))
                .or(() -> node.getAncestor("SwitchStatement")).isEmpty())
            error(node, "Cannot have a break statement outside a loop or switch statement");

        return context;
    }

    protected String checkContinue(JmmNode node, String context) {
        if (node.getAncestor("ForStatement")
                .or(() -> node.getAncestor("ForEachStatement"))
                .or(() -> node.getAncestor("WhileStatement"))
                .or(() -> node.getAncestor("DoStatement")).isEmpty())
            error(node, "Cannot have a continue statement outside a loop");

        return context;
    }

    protected String checkIf(JmmNode node, String context) {
        var type = node.getJmmChild(0).get("type");
        if (!typesMatch(type, "boolean"))
            error(node, "Cannot use expression of type '" + type + "' inside if statement");

        return context;
    }

    protected String checkWhile(JmmNode node, String context) {
        var type = node.getJmmChild(0).get("type");
        if (!typesMatch(type, "boolean"))
            error(node, "Cannot use expression of type '" + type + "' inside while statement");

        return context;
    }

    protected String checkDo(JmmNode node, String context) {
        var type = node.getJmmChild(1).get("type");
        if (!typesMatch(type, "boolean"))
            error(node, "Cannot use expression of type '" + type + "' inside do statement");

        return context;
    }

    protected String checkFor(JmmNode node, String context) {
        return context;
    }

    protected String checkForTerminal(JmmNode node, String context) {
        var type = node.getJmmChild(0).get("type");
        if (!typesMatch(type, "boolean"))
            error(node, "Cannot use expression of type '" + type + "' as for statement terminal");

        return context;
    }

    protected String checkForEach(JmmNode node, String context) {
        var type = node.getJmmChild(0).get("type");
        var expressionType = node.getJmmChild(1).get("type");

        if (!typesMatch(expressionType, type + "[]"))
            error(node, "Cannot use expression of type '" + expressionType + "' as array of '" + type + "' in for each statement");

        return context;
    }

    protected String checkSwitch(JmmNode node, String context) {
        var type = node.getJmmChild(0).get("type");
        if (!in(PRIMITIVE_TYPES, type) && !type.equals("String"))
            error(node, "Cannot use expression of type '" + type + "' inside switch statement");

        return context;
    }

    protected String checkCase(JmmNode node, String context) {
        var switchNode = node.getJmmParent();
        var switchExpression = switchNode.getJmmChild(0);
        var switchType = switchExpression.get("type");

        var casesOpt = switchNode.getOptionalObject("cases");
        if (casesOpt.isEmpty()) {
            switchNode.putObject("cases", new TreeSet<String>());
            casesOpt = switchNode.getOptionalObject("cases");
        }
        var cases = (Set<String>) casesOpt.get();
        var value = node.get("value");

        if (cases.contains(value))
            error(node, "Cannot have more than one case with value '" + value + "' in switch statement");

        cases.add(value);

        checkLiteral(node, context);
        var type = node.get("type");

        if (!typesMatch(type, switchType))
            error(node, "Cannot use expression of type '" + type + "' in case inside switch statement with expression of type '" + switchType + "'");

        if (switchType.equals("*"))
            switchExpression.put("type", type);

        return context;
    }

    protected String checkDefault(JmmNode node, String context) {
        var switchNode = node.getJmmParent();

        if (switchNode.getOptional("hasDefault").isPresent())
            error(node, "Cannot have more than one default case in switch statement");

        switchNode.put("hasDefault", "true");

        return context;
    }

    protected String checkComplexType(JmmNode node, String context) {
        var type = node.get("id");
        node.put("type", type);

        if (!type.equals(table.getClassName()) && !in(UNIVERSAL_IMPORTS, type) && table.getImports().stream().map(i -> {
            var split = i.split("\\.");
            return split[split.length - 1];
        }).noneMatch(i -> i.equals(type)))
            error(node, "Cannot use '" + type + "' as a type without importing it");

        return context;
    }

    /**
     * @return `true` if `type1` matches `type2`
     */
    private boolean typesMatch(String type1, String type2) {
        // Either is wildcard
        return type1.equals("*") || type2.equals("*")
            // Both ints
            || in(INTEGER_TYPES, type1) && in(INTEGER_TYPES, type2)
            // Both floats
            || in(FLOAT_TYPES, type1) && in(FLOAT_TYPES, type2)
            // First is Object and second is not primitive
            || type1.equals("Object") && !in(PRIMITIVE_TYPES, type2)
            // Both are imported
            || table.getImports().stream().map(i -> {
                var split = i.split("\\.");
                return split[split.length - 1];
            }).collect(Collectors.toSet()).containsAll(List.of(type1, type2))
            // First is superclass of second
            || table.getSuper() != null && table.getSuper().equals(type1) && table.getClassName().equals(type2)
            // Both equal
            || type1.equals(type2);
    }


    private void error(JmmNode node, String message) {
        reports.add(new Report(
            ReportType.ERROR,
            Stage.SEMANTIC,
            Integer.parseInt(node.get("lineStart")),
            Integer.parseInt(node.get("colStart")),
            message)
        );
    }
}
