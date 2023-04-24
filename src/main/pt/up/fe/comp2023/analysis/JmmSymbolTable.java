package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class JmmSymbolTable implements SymbolTable {
    private final List<String> imports = new ArrayList<>();
    private final List<Symbol> fields = new ArrayList<>();
    private final List<Method> methods = new ArrayList<>();

    private String className;
    private String superName;

    private String packageName;

    public JmmSymbolTable(JmmNode node) {
        new SymbolTableVisitor(this).visit(node);
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

    public void addImport(String fullImport) {
        imports.add(fullImport);
    }

    public void addField(Symbol field) {
        fields.add(field);
    }

    public void addMethod(Method method) {
        methods.add(method);
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setSuperName(String superName) {
        this.superName = superName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
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

}
