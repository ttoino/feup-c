package pt.up.fe.comp2023.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmVisitor;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.analysis.JmmSymbolTable;

import java.util.Collections;
import java.util.regex.Pattern;

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

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        var code = ollirResult.getOllirCode();
        var newCode = new StringBuilder();

        var ifPattern = Pattern.compile("(\\w+\\.bool)\\s*:=\\.bool\\s*(\\w+\\.\\w+\\s*(<|>|==|!=|<=|>=)\\.bool\\s*\\w+\\.\\w+);\\n\\s*if\\s*\\(!\\.bool\\s*\\1\\)");
        var ifMatcher = ifPattern.matcher(code);

        while (ifMatcher.find()) {
            var condition = ifMatcher.group(2);
            var op = ifMatcher.group(3);

            condition = switch (op) {
                case "<" -> condition.replace("<", ">=");
                case ">" -> condition.replace(">", "<=");
                case "==" -> condition.replace("==", "!=");
                case "!=" -> condition.replace("!=", "==");
                case "<=" -> condition.replace("<=", ">");
                case ">=" -> condition.replace(">=", "<");
                default -> condition;
            };

            ifMatcher.appendReplacement(newCode, "if (" + condition + ")");
        }
        ifMatcher.appendTail(newCode);

        ollirResult.getReports().add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1, "Optimized OLLIR:\n" + newCode));

        return new OllirResult(new JmmSemanticsResult(null, ollirResult.getSymbolTable(), Collections.emptyList(), ollirResult.getConfig()), newCode.toString(), ollirResult.getReports());
    }
}
