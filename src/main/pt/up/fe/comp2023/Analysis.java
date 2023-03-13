package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;

public class Analysis implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {
        return new JmmSemanticsResult(
                jmmParserResult.getRootNode(),
                new JmmSymbolTable(jmmParserResult.getRootNode()),
                jmmParserResult.getReports(),
                jmmParserResult.getConfig());
    }
}
