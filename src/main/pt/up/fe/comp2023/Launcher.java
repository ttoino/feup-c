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

        if (!parserResult.getReports().isEmpty())
            return;

        // ... add remaining stages
        Analysis analysis = new Analysis();
        JmmSemanticsResult semanticsResult = analysis.semanticAnalysis(parserResult);

        System.out.println("\n====================================== AST =====================================\n");
        System.out.println(semanticsResult.getRootNode().toTree());

        // Check if there are semantic errors
        for (Report report : semanticsResult.getReports())
            System.err.println(report.getLine() + ":" + report.getColumn() + " " + report.getMessage());

        if (!semanticsResult.getReports().isEmpty())
            return;

        System.out.println("\n================================= SYMBOL TABLE =================================\n");
        System.out.println(semanticsResult.getSymbolTable().print());

        Backend backend = new Backend();

        OllirResult result = new OllirResult(
                """
                        myClass {
                        \t.field private a.i32;
                        \t
                        \t.construct myClass(n.i32).V {
                        \t\tinvokespecial(this, "<init>").V;
                        \t\tputfield(this, a.i32, $1.n.i32).V;
                        \t}
                        \t
                        \t.construct myClass().V {
                        \t\tinvokespecial(this, "<init>").V;
                        \t}
                        \t
                        \t.method public get().i32 {\s
                        \t\tt1.i32 :=.i32 getfield(this, a.i32).i32;
                        \t\tret.i32 t1.i32;
                        \t}
                        \t
                        \t.method public put(n.i32).V {
                        \t\tputfield(this, a.i32, $1.n.i32).V;
                        \t}
                        \t
                        \t.method public m1().V {
                        \t\tputfield(this, a.i32, 2.i32).V;  // this.a = 2;
                        \t\t
                        \t\tt2.String :=.String ldc("val = ").String;
                        \t\tt1.i32 :=.i32 invokevirtual(this,"get").i32;
                        \t\tinvokestatic(io, "println", t2.String, t1.i32).V;  //io.println("val = ", this.get());
                        \t\t
                        \t\tc1.myClass :=.myClass new(myClass,3.i32).myClass;
                        \t\tinvokespecial(c1.myClass,"<init>").V;  // myClass c1 = new myClass(3);
                        \t\t
                        \t\tt3.i32 :=.i32 invokevirtual(c1.myClass, "get").i32;
                        \t\tinvokestatic(io, "println", t2.String, t3.i32).V; // io.println("val = ", c1.get());
                        \t\t
                        \t\tinvokevirtual(c1.myClass, "put", 2.i32).V;  // c1.put(2);
                        \t\t
                        \t\tt4.i32 :=.i32 invokevirtual(c1.myClass, "get").i32;
                        \t\tinvokestatic(io, "println", t2.String, t4.i32).V; //io.println("val = ", c1.get());
                        \t}
                        \t
                        \t.method public static main(args.array.myClass).V {
                        \t\tA.myClass :=.myClass new(myClass).myClass;
                        \t\tinvokespecial(A.myClass,"<init>").V;
                        \t\tinvokevirtual(A.myClass,"m1").V;
                        \t}
                        }""", config);

        var generatedCode = backend.toJasmin(result);

        for (Report report : generatedCode.getReports())
            System.err.println(report.getMessage());

        if (!generatedCode.getReports().isEmpty())
            return;

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
