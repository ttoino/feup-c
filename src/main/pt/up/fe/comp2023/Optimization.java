package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Optimization implements JmmOptimization {
    private JmmSymbolTable table;

    private final List<Report> reports = new ArrayList<>();
    private final StringBuilder code = new StringBuilder();

    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        table = ((JmmSymbolTable) jmmSemanticsResult.getSymbolTable());

        new OllirEmitterVisitor().visit(jmmSemanticsResult.getRootNode());

        return new OllirResult(jmmSemanticsResult, code.toString(), reports);
    }

    private void emit(String ...code) {
        Arrays.stream(code).forEach(this.code::append);
    }

    private void emitIndentation(int indentation) {
        emit(" ".repeat(indentation));
    }

    private void emitLine(int indentation, String ...code) {
        emitIndentation(indentation);
        emit(code);
        emit("\n");
    }

    private String toOllirType(Type type) {
        return (type.isArray() ? "array." : "") + switch (type.getName()) {
            case "void" -> "V";
            case "byte" -> "i8";
            case "short" -> "i16";
            case "int" -> "i32";
            case "long" -> "i64";
            default -> type.getName();
        };
    }

    private String toOllirSymbol(Symbol symbol) {
        return symbol.getName() + "." + toOllirType(symbol.getType());
    }

    private class OllirEmitterVisitor extends AJmmVisitor<Integer, Integer> {
        @Override
        protected void buildVisitor() {
            setDefaultVisit(this::visitChildren);
            addVisit("ClassDeclaration", this::visitClassDeclaration);
            addVisit("MethodDeclaration", this::visitMethodDeclaration);
        }

        @Override
        public Integer visit(JmmNode jmmNode) {
            return visit(jmmNode, 0);
        }

        protected Integer visitChildren(JmmNode node, Integer indentation) {
            for (var child: node.getChildren())
                visit(child, indentation);

            return indentation;
        }

        protected Integer visitClassDeclaration(JmmNode node, Integer indentation) {
            emitIndentation(indentation);
            emit(table.getClassName());

            if (table.getSuper() != null)
                emit(" extends ", table.getSuper());

            emit(" {\n");

            visitChildren(node, indentation + 4);

            emitLine(indentation, "}");

            return indentation;
        }

        protected Integer visitMethodDeclaration(JmmNode node, Integer indentation) {
            var method = table.getMethod(node.get("methodName"));
            emitLine(indentation,
                    ".method ",
                    String.join(" ", method.getModifiers()), " ",
                    method.getName(),
                    "(", method.getParameters().stream().map(Optimization.this::toOllirSymbol).collect(Collectors.joining(", ")), ").",
                    toOllirType(method.getReturnType()), " {");

            visitChildren(node, indentation + 4);

            emitLine(indentation, "}");

            return indentation;
        }
    }
}
