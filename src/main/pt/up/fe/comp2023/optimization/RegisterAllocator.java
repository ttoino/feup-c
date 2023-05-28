package pt.up.fe.comp2023.optimization;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Instruction;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.*;
public class RegisterAllocator {
    private static class Node {
        String variable;
        Set<Node> neighbors = new HashSet<>();
        // Other stuff...
    }

    public OllirResult optimizeRegisters(OllirResult ollirResult) {
        ClassUnit ollirClass = ollirResult.getOllirClass();
        ollirClass.buildCFGs();


        // Foreach method in the class, perform liveness analysis,
        // TODO: build the interference graph, and color the graph.
        for (Method method : ollirClass.getMethods()) {
            method.
            List<String> variables = parseVariables(method);
            Map<String, Node> graph = buildInterferenceGraph(method, variables);
            Map<String, Integer> colorMap = colorGraph(graph);

            replaceWithRegisters(method, colorMap);
        }

        return ollirResult;
    }

    private List<String> parseVariables(Method method) {
        // Create a list to store the variable names
        List<String> variables = new ArrayList<>();

        // Iterate over the instructions of the method
        for (Instruction instruction : method.getInstructions()) {
            // TODO: Extract variables from instruction and add to the list
        }

        return variables;
    }

    private Map<String, Node> buildInterferenceGraph(Method method, List<String> variables) {
        // TODO: Perform liveness analysis and build the interference graph
        // TODO: create a Node for each variable, and add edges between nodes that interfere with each other
        return new HashMap<>();
    }

    private Map<String, Integer> colorGraph(Map<String, Node> graph) {
        // TODO: Color the graph
        // Output should be a map from variables to register numbers (colors) ???
        return new HashMap<>();
    }

    private void replaceWithRegisters(Method method, Map<String, Integer> colorMap) {
        // TODO: Implement this method to replace the variables in the method with the allocated registers
    }
}