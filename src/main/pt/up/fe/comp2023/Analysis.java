package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static pt.up.fe.comp2023.Constants.*;

public class Analysis implements JmmAnalysis {
    private JmmSymbolTable table;

    private final List<Report> reports = new ArrayList<>();

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {
        table = new JmmSymbolTable(jmmParserResult.getRootNode());

        var main = table.getMethod("main");
        if (main == null) {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Could not find the main method"));
        } else {
            if (!main.getReturnType().print().equals("void"))
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "The main method must return void"));
            if (main.getParameters().size() != 1 || !main.getParameters().get(0).getType().print().equals("String[]"))
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "The main method must take an argument of type 'String[]'"));
            if (!main.getModifiers().contains("public") || !main.getModifiers().contains("static"))
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "The main method must be public and static"));
        }

        new SemanticAnalysisVisitor().visit(jmmParserResult.getRootNode());

        return new JmmSemanticsResult(
                jmmParserResult.getRootNode(),
                table,
                reports,
                jmmParserResult.getConfig());
    }

    private class SemanticAnalysisVisitor extends AJmmVisitor<String, String> {
        SemanticAnalysisVisitor() {
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
            addVisit("ClassDeclaration", this::checkModifiers);
            addVisit("MethodDeclaration", this::checkModifiers);
            addVisit("ConstructorDeclaration", this::checkModifiers);
            addVisit("FieldDeclaration", this::checkModifiers);
        }

        @Override
        public String visit(JmmNode jmmNode, String data) {
            data = jmmNode.getOptional("methodName").orElse(data);

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

            for (var imp : table.getImports())
                if (imp.equals(id))
                    type = id;
            for (var field : table.getFields())
                if (field.getName().equals(id))
                    type = field.getType().print();
            for (var parameter : table.getParametersTry(context).orElse(new ArrayList<>()))
                if (parameter.getName().equals(id))
                    type = parameter.getType().print();
            for (var local : table.getLocalVariablesTry(context).orElse(new ArrayList<>()))
                if (local.getName().equals(id))
                    type = local.getType().print();

            if (in(UNIVERSAL_IMPORTS, id))
                type = id;

            if (type == null)
                error(node, "Cannot access variable '" + id + "' without declaration");

            node.put("type", type == null ? "{unknown}" : type);

            if (node.getNumChildren() > 1) {
                var expType = node.getJmmChild(1).get("type");

                if (!typesMatch(type, expType))
                    error(node, "Cannot assign expression of type '" + expType + "' to variable '" + id + "' of type '" + type + "'");
            }

            node.put("canAssign", "true");

            return context;
        }

        protected String checkUnary(JmmNode node, String context) {
            var type = node.getJmmChild(0).get("type");
            var op = node.get("op");

            if (!(in(INTEGER_TYPES, type) && in(INTEGER_OPS, op)
                || in(FLOAT_TYPES, type) && in(FLOAT_OPS, op)
                || type.equals("boolean") && in(BOOLEAN_OPS, op)
                || in(UNIVERSAL_OPS, op)))
                error(node, "Cannot use '" + op + "' on expression of type '" + type + "'");

            return context;
        }

        protected String checkBinary(JmmNode node, String context) {
            var type1 = node.getJmmChild(0).get("type");
            var type2 = node.getJmmChild(1).get("type");
            var op = node.get("op");

            if (!(in(INTEGER_TYPES, type1) && in(INTEGER_TYPES, type2) && in(INTEGER_OPS, op)
                    || in(FLOAT_TYPES, type1) && in(FLOAT_TYPES, type2) && in(FLOAT_OPS, op)
                    || type1.equals("boolean") && type2.equals("boolean") && in(BOOLEAN_OPS, op)
                    || type1.equals("String") && type2.equals("String") && op.equals("+")
                    || typesMatch(type1, type2) && in(UNIVERSAL_OPS, op)))
                error(node, "Cannot use '" + op + "' on expressions of type '" + type1 + "' and '" + type2 + "'");

            node.put("type", type1);

            return context;
        }

        protected String checkTernary(JmmNode node, String context) {
            var type1 = node.getJmmChild(0).get("type");
            var type2 = node.getJmmChild(1).get("type");
            var type3 = node.getJmmChild(2).get("type");

            if (!type1.equals("boolean"))
                error(node, "Cannot use expression of type '" + type1 + "' as ternary expression");

            if (typesMatch(type1, type2))
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

            if (!type1.endsWith("[]"))
                error(node, "Cannot index expression of type '" + type1 + "'");

            if (!in(INTEGER_TYPES, type2))
                error(node, "Cannot use expression of type '" + type2 + "' as index");

            node.put("type", type1.substring(0, type2.length() - 2));
            node.put("canAssign", "true");

            return context;
        }

        protected String checkPropertyAccess(JmmNode node, String context) {
            node.put("canAssign", "true");
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

            if (!(in(INTEGER_TYPES, type1) && in(INTEGER_TYPES, type2) && in(INTEGER_OPS, op)
                || in(FLOAT_TYPES, type1) && (in(FLOAT_TYPES, type2) || in(INTEGER_TYPES, type2)) && in(FLOAT_OPS, op)
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

        protected String checkModifiers(JmmNode node, String context) {
            var modifiers = node.getObjectAsList("modifiers", String.class);
            Set<String> used = new TreeSet<>();
            var allowed = switch (node.getKind()) {
                case "ClassDeclaration" -> CLASS_MODIFIERS;
                case "MethodDeclaration" -> METHOD_MODIFIERS;
                case "ConstructorDeclaration" -> CONSTRUCTOR_MODIFIERS;
                case "FieldDeclaration" -> FIELD_MODIFIERS;
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
                    // First is superclass of second
                    || table.getSuper().equals(type1) && table.getClassName().equals(type2)
                    // Both equal
                    || type1.equals(type2);
        }

        private <T> boolean in(T[] arr, T i) {
            for (var x : arr)
                if (x.equals(i)) return true;
            return false;
        }

        private void error(JmmNode node, String message) {
            reports.add(new Report(
                    ReportType.ERROR,
                    Stage.SEMANTIC,
                    Integer.parseInt(node.get("lineStart")),
                    Integer.parseInt(node.get("colStart")),
                    message));
        }
    }
}
