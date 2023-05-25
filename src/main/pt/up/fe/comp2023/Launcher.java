package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp2023.analysis.Analyzer;
import pt.up.fe.comp2023.backend.Backend;
import pt.up.fe.comp2023.backend.JasminOptimizer;
import pt.up.fe.comp2023.optimization.Optimizer;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsSystem;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Launcher {

    public static void main(String[] args) {
        // Setups console logging and other things
        SpecsSystem.programStandardInit();

        // Parse arguments as a map with predefined options
        var config = parseArgs(args);

        // Get input file
        File inputFile = new File(config.get("inputFile"));

        // Check if file exists
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + inputFile + "'.");
        }

        // Read contents of input file
        String code = SpecsIo.read(inputFile);

        // Instantiate JmmParser
        SimpleParser parser = new SimpleParser();

        // Parse stage
        JmmParserResult parserResult = parser.parse(code, config);

        // Check if there are parsing errors
        if (reports(config, parserResult.getReports())) return;

        // ... add remaining stages
        Analyzer analyzer = new Analyzer();
        JmmSemanticsResult semanticsResult = analyzer.semanticAnalysis(parserResult);

        if (reports(config, semanticsResult.getReports())) return;

        Optimizer optimizer = new Optimizer();

        semanticsResult = optimizer.optimize(semanticsResult);
        OllirResult ollirResult = optimizer.toOllir(semanticsResult);
        ollirResult = optimizer.optimize(ollirResult);

        if (reports(config, ollirResult.getReports())) return;

        Backend backend = new Backend();
        JasminResult unoptimizedJasminResult = backend.toJasmin(ollirResult);

        if (reports(config, unoptimizedJasminResult.getReports()) || code == null) return;

        JasminOptimizer jasminOptimizer = new JasminOptimizer();

        JasminResult jasminResult = jasminOptimizer.optimize(unoptimizedJasminResult);

        jasminResult.run();
    }

    private static Map<String, String> parseArgs(String[] args) {
        // Default config
        Map<String, String> config = new HashMap<>();
        config.put("optimize", "false");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");

        for (var arg : args) {
            if (arg.equals("-o") || arg.equals("--optimize"))
                config.put("optimize", "true");
            else if (arg.startsWith("-r=") || arg.startsWith("--registers="))
                config.put("registerAllocation", arg.split("=")[1]);
            else if (arg.equals("-d") || arg.equals("--debug"))
                config.put("debug", "true");
            else if (arg.startsWith("-i=") || arg.startsWith("--input="))
                config.put("inputFile", arg.split("=")[1]);
            else
                System.err.println("Unknown argument '" + arg + "'.");
        }

        if (!config.containsKey("inputFile")) {
            System.err.println("Missing input file.");
            System.exit(1);
        }

        return config;
    }

    private static boolean reports(Map<String, String> config, Collection<Report> reports) {
        boolean hasErrors = false;
        boolean debug = Boolean.parseBoolean(config.get("debug"));

        for (Report report : reports) {
            hasErrors |= report.getType() == ReportType.ERROR;
            var out = report.getType() == ReportType.ERROR || report.getType() == ReportType.WARNING ? System.err : System.out;
            var type = report.getType().toString().toUpperCase();
            var stage = report.getStage().toString().toUpperCase();
            var line = report.getLine() == -1 ? "" : ":" + report.getLine();
            var column = report.getColumn() == -1 ? "" : ":" + report.getColumn();

            if (debug || report.getType() != ReportType.DEBUG)
                out.println(type + "@" + stage + line + column + " " + report.getMessage());
        }

        System.out.flush();
        System.err.flush();

        return hasErrors;
    }

}
