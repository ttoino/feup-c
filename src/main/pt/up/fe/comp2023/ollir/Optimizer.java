package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.analysis.JmmSymbolTable;

import java.util.Collections;

public class Optimizer implements JmmOptimization {
    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        OllirBuilder ollirBuilder = new OllirBuilder(((JmmSymbolTable) jmmSemanticsResult.getSymbolTable()));
        ollirBuilder.visit(jmmSemanticsResult.getRootNode(), 0);

        String ollirResult = ollirBuilder.getOllirCode();
        var reports = ollirBuilder.getReports();

        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1, "Generated OLLIR:\n" + ollirResult));

        return new OllirResult(jmmSemanticsResult, ollirResult, reports);
    }
}
