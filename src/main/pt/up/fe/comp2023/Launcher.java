package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

import java.io.File;
import java.util.Arrays;
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
        for (Report report : parserResult.getReports())
            System.err.println(report.getLine() + ":" + report.getColumn() + " " + report.getMessage());

        if (!parserResult.getReports().isEmpty()) return;

        // ... add remaining stages
        Analysis analysis = new Analysis();
        JmmSemanticsResult semanticsResult = analysis.semanticAnalysis(parserResult);

        System.out.println("\n====================================== AST =====================================\n");
        System.out.println(semanticsResult.getRootNode().toTree());

        // Check if there are semantic errors
        for (Report report : semanticsResult.getReports())
            System.err.println(report.getLine() + ":" + report.getColumn() + " " + report.getMessage());

        System.out.println("\n================================= SYMBOL TABLE =================================\n");
        System.out.println(semanticsResult.getSymbolTable().print());

        if (!semanticsResult.getReports().isEmpty()) return;

        Backend backend = new Backend();

        OllirResult result = new OllirResult("""
                import io;
                Simple {
                .construct Simple().V {
                invokespecial(this, "<init>").V;
                }
                                
                .method public add(a.i32, b.i32).i32 {
                temp_0.i32 :=.i32 invokevirtual(this, "constInstr").i32;
                c.i32 :=.i32 $1.a.i32 +.i32 temp_0.i32;
                ret.i32 c.i32;
                }
                                
                .method public static main(args.array.String).V {
                a.i32 :=.i32 20.i32;
                b.i32 :=.i32 10.i32;
                temp_2.Simple :=.Simple new(Simple).Simple;
                invokespecial(temp_2.Simple,"<init>").V;
                s.Simple :=.Simple temp_2.Simple;
                temp_3.i32 :=.i32 invokevirtual(s.Simple, "add", a.i32, b.i32).i32;
                c.i32 :=.i32 temp_3.i32;
                invokestatic(io, "println", c.i32).V;
                ret.V;
                }
                                
                .method public constInstr().i32 {
                c.i32 :=.i32 0.i32;
                c.i32 :=.i32 4.i32;
                c.i32 :=.i32 8.i32;
                c.i32 :=.i32 14.i32;
                c.i32 :=.i32 250.i32;
                c.i32 :=.i32 400.i32;
                c.i32 :=.i32 1000.i32;
                c.i32 :=.i32 100474650.i32;
                c.i32 :=.i32 10.i32;
                ret.i32 c.i32;
                }
                                
                }
                """, config);

        var generatedCode = backend.toJasmin(result);

        for (Report report : generatedCode.getReports())
            System.err.println(report.getMessage());

        if (!generatedCode.getReports().isEmpty()) return;

        System.out.println(generatedCode.getJasminCode());
    }

    private static Map<String, String> parseArgs(String[] args) {
        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // Check if there is at least one argument
        if (args.length != 1) {
            throw new RuntimeException("Expected a single argument, a path to an existing input file.");
        }

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        config.put("optimize", "false");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");

        return config;
    }

}
