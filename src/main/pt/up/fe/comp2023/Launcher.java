package pt.up.fe.comp2023;

import pt.limwa.jmm.protocol.JmmProtocolAdapter;
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
        JmmProtocolAdapter.start(adapter -> {
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

            SimpleParser parser = new SimpleParser();
            final JmmParserResult parserResult = parser.parse(code, config);
            adapter.createSection("Parsing", () -> reports(config, parserResult.getReports()));

            Analyzer analyzer = new Analyzer();
            final JmmSemanticsResult semanticsResult = analyzer.semanticAnalysis(parserResult);
            adapter.createSection("Semantic Analysis", () -> reports(config, semanticsResult.getReports()));

            Optimizer optimizer = new Optimizer();

            final JmmSemanticsResult optimizedSemanticsResult = optimizer.optimize(semanticsResult);
            adapter.createSection("Optimized AST", () -> reports(config, optimizedSemanticsResult.getReports()));

            final OllirResult ollirResult = optimizer.toOllir(optimizedSemanticsResult);
            adapter.createSection("OLLIR", () -> reports(config, ollirResult.getReports()));

            final OllirResult optimizedOllirResult = optimizer.optimize(ollirResult);
            adapter.createSection("Optimized OLLIR", () -> reports(config, optimizedOllirResult.getReports()));

            Backend backend = new Backend();
            final JasminResult jasminResult = backend.toJasmin(optimizedOllirResult);
            adapter.createSection("Jasmin", () -> reports(config, jasminResult.getReports()));

            JasminOptimizer jasminOptimizer = new JasminOptimizer();
            final JasminResult optimizedJasminResult = jasminOptimizer.optimize(jasminResult);
            adapter.createSection("Optimized Jasmin", () -> reports(config, optimizedJasminResult.getReports()));

            adapter.createSection("Execution", () -> optimizedJasminResult.run());
        });
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

    private static void reports(Map<String, String> config, Collection<Report> reports) throws RuntimeException {
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

        if (hasErrors) throw new RuntimeException("Found errors");
    }

}
