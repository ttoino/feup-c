package pt.up.fe.comp2023.optimization;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.graphs.Graph;

import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.stream.Collectors;

public class RegisterAllocator {
    private static class Node {
        Set<String> defs = new HashSet<>();
        Set<String> uses = new HashSet<>();
        Set<String> ins = new HashSet<>();
        Set<String> outs = new HashSet<>();
    }

    public OllirResult optimizeRegisters(OllirResult ollirResult) {
        ClassUnit ollirClass = ollirResult.getOllirClass();

        for (Method method : ollirClass.getMethods()) {
            var nodes = parseVariables(method);
            var graph = buildInterferenceGraph(nodes);
            var colorMap = colorGraph(graph);

            int maxRegsAllowed = Integer.parseInt(ollirResult.getConfig().get("registerAllocation"));
            var colors = new TreeSet<>(colorMap.values());
            if (maxRegsAllowed > 0 && colors.size() > maxRegsAllowed)
                throw new IllegalStateException("More regs than supposed");

            replaceWithRegisters(method, colorMap);
        }

        return ollirResult;
    }


    private List<Node> parseVariables(Method method) {
        List<Node> nodes = new ArrayList<>();

        // Create empty sets for live-ins and live-outs of instructions
        Map<Instruction, Set<String>> liveIns = new HashMap<>();
        Map<Instruction, Set<String>> liveOuts = new HashMap<>();

        // Initialize live-ins and live-outs with empty sets for all instructions
        for (Instruction instruction : method.getInstructions()) {
            liveIns.put(instruction, new HashSet<>());
            liveOuts.put(instruction, new HashSet<>());
        }

        // Perform the live-in/live-out analysis iteratively until convergence
        boolean changed;
        do {
            changed = false;
            for (Instruction instruction : method.getInstructions()) {
                Set<String> oldLiveIn = new HashSet<>(liveIns.get(instruction));
                Set<String> oldLiveOut = new HashSet<>(liveOuts.get(instruction));

                // Compute live-in
                Set<String> liveIn = new HashSet<>(getUses(instruction));
                liveIn.addAll(SetUtils.difference(liveOuts.get(instruction), getDefs(instruction)));

                // Compute live-out
                Set<String> liveOut = new HashSet<>();
                for (var successor : instruction.getSuccessors())
                    liveOut.addAll(liveIns.get((Instruction) successor));

                liveIns.put(instruction, liveIn);
                liveOuts.put(instruction, liveOut);

                if (!oldLiveIn.equals(liveIn) || !oldLiveOut.equals(liveOut))
                    changed = true;
            }
        } while (changed);

        // Create nodes with defs, uses, ins, and outs based on the live-ins and live-outs
        for (Instruction instruction : method.getInstructions()) {
            Node node = new Node();
            node.defs.addAll(getDefs(instruction));
            node.uses.addAll(getUses(instruction));
            node.ins.addAll(liveIns.get(instruction));
            node.outs.addAll(liveOuts.get(instruction));
            nodes.add(node);
        }

        return nodes;
    }


    private Set<String> getDefs(Instruction instruction) {
        Set<String> defs = new HashSet<>();

        if (instruction instanceof AssignInstruction assign)
            if (assign.getDest() instanceof Operand op)
                defs.add(op.getName());

        return defs;
    }

    private Set<String> getUses(Instruction instruction) {
        Set<String> uses = new HashSet<>();

        if (instruction instanceof AssignInstruction assign) {
            uses.addAll(getUses(assign.getRhs()));
        } else if (instruction instanceof  CallInstruction call) {
            if (call.getInvocationType() != CallType.invokestatic && call.getInvocationType() != CallType.NEW && call.getFirstArg() instanceof Operand op)
                uses.add(op.getName());

            if (call.getListOfOperands() != null)
                for (Element operand: call.getListOfOperands())
                    if (operand instanceof Operand op)
                        uses.add(op.getName());
        } else if (instruction instanceof  ReturnInstruction ret) {
            if (ret.getOperand() instanceof Operand op)
                uses.add(op.getName());
        } else if (instruction instanceof UnaryOpInstruction unop) {
            if (unop.getOperand() instanceof Operand op)
                uses.add(op.getName());

        } else if (instruction instanceof BinaryOpInstruction binop) {
            if (binop.getLeftOperand() instanceof Operand l_op)
                uses.add(l_op.getName());
            if (binop.getRightOperand() instanceof Operand r_op)
                uses.add(r_op.getName());
        } else if (instruction instanceof OpCondInstruction opcond) {
            for (Element el: opcond.getOperands())
                if (el instanceof Operand op)
                    uses.add(op.getName());
        } else if (instruction instanceof  PutFieldInstruction put) {
            if (put.getThirdOperand() instanceof Operand op)
                uses.add(op.getName());

            if (put.getFirstOperand() instanceof Operand op)
                uses.add(op.getName());
        } else if (instruction instanceof SingleOpInstruction sop) {
            if (sop.getSingleOperand() instanceof Operand op)
                uses.add(op.getName());
        }

        return uses;
    }




    private Map<String, Set<String>> buildInterferenceGraph(List<Node> nodes) {
        Map<String, Set<String>> graph = new HashMap<>();

        for (Node node : nodes) {
            for (var def : SetUtils.union(node.defs, node.uses)) {
                graph.computeIfAbsent(def, s -> new HashSet<>());
            }

            var pairs = SetUtils.generateCombinations(node.ins);
            pairs.addAll(SetUtils.generateCombinations(SetUtils.union(node.outs, node.defs)));

            for (var pair : pairs) {
                var neighbors = graph.get(pair.get(0));
                neighbors.add(pair.get(1));
            }
        }

        return graph;
    }


    private Map<String, Integer> colorGraph(Map<String, Set<String>> graph) {
        Map<String, Integer> colorMap = new HashMap<>();
        int numColors = graph.size();

        Deque<String> stack = new ArrayDeque<>();

        for (var node : graph.entrySet())
            if (node.getValue().size() < numColors)
                stack.push(node.getKey());

        while (!stack.isEmpty()) {
            String node = stack.pop();
            var neighbors = graph.get(node);

            boolean[] usedColors = new boolean[numColors];
            for (String neighbor : neighbors) {
                Integer neighborColor = colorMap.get(neighbor);
                if (neighborColor != null)
                    usedColors[neighborColor] = true;
            }

            for (int color = 0; color < numColors; color++) {
                if (!usedColors[color]) {
                    colorMap.put(node, color);
                    break;
                }
            }

            for (var neighbor : neighbors) {
                neighbors.remove(node);
                if (neighbors.size() < numColors)
                    stack.push(neighbor);
            }
        }

        return colorMap;
    }

    private void replaceWithRegisters(Method method, Map<String, Integer> colorMap) {
        var varTable = method.getVarTable();

        for (var key : colorMap.keySet())
            varTable.get(key).setVirtualReg(colorMap.get(key));
    }
}