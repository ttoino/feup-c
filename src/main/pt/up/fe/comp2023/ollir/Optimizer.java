package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;

public class Optimizer extends JmmOptimization {
    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        System.out.println(jmmSemanticsResult.getRootNode().toTree());
        OllirBuilder ollirBuilder = new OllirBuilder(jmmSemanticsResult.getSymbolTable());
        ollirBuilder.visit(jmmSemanticsResult.getRootNode());

        String ollirResult = ollirBuilder.getOllirCode();

        return new OllirResult(jmmSemanticsResult, ollirResult, Collections.emptyList());
    }
}
