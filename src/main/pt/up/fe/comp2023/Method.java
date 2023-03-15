package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.List;

public class Method {
    private String name;
    private Type returnType;
    private List<Symbol> parameters;

    private List<Symbol> localVariables;

    public Method(String name, Type returnType, List<Symbol> parameters, List<Symbol> localVariables) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.localVariables = localVariables;
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
}
