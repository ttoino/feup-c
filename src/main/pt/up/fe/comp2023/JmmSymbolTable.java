package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.*;

public class JmmSymbolTable implements SymbolTable {
    private final List<String> imports = new ArrayList<>();
    private final List<Symbol> fields = new ArrayList<>();
    private final List<Method> methods = new ArrayList<>();

    private String className;
    private String superName;

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

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        return methods.stream().map(Method::getName).toList();
    }

    @Override
    public Type getReturnType(String s) {
        return methods.stream().filter(m -> Objects.equals(m.getName(), s)).findFirst().get().getReturnType();
    }

    @Override
    public List<Symbol> getParameters(String s) {
        return methods.stream().filter(m -> Objects.equals(m.getName(), s)).findFirst().get().getParameters();
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        return methods.stream().filter(m -> Objects.equals(m.getName(), s)).findFirst().get().getLocalVariables();
    }

    private class Visitor extends AJmmVisitor<Object, Object> {
        @Override
        protected void buildVisitor() {
            setDefaultVisit(this::visitOther);
            addVisit("ClassDeclaration", this::visitClass);
            addVisit("MethodDeclaration", this::visitMethod);
            addVisit("ParameterList", this::visitParameters);
            addVisit("SimpleType", this::visitType);
            addVisit("PrimitiveType", this::visitType);
            addVisit("ArrayType", this::visitType);
            addVisit("VariableDeclaration", this::visitVariable);
            addVisit("ImportStatement", this::visitImport);
        }

        private Object visitOther(JmmNode node, Object context) {
            for (var child : node.getChildren())
                visit(child, context);

            return context;
        }

        private Object visitClass(JmmNode node, Object context) {
            className = node.get("className");
            superName = node.getOptional("parentClass").orElse(null);

            for (var child : node.getChildren())
                visit(child, context);

            return context;
        }

        private Object visitImport(JmmNode node, Object context) {
            imports.add(node.get("className"));

            return context;
        }

        private Object visitMethod(JmmNode node, Object context) {
            var method = new Method(node.get("methodName"));

            methods.add(method);

            for (var child : node.getChildren())
                visit(child, method);

            return method;
        }

        private Object visitType(JmmNode node, Object context) {
            Type type;

            if (node.getOptional("id").isPresent())
                type = new Type(node.get("id"), false);
            else
                type = new Type(((Type) visit(node.getJmmChild(0), context)).getName(), true);

            if (context instanceof Method)
                ((Method) context).setReturnType(type);

            return type;
        }

        private Object visitParameters(JmmNode node, Object context) {
            assert context instanceof Method;

            var params = (List<Object>) node.getOptionalObject("argName").orElse(new ArrayList<>());

            for (int i = 0; i < node.getChildren().size(); ++i)
                ((Method) context).getParameters().add(new Symbol((Type) visit(node.getJmmChild(i), ((Method) context).getParameters()), params.get(i).toString()));

            return ((Method) context).getParameters();
        }

        private Object visitVariable(JmmNode node, Object context) {
            var symbol = new Symbol((Type) visit(node.getJmmChild(0)), node.get("id"));

            if (context instanceof Method)
                ((Method) context).getLocalVariables().add(symbol);
            else
                fields.add(symbol);

            return symbol;
        }
    }
}
