package pt.up.fe.comp2023;

import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.List;
import java.util.Optional;

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

        if (className == null) return "";

        if (fileName != null) {
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
        }

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

        for (Field field : ollirClass.getFields())
            sb.append(this.buildJasminField(field, reports)).append('\n');

        for (Method method : ollirClass.getMethods())
            sb.append(this.buildJasminMethod(method, reports)).append('\n');

        return sb.toString();
    }

    private String buildJasminMethod(Method method, List<Report> reports) {

        // FIXME: should this be placed in a different function ?
        if (method.isConstructMethod()) {
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
                        "Cannot generate final constructor",
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

        for (var param : method.getParams())
            sb.append(this.buildJasminType(param.getType(), reports));

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

        for (Instruction instruction : method.getInstructions())
            sb.append(this.buildJasminInstruction(instruction, reports)).append('\n');

        sb.append("\treturn");
        if (!method.isConstructMethod() && !isVoid) {
            // TODO: handle return value

        }
        sb.append('\n');

        sb.append(".end method\n");

        return sb.toString();
    }

    private String buildJasminField(Field field, List<Report> reports) {

        StringBuilder sb = new StringBuilder();

        sb.append(".field ");

        var accessModifier = field.getFieldAccessModifier();
        sb.append(
                accessModifier == AccessModifiers.DEFAULT
                        ? ""
                        : accessModifier.name().toLowerCase().concat(" "));

        if (field.isFinalField())
            sb.append("static ");

        if (field.isFinalField())
            sb.append("final ");

        sb.append(field.getFieldName()).append(' ');

        sb.append(this.buildJasminType(field.getFieldType(), reports));

        if (field.isInitialized())
            sb.append(" = ").append(field.getInitialValue());

        return sb.toString();
    }

    private String buildJasminInstruction(Instruction instruction, List<Report> reports) {
        // TODO:
        return "";
    }

    private String buildJasminType(Type type, List<Report> reports) {
        var typeDescriptor = "";

        switch (type.getTypeOfElement()) {
            case ARRAYREF:
                typeDescriptor = this.buildJasminArrayType((ArrayType) type, reports);
                break;
            case OBJECTREF:
            case CLASS:
                // what's the difference?

                typeDescriptor = this.buildJasminClassType((ClassType) type, reports);
                break;
            case INT32:
                typeDescriptor = "I";
                break;
            case BOOLEAN:
                typeDescriptor = "Z";
                break;
            case THIS:
                // ??
                typeDescriptor = "this";
                break;
            case STRING:
                typeDescriptor = "Ljava/lang/String;";
                break;
            case VOID:
                typeDescriptor = "V";
                break;
        }

        return typeDescriptor;
    }

    private String buildJasminArrayType(ArrayType type, List<Report> reports) {
        return "[".repeat(Math.max(0, type.getNumDimensions())) +
                this.buildJasminType(type.getElementType(), reports);
    }

    private String buildJasminClassType(ClassType type, List<Report> reports) {
        return "L" + type.getName() + ";";
    }
}
