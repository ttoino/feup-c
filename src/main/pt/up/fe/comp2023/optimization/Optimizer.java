package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmVisitor;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.analysis.JmmSymbolTable;

import java.util.Arrays;
import java.util.Collection;

public class Optimizer implements JmmOptimization {
    private final JmmVisitor<?, Boolean>[] visitors = new JmmVisitor[]{
            new ASTSimplificationVisitor(),
            new ConstantFoldingVisitor(),
            new ConstantPropagationVisitor()
    };

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        if (!Boolean.parseBoolean(semanticsResult.getConfig().get("optimize")))
            return semanticsResult;

        var node = semanticsResult.getRootNode();

        var cont = true;
        while (cont) {
            cont = false;
            for (var visitor : visitors)
                cont |= visitor.visit(node);
        }

        semanticsResult.getReports().add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1, "Optimized AST:\n" + node.toTree()));

        return JmmOptimization.super.optimize(semanticsResult);
    }

    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        OllirVisitor ollirVisitor = new OllirVisitor(((JmmSymbolTable) jmmSemanticsResult.getSymbolTable()));
        ollirVisitor.visit(jmmSemanticsResult.getRootNode(), 0);

        String ollirResult = ollirVisitor.getOllirCode();
        var reports = ollirVisitor.getReports();

        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1, "Generated OLLIR:\n" + ollirResult));

        return new OllirResult(jmmSemanticsResult, ollirResult, reports);
    }
}
