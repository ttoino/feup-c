package pt.up.fe.comp2023.optimization;

import org.specs.comp.ollir.AssignInstruction;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Instruction;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.graphs.Graph;

import java.util.*;

public class RegisterAllocator {
    private static class Node {
        String variable;
        Set<Node> neighbors = new HashSet<>();
        Set<String> defs = new HashSet<>();
        Set<String> uses = new HashSet<>();
        Set<String> ins = new HashSet<>();
        Set<String> outs = new HashSet<>();
    }

    public OllirResult optimizeRegisters(OllirResult ollirResult) {
        ClassUnit ollirClass = ollirResult.getOllirClass();
        ollirClass.buildCFGs();

        // Foreach method in the class, perform liveness analysis,
        // TODO: build the interference graph, and color the graph.
        for (Method method : ollirClass.getMethods()) {
            List<Node> nodes = parseVariables(method);
            buildInterferenceGraph(nodes);
            Map<String, Integer> colorMap = colorGraph(nodes);

            replaceWithRegisters(method, colorMap);
        }

        return ollirResult;
    }

    private List<Node> parseVariables(Method method) {
        List<Node> nodes = new ArrayList<>();

        for (Instruction instruction : method.getInstructions()) {
            Node node = new Node();

            node.defs = getDefs(instruction);
            node.uses = getUses(instruction);

            nodes.add(node);
        }

        computeInsOuts(nodes);

        return nodes;
    }

    private Set<String> getUses(Instruction instruction) {
        // TODO: get usesg
        return new HashSet<>();
    }

    private Set<String> getDefs(Instruction instruction) {
        //TODO: get defs
        return new HashSet<>();
    }

    private void computeInsOuts(List<Node> nodes) {
        // Compute ins and outs iteratively
        boolean changed;
        do {
            changed = false;
            for (Node node : nodes) {
                int oldInsSize = node.ins.size();
                int oldOutsSize = node.outs.size();

                node.ins.clear();
                node.ins.addAll(node.uses);

                for (String outVar : node.outs)
                    if (!node.defs.contains(outVar))
                        node.ins.add(outVar);


                for (Node neighbor : node.neighbors)
                    node.outs.addAll(neighbor.ins);

                if (node.ins.size() != oldInsSize || node.outs.size() != oldOutsSize)
                    changed = true;
            }
        } while (changed);
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