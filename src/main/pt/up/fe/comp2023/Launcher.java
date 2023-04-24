package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp2023.analysis.Analysis;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

import java.io.File;
import java.util.Arrays;
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
        if (reports(parserResult.getReports())) return;

        // ... add remaining stages
        Analysis analysis = new Analysis();
        JmmSemanticsResult semanticsResult = analysis.semanticAnalysis(parserResult);

        System.out.println("\n====================================== AST =====================================\n");
        System.out.println(semanticsResult.getRootNode().toTree());

        System.out.println("\n================================= SYMBOL TABLE =================================\n");
        System.out.println(semanticsResult.getSymbolTable().print());

        if (reports(semanticsResult.getReports())) return;

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

        JasminResult jasminResult = backend.toJasmin(result);

        if (reports(jasminResult.getReports())) return;

        System.out.println(jasminResult.getJasminCode());
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

    private static boolean reports(Collection<Report> reports) {
        boolean hasErrors = false;

        for (Report report : reports) {
            hasErrors |= report.getType() == ReportType.ERROR;
            var out = report.getType() == ReportType.ERROR || report.getType() == ReportType.WARNING ? System.err : System.out;
            var type = report.getType().toString().toUpperCase();
            var stage = report.getStage().toString().toUpperCase();
            var line = report.getLine() == -1 ? "" : ":" + report.getLine();
            var column = report.getColumn() == -1 ? "" : ":" + report.getColumn();

            out.println(type + "@" + stage + line + column + " " + report.getMessage());
        }

        return hasErrors;
    }

}
