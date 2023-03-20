package pt.up.fe.comp2023;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public class Backend implements JasminBackend {

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {

        var config = ollirResult.getConfig();
        var reports = ollirResult.getReports();

        var ollirClass = ollirResult.getOllirClass();

        var jasminCode = this.buildJasminCode(ollirClass, reports, config.get("inputFile"));

        return new JasminResult(ollirClass.getClassName(), jasminCode, reports, config);
    }

    private String buildJasminCode(ClassUnit ollirClass, List<Report> reports, String fileName) {

        int initialNumberOfReports = reports.size();
        var jasminCode = this.buildJasminClass(ollirClass, reports, fileName);

        if (reports.size() != initialNumberOfReports) {
            return null;
        }

        return "; class " +
                ollirClass.getClassName() +
                ", transpiled to jasmin\n" +
                jasminCode;
    }

    private String buildJasminClass(ClassUnit ollirClass, List<Report> reports, String fileName) {

        StringBuilder sb = new StringBuilder();

        var className = ollirClass.getClassName();

        if (!fileName.equals(className.concat(".jmm"))) {
            reports.add(Report.newError(
                    Stage.GENERATION,
                    -1,
                    -1,
                    "Top level classes should have the same name as the file they are defined in",
                    new Exception("Top level classes should have the same name as the file they are defined in")));
            return "";
        }

        sb.append(".source ").append(fileName).append('\n');
        sb.append(".class ");

        var classAccessModifier = ollirClass.getClassAccessModifier();
        String modifier = classAccessModifier.name().toLowerCase();
        if (classAccessModifier == AccessModifiers.DEFAULT)
            modifier = "";
        sb.append(modifier);

        // TODO: package

        sb
            .append(' ')
            .append(className)
            .append('\n');

        var superName = Optional.ofNullable(ollirClass.getSuperClass()).orElse("java.lang.Object");
        sb
            .append(".super ")
            .append(superName.replaceAll("\\.", "/"))
            .append("\n\n");

        for (Method method : ollirClass.getMethods())
            sb.append(this.buildJasminMethod(method, reports)).append('\n');

        return sb.toString();
    }

    private String buildJasminMethod(Method method, List<Report> reports) {

        // FIXME: should this be placed in a different function ?
        if(method.isConstructMethod()) {
            if (method.isStaticMethod()) { // constructor cannot be static
                reports.add(Report.newError(
                    Stage.GENERATION,
                    -1,
                    -1,
                    "Cannot generate static constructor",
                    new Exception("Cannot generate static constructor")));
                return "";
            } else if (method.isFinalMethod()) {
                reports.add(Report.newError(
                    Stage.GENERATION,
                    -1,
                    -1,
                    "Cannot generate static constructor",
                    new Exception("Cannot generate final constructor")));
                return "";
            }
        }

        StringBuilder sb = new StringBuilder(".method ");

        var accessModifier = method.getMethodAccessModifier();
        sb.append(
            accessModifier == AccessModifiers.DEFAULT
                ? ""
                : accessModifier.name().toLowerCase().concat(" "));

        if (method.isStaticMethod())
            sb.append("static ");

        if (method.isFinalMethod())
            sb.append("final ");

        if (method.isConstructMethod())
            sb.append("<init>");
        else
            sb.append(method.getMethodName());

        sb.append('(');
        // TODO: handle params
        sb.append(")");

        var methodReturnType = method.getReturnType();
        var isVoid = methodReturnType.getTypeOfElement() == ElementType.VOID;

        // even though constructors return void, short-circuit the check
        if (method.isConstructMethod() || isVoid)
            sb.append('V');
        else
            sb.append(this.buildJasminType(methodReturnType, reports));
        sb.append('\n');

        // TODO: handle tabs
        // add function code here

        sb.append("\treturn");
        if (!method.isConstructMethod() && !isVoid) {
            // TODO: handle return value
        }
        sb.append('\n');

        sb.append(".end method\n");

        return sb.toString();
    }

    private String buildJasminType(Type type, List<Report> reports) {
        var typeDescriptor = "";
        switch (type.getTypeOfElement()) {
            case INT32: typeDescriptor = "I"; break;
            case BOOLEAN: typeDescriptor = "Z"; break;
            case ARRAYREF: typeDescriptor = "L"; break;
            case OBJECTREF: typeDescriptor = "L"; break;
            case CLASS: typeDescriptor = "L"; break;
            case THIS: typeDescriptor = "null"; break;
            case STRING: typeDescriptor = "Ljava/lang/String;"; break;
            case VOID: typeDescriptor = "V"; break;
        };

        return typeDescriptor;
    }
}
