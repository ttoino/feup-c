package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;

public class Analyzer implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {
        var table = new JmmSymbolTable(jmmParserResult.getRootNode());
        List<Report> reports = new ArrayList<>();

        reports.add(new Report(ReportType.DEBUG, Stage.SEMANTIC, -1, -1, "Generated AST:\n" + jmmParserResult.getRootNode().toTree()));

        var main = table.getMethod("main");
        if (main != null) {
            if (!main.getReturnType().print().equals("void"))
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "The main method must return void"));
            if (main.getParameters().size() != 1 || !main.getParameters().get(0).getType().print().equals("String[]"))
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "The main method must take an argument of type 'String[]'"));
            if (!main.getModifiers().contains("public") || !main.getModifiers().contains("static"))
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "The main method must be public and static"));
        }

        new SemanticAnalysisVisitor(table, reports).visit(jmmParserResult.getRootNode());

        reports.add(new Report(ReportType.DEBUG, Stage.SEMANTIC, -1, -1, "Annotated AST:\n" + jmmParserResult.getRootNode().toTree()));
        reports.add(new Report(ReportType.DEBUG, Stage.SEMANTIC, -1, -1, "Generated symbol table:\n" + table.print()));

        return new JmmSemanticsResult(
            jmmParserResult.getRootNode(),
            table,
            reports,
            jmmParserResult.getConfig()
        );
    }

}
