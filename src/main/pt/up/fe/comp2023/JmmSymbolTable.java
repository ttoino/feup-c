package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class JmmSymbolTable implements SymbolTable {
    private final List<String> imports = new ArrayList<>();
    private final List<Symbol> fields = new ArrayList<>();
    private final List<Method> methods = new ArrayList<>();

    private String className;
    private String superName;

    private String packageName;

    private boolean hasMainMethod;

    public JmmSymbolTable(JmmNode node) {
        new Visitor().visit(node);
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superName;
    }

    public String getPackage() {
        return packageName;
    }

    public boolean hasMainMethod() {
        return hasMainMethod;
    }

    public Method getMethod(String s) {
        return methods.stream().filter(m -> m.getName().equals(s)).findFirst().orElse(null);
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        return methods.stream().map(Method::getName).collect(Collectors.toList());
    }

    @Override
    public Type getReturnType(String s) {
        return methods.stream().filter(m -> Objects.equals(m.getName(), s)).findFirst().map(Method::getReturnType).orElse(null);
    }

    @Override
    public List<Symbol> getParameters(String s) {
        return methods.stream().filter(m -> Objects.equals(m.getName(), s)).findFirst().map(Method::getParameters).orElse(null);
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        return methods.stream().filter(m -> Objects.equals(m.getName(), s)).findFirst().map(Method::getLocalVariables).orElse(null);
    }

    @Override
    public String print() {
        StringBuilder builder = new StringBuilder();
        builder.append("Class: ").append(className).append("\n");

        String superClass = superName != null ? superName : "java.lang.Object";
        builder.append("Super: ").append(superClass).append("\n");

        if (packageName != null)
            builder.append("Package: ").append(packageName).append("\n");

        if (imports.isEmpty()) {
            builder.append("\nNo imports\n");
        } else {
            builder.append("\nImports:\n");
            imports.forEach((fullImport) -> builder.append(" - ").append(fullImport).append("\n"));
        }

        if (fields.isEmpty()) {
            builder.append("\nNo fields\n");
        } else {
            builder.append("\nFields:\n");
            fields.forEach((field) -> builder.append(" - ").append(field.print()).append("\n"));
        }

        if (methods.isEmpty()) {
            builder.append("\nNo methods\n");
        } else {
            builder.append("\nMethods:\n");
            methods.forEach((method) -> builder.append(" - ").append(method.print()).append("\n"));
        }

        return builder.toString();
    }

    private class Visitor extends AJmmVisitor<Object, Object> {
        @Override
        protected void buildVisitor() {
            setDefaultVisit(this::visitOther);
            addVisit("PackageDeclaration", this::visitPackage);
            addVisit("ImportStatement", this::visitImport);
            addVisit("ClassDeclaration", this::visitClass);
            addVisit("ParentClass", this::visitParentClass);
            addVisit("MethodDeclaration", this::visitMethod);
            addVisit("ConstructorDeclaration", this::visitMethod);
            addVisit("ParameterList", this::visitParameters);
            addVisit("VariableDeclaration", this::visitVariable);
            addVisit("PrimitiveType", this::visitType);
            addVisit("VoidType", this::visitType);
            addVisit("ComplexType", this::visitType);
            addVisit("ArrayType", this::visitType);
        }

        private Object visitOther(JmmNode node, Object context) {
            for (var child : node.getChildren())
                visit(child, context);

            return context;
        }

        private Object visitClass(JmmNode node, Object context) {
            className = node.get("className");

            for (var child : node.getChildren())
                visit(child, context);

            return context;
        }

        private Object visitParentClass(JmmNode node, Object context) {

            StringBuilder superNameBuilder = new StringBuilder();

            var parentPackage = node.getObjectAsList("parentPackage", String.class);

            superNameBuilder.append(parentPackage.stream().map(s -> s + '.').collect(Collectors.joining()));
            superNameBuilder.append(node.get("parentClass"));

            superName = superNameBuilder.toString();

            return context;
        }

        private Object visitPackage(JmmNode node, Object context) {
            packageName = node.getObjectAsList("packagePath", String.class).stream().map(s -> s + '.').collect(Collectors.joining()) + node.get("packageName");

            return context;
        }

        private Object visitImport(JmmNode node, Object context) {
            imports.add(
                    String.join("", node.getObjectAsList("classPackage", String.class)
                            + node.get("className")));

            return context;
        }

        private Method visitMethod(JmmNode node, Object context) {
            var method = new Method(
                    node.getKind().equals("ConstructorDeclaration") ? "<constructor>" : node.get("methodName"),
                    new Type("void", false));

            methods.add(method);

            method.setModifiers(new TreeSet<>(node.getObjectAsList("modifiers", String.class)));

            if (method.getName().equals("main"))
                hasMainMethod = true;

            for (var child : node.getChildren())
                visit(child, method);

            return method;
        }

        private Type visitType(JmmNode node, Object context) {
            Type type;

            // FIXME: THIS CODE WAS DONE AT 5AM

            var typeId = node.getOptional("id");

            if (typeId.isPresent()) {
                var realTypeId = typeId.get(); // TODO: ew

                try { // Complex type
                    var typePrefix = node.getObjectAsList("typePrefix", String.class);

                    var actualType = typePrefix.stream().map(s -> s + '.').collect(Collectors.joining()) + realTypeId;

                    type = new Type(actualType, false);
                } catch (Exception e) { // Simple type
                    type = new Type(realTypeId, false);
                }
            } else if (node.getNumChildren() == 1) // Array Type
                type = new Type(((Type) visit(node.getJmmChild(0), context)).getName(), true);
            else // Void Type
                type = new Type("void", false);

            if (node.getJmmParent().getKind().equals("MethodDeclaration") && context instanceof Method)
                ((Method) context).setReturnType(type);

            node.put("type", type.print());

            return type;
        }

        private List<Symbol> visitParameters(JmmNode node, Object context) {
            assert context instanceof Method;

            var params = node.getObjectAsList("argName");

            Method method = (Method) context;
            for (int i = 0; i < node.getChildren().size(); ++i) {
                Type type = (Type) visit(node.getJmmChild(i), method.getParameters());

                Symbol parameter = new Symbol(type, params.get(i).toString());

                method.getParameters().add(parameter);
            }

            return method.getParameters();
        }

        private Symbol visitVariable(JmmNode node, Object context) {
            var symbol = new Symbol((Type) visit(node.getJmmChild(0)), node.get("id"));

            if (context instanceof Method) ((Method) context).getLocalVariables().add(symbol);
            else fields.add(symbol);

            return symbol;
        }
    }
}
