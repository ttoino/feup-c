package pt.up.fe.comp2023.optimization;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.graphs.Graph;

import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.stream.Collectors;

public class RegisterAllocator {
    private static class Node {
        String variable;
        Set<Node> neighbors = new HashSet<>();
        Set<String> defs = new HashSet<>();
        Set<String> uses = new HashSet<>();
        Set<String> ins = new HashSet<>();
        Set<String> outs = new HashSet<>();
    }

    // If the InstructionType is an instance of ASSIGN, we know that we have dest, so we can use the getDest command to get the dest var

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

    private Set<String> getDefs(Instruction instruction) {
        Set<String> uses = new HashSet<>();


        return uses;
    }


    private Set<String> getUses(Instruction instruction) {
        Set<String> defs = new HashSet<>();

        if(instruction.getInstType() == InstructionType.ASSIGN) {
            AssignInstruction assignInstruction = (AssignInstruction) instruction;
            String destVarName = assignInstruction.getDest().toString();
        }

        //ASSIGN, CALL, RETURN, , UNARYOP, BINARYOP

        return defs;
    }

    private Set<String> getDefs(Instruction instruction) {
        Set<String> defs = new HashSet<>();

        switch (instruction.getInstType()) {
            case ASSIGN:
                Operand destVar = ((AssignInstruction)instruction).getDest();
                defs.add(destVar.getName());
                break;

            case CALL:
                // TODO: Handle CallInstruction
                break;

            case RETURN:
                // TODO: Handle ReturnInstruction
                break;

            case UNARYOPER:
                // TODO: Handle UnaryOperInstruction
                break;

            case BINARYOPER:
                // TODO: Handle BinaryOperInstruction
                break;

            default:
                // Handle other types of instructions if needed
        }

        return defs;
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

    /*
    private Map<String, Integer> colorGraph(Map<String, Node> graph) {
        // TODO: Color the graph
        // Output should be a map from variables to register numbers (colors) ???
        return new HashMap<>();
    }
    */



    private void replaceWithRegisters(Method method, Map<String, Integer> colorMap) {
        // TODO: Implement this method to replace the variables in the method with the allocated registers
    }
}