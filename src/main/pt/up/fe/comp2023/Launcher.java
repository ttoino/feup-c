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
                Simple {
                	.construct Simplez().V {
                		invokespecial(this, "<init>").V;
                	}
                	
                	.method public sum(A.array.i32, B.array.i32).array.i32 {
                		t1.i32 :=.i32 arraylength($1.A.array.i32).i32;
                		C.array.i32 :=.array.i32 new(array, t1.i32).array.i32;
                		i.i32 :=.i32 0.i32;
                		
                		Loop:
                			t1.i32 :=.i32 arraylength($1.A.array.i32).i32;
                			if (i.i32 >=.bool t1.i32) goto End;
                			
                			t2.i32 :=.i32 $1.A[i.i32].i32;
                			t3.i32 :=.i32 $2.B[i.i32].i32;
                			t4.i32 :=.i32 t2.i32 +.i32 t3.i32;
                			C[i.i32].i32 :=.i32 t4.i32;
                			i.i32 :=.i32 i.i32 +.i32 1.i32;
                			goto Loop;
                		End:
                			ret.array.i32 C.array.i32;
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
