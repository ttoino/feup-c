package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Method {
    private String name;
    private Type returnType;
    private List<Symbol> parameters;

    private List<Symbol> localVariables;

    private Set<String> modifiers;

    public Method(String name, Type returnType, List<Symbol> parameters, List<Symbol> localVariables, Set<String> modifiers) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.localVariables = localVariables;
    }

    public Method(String name, Type returnType, List<Symbol> parameters, List<Symbol> localVariables) {
        this(name, returnType, parameters, localVariables, new TreeSet<>());
    }

    public Method(String name, Type returnType, List<Symbol> parameters) {
        this(name, returnType, parameters, new ArrayList<>());
    }

    public Method(String name, Type returnType) {
        this(name, returnType, new ArrayList<>(), new ArrayList<>());
    }

    public Method(String name) {
        this(name, null, new ArrayList<>(), new ArrayList<>());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getReturnType() {
        return returnType;
    }

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    public List<Symbol> getParameters() {
        return parameters;
    }

    public void setParameters(List<Symbol> parameters) {
        this.parameters = parameters;
    }

    public List<Symbol> getLocalVariables() {
        return localVariables;
    }

    public void setLocalVariables(List<Symbol> localVariables) {
        this.localVariables = localVariables;
    }

    public Set<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(Set<String> modifiers) {
        this.modifiers = modifiers;
    }

    public String print() {
        var builder = new StringBuilder();

        builder.append("Name: ").append(name).append("; ");

        if (modifiers.isEmpty()) {
            builder.append("No modifiers; ");
        } else {
            builder.append("Modifiers: ");
            builder.append(String.join(", ", modifiers));
            builder.append("; ");
        }

        builder.append("Return type: ").append(returnType.print()).append("; ");

        if (parameters.isEmpty()) {
            builder.append("No parameters; ");
        } else {
            builder.append("Parameters: ");
            builder.append(parameters.stream().map(Symbol::print).collect(Collectors.joining(", ")));
            builder.append("; ");
        }

        if (localVariables.isEmpty()) {
            builder.append("No local variables; ");
        } else {
            builder.append("Local variables: ");
            builder.append(localVariables.stream().map(Symbol::print).collect(Collectors.joining(", ")));
            builder.append(";");
        }

        return builder.toString();
    }
}
