package pt.up.fe.comp2023;

import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Backend implements JasminBackend {

    private String superClassName;

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

        return "; class " + ollirClass.getClassName() + ", transpiled to jasmin\n" + jasminCode;
    }

    private String buildJasminClass(ClassUnit ollirClass, List<Report> reports, String fileName) {

        StringBuilder sb = new StringBuilder();

        var className = ollirClass.getClassName();

        if (className == null) return "";

        if (fileName != null) {
            if (!fileName.equals(className.concat(".jmm"))) {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Top level classes should have the same name as the file they are defined in: expected '" + fileName + "', got '" + className + ".jmm'", new Exception("Top level classes should have the same name as the file they are defined in")));
            }

            sb.append(".source ").append(fileName).append('\n');
        }

        sb.append(".class ");

        var classAccessModifier = ollirClass.getClassAccessModifier();
        String modifier = classAccessModifier.name().toLowerCase();
        if (classAccessModifier == AccessModifiers.DEFAULT) modifier = "public"; // TODO: this is made in order
        sb.append(modifier);
        sb.append(' ').append(className).append('\n');

        var superName = Optional.ofNullable(ollirClass.getSuperClass()).orElse("java.lang.Object").replaceAll("\\.", "/");
        sb.append(".super ").append(superName).append("\n");
        this.superClassName = superName;

        sb.append('\n');

        for (Field field : ollirClass.getFields())
            sb.append(this.buildJasminClassField(field, reports)).append('\n');

        sb.append('\n');

        boolean hasConstructor = false;
        for (Method method : ollirClass.getMethods()) {
            if (method.isConstructMethod()) hasConstructor = true;
            sb.append(this.buildJasminMethod(method, reports)).append('\n');
        }

        if (!hasConstructor) {
            // TODO:
        }

        return sb.toString();
    }

    private String buildJasminClassField(Field field, List<Report> reports) {

        StringBuilder sb = new StringBuilder();

        sb.append(".field ");

        var accessModifier = field.getFieldAccessModifier();
        sb.append(accessModifier == AccessModifiers.DEFAULT ? "" : accessModifier.name().toLowerCase().concat(" "));

        if (field.isFinalField()) sb.append("static ");

        if (field.isFinalField()) sb.append("final ");

        sb.append(field.getFieldName()).append(' ');

        sb.append(this.buildJasminTypeDescriptor(field.getFieldType(), reports));

        if (field.isInitialized()) {

            // TODO: needs better handling

            sb.append(" = ").append(field.getInitialValue());
        }

        return sb.toString();
    }

    private String buildJasminMethod(Method method, List<Report> reports) {

        // FIXME: should this be placed in a different function ?
        if (method.isConstructMethod()) {
            if (method.isStaticMethod()) { // constructor cannot be static
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot generate static constructor(" + method.getMethodName() + ")", new Exception("Cannot generate static constructor")));
                return "";
            } else if (method.isFinalMethod()) {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot generate final constructor(" + method.getMethodName() + ")", new Exception("Cannot generate final constructor")));
                return "";
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append(".method ");

        sb.append(this.buildJasminMethodHeader(method, reports));

        if (!method.isConstructMethod()) {
            // TODO: remove when optimizing
            sb.append("\t.limit locals 99").append('\n');
            sb.append("\t.limit stack 99").append('\n');
        }

        sb.append(this.buildJasminMethodBody(method, reports));

        sb.append(".end method\n");

        return sb.toString();
    }

    private String buildJasminMethodHeader(Method method, List<Report> reports) {
        var sb = new StringBuilder();

        var accessModifier = method.getMethodAccessModifier();
        sb.append(accessModifier == AccessModifiers.DEFAULT ? "" : accessModifier.name().toLowerCase().concat(" "));

        if (method.isStaticMethod()) sb.append("static ");

        if (method.isFinalMethod()) sb.append("final ");

        if (method.isConstructMethod()) sb.append("<init>");
        else sb.append(method.getMethodName());

        sb.append('(');

        for (var param : method.getParams())
            sb.append(this.buildJasminTypeDescriptor(param.getType(), reports));

        sb.append(")");

        var methodReturnType = method.getReturnType();
        var isVoid = methodReturnType.getTypeOfElement() == ElementType.VOID;

        // even though constructors return void, short-circuit the check
        if (method.isConstructMethod() || isVoid) sb.append('V');
        else sb.append(this.buildJasminTypeDescriptor(methodReturnType, reports));
        sb.append('\n');

        return sb.toString();
    }

    private String buildJasminMethodBody(Method method, List<Report> reports) {

        var sb = new StringBuilder();

        boolean hasReturn = false;
        var varTable = method.getVarTable();

        for (Instruction instruction : method.getInstructions()) {

            if (instruction.getInstType() == InstructionType.RETURN) hasReturn = true;

            var labels = method.getLabels(instruction);

            sb.append(this.buildJasminInstruction(instruction, varTable, labels, reports)).append('\n');

            if (instruction.getInstType() == InstructionType.CALL
                    && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID) {

                sb.append("\tpop\n");
            }
        }
        if (!hasReturn) { // default to have a return
            if (!(method.isConstructMethod() || method.getReturnType().getTypeOfElement() == ElementType.VOID)) {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Non-void function must have a return type", new Exception("Non-void function must have a return type")));
                return "";
            }

            var instruction = new ReturnInstruction();
            instruction.setReturnType(new Type(ElementType.VOID));

            sb.append(this.buildJasminInstruction(instruction, varTable, List.of(), reports)).append('\n');
        }

        return sb.toString();
    }

    private String buildJasminInstruction(Instruction instruction, HashMap<String, Descriptor> varTable, List<String> labels, List<Report> reports) {

        switch (instruction.getInstType()) {
            case ASSIGN:
                return this.buildJasminAssignInstruction((AssignInstruction) instruction, varTable, labels, reports);
            case CALL:
                return this.buildJasminCallInstruction((CallInstruction) instruction, varTable, reports);
            case GOTO:
                break;
            case BRANCH:
                break;
            case RETURN:
                return this.buildJasminReturnInstruction((ReturnInstruction) instruction, varTable, reports);
            case PUTFIELD:
                return this.buildJasminPutfieldOperation((PutFieldInstruction) instruction, varTable, reports);
            case GETFIELD:
                return this.buildJasminGetfieldOperation((GetFieldInstruction) instruction, varTable, reports);
            case UNARYOPER:
                return this.buildJasminUnaryOperatorInstruction((UnaryOpInstruction) instruction, varTable, reports);
            case BINARYOPER:
                return this.buildJasminBinaryOperatorInstruction((BinaryOpInstruction) instruction, varTable, reports);
            case NOPER:
                return this.buildJasminSingleOpInstruction((SingleOpInstruction) instruction, varTable, reports);
        }

        return "\tno-op";
    }

    private String buildJasminAssignInstruction(AssignInstruction instruction, HashMap<String, Descriptor> varTable, List<String> labels, List<Report> reports) {

        var sb = new StringBuilder();

        sb.append(this.buildJasminInstruction(instruction.getRhs(), varTable, labels, reports)).append('\n');

        Operand op = (Operand) instruction.getDest();

        var descriptor = varTable.get(op.getName());
        int regNum = descriptor.getVirtualReg();

        sb.append('\t');
        switch (instruction.getTypeOfAssign().getTypeOfElement()) {

            case INT32:
            case BOOLEAN:
                sb.append('i');
                break;
            case ARRAYREF:
            case OBJECTREF:
            case STRING:
                sb.append('a');
                break;
            case CLASS:
                // TODO: ?
                break;
            case THIS:
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot assign to 'this' reference", new Exception("Cannot assign to 'this' reference")));
                break;
            case VOID:
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot assign to void variable", new Exception("Cannot assign to void variable")));
                break;
        }
        sb.append("store");

        sb.append(regNum < 4 ? '_' : ' ').append(regNum);

        return sb.toString();
    }

    private String buildJasminCallInstruction(CallInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        switch (instruction.getInvocationType()) {
            case invokevirtual -> {// method call on object reference
                Operand calledObject = (Operand) instruction.getFirstArg();
                LiteralElement methodName = (LiteralElement) instruction.getSecondArg();

                var descriptor = varTable.get(calledObject.getName());

                int regNum = descriptor.getVirtualReg();

                // load the object reference onto the stack
                sb.append('\t').append('a').append("load").append(regNum < 4 ? '_' : ' ').append(regNum).append('\n');

                // load args
                instruction.getListOfOperands().forEach((arg) -> {

                    sb.append('\t');

                    Operand op = (Operand) arg;

                    var argDescriptor = varTable.get(op.getName());
                    int argRegNum = argDescriptor.getVirtualReg();

                    switch (op.getType().getTypeOfElement()) {
                        case INT32:
                        case BOOLEAN:
                            sb.append('i');
                            break;
                        case ARRAYREF:
                        case OBJECTREF:
                        case STRING:
                            sb.append('a');
                            break;
                        case THIS:
                            // TODO: Error?
                            break;
                        case CLASS:
                            // TODO: ?
                            break;
                        case VOID:
                            reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
                            break;
                    }

                    sb.append("load");

                    sb.append(argRegNum < 4 ? '_' : ' ').append(argRegNum).append('\n');
                });

                sb.append('\t').append("invokevirtual ").append(((ClassType) calledObject.getType()).getName()).append('.').append(methodName.getLiteral().replaceAll("\"", "")).append('(');

                instruction.getListOfOperands().forEach((op) -> {
                    var opType = this.buildJasminTypeDescriptor(op.getType(), reports);
                    sb.append(opType);
                });

                sb.append(')').append(this.buildJasminTypeDescriptor(instruction.getReturnType(), reports));

            }
            case invokeinterface -> {
            }
            case invokespecial -> {
                Operand calledObject = (Operand) instruction.getFirstArg();
                LiteralElement methodName = (LiteralElement) instruction.getSecondArg();

                var descriptor = varTable.get(calledObject.getName());

                int regNum = descriptor.getVirtualReg();

                sb.append('\t').append('a').append("load").append(regNum < 4 ? '_' : ' ').append(regNum).append('\n');
                sb.append('\t').append("invokespecial ");

                // TODO: perhaps RTE
                String objectName = ((ClassType) calledObject.getType()).getName();
                if (Objects.equals(calledObject.getName(), ElementType.THIS.toString().toLowerCase())) {
                    objectName = this.superClassName;
                }

                sb.append(objectName).append('.').append(methodName.getLiteral().replaceAll("\"", "")).append('(');

                instruction.getListOfOperands().forEach((op) -> {
                    var opType = this.buildJasminTypeDescriptor(op.getType(), reports);
                    sb.append(opType);
                });

                sb.append(')').append(this.buildJasminTypeDescriptor(instruction.getReturnType(), reports));

            }
            case invokestatic -> {
                Operand calledObject = (Operand) instruction.getFirstArg();
                LiteralElement methodName = (LiteralElement) instruction.getSecondArg();

                // load args
                instruction.getListOfOperands().forEach((arg) -> {

                    sb.append('\t');

                    if (arg.isLiteral()) {

                        LiteralElement literal = (LiteralElement) arg;

                        sb.append('\t');
                        switch (instruction.getReturnType().getTypeOfElement()) {
                            case INT32 -> {
                                var value = literal.getLiteral();

                                sb.append(this.buildJasminIntegerPushInstruction(Integer.parseInt(value)));
                            }
                            case BOOLEAN -> sb.append("bipush ").append(literal.getLiteral());
                            case ARRAYREF, OBJECTREF, STRING, THIS, CLASS, VOID ->
                                    reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
                        }

                    } else {

                        Operand op = (Operand) arg;

                        var argDescriptor = varTable.get(op.getName());
                        int argRegNum = argDescriptor.getVirtualReg();

                        switch (op.getType().getTypeOfElement()) {
                            case INT32, BOOLEAN -> sb.append('i');
                            case ARRAYREF, OBJECTREF, STRING, THIS, CLASS -> sb.append('a');
                            case VOID ->
                                    reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
                        }

                        sb.append("load");

                        sb.append(argRegNum < 4 ? '_' : ' ').append(argRegNum);

                    }

                    sb.append('\n');
                });

                var className = calledObject.getName();
                if (Objects.equals(className, ElementType.THIS.toString().toLowerCase())) {
                    className = ((ClassType) calledObject.getType()).getName();
                }

                sb.append('\t').append("invokestatic ").append(className).append('.').append(methodName.getLiteral().replaceAll("\"", "")).append('(');

                instruction.getListOfOperands().forEach((op) -> {
                    var opType = this.buildJasminTypeDescriptor(op.getType(), reports);
                    sb.append(opType);
                });

                sb.append(')').append(this.buildJasminTypeDescriptor(instruction.getReturnType(), reports));

            }
            case NEW -> {
                Operand objectClass = (Operand) instruction.getFirstArg();

                var className = objectClass.getName();

                sb.append("\tnew ").append(className).append('\n');
                sb.append("\tdup");

            }
            case arraylength -> {

                Operand op = (Operand) instruction.getFirstArg();
                var descriptor = varTable.get(op.getName());

                int regNum = descriptor.getVirtualReg();

                // load the object reference onto the stack
                sb.append('\t').append('a').append("load").append(regNum < 4 ? '_' : ' ').append(regNum).append('\n');
                sb.append("\tarraylength");

            }
            case ldc -> {
            }
        }

        return sb.toString();
    }

    private String buildJasminIntegerPushInstruction(int value) {
        var sb = new StringBuilder();
        if (value < 6) {
            sb.append("iconst_").append(value);
        } else if (value < 128) {
            sb.append("bipush ").append(value);
        } else if (value < 32767) {
            sb.append("sipush ").append(value);
        } else {
            sb.append("ldc ").append(value);
        }
        return sb.toString();
    }

    private String buildJasminReturnInstruction(ReturnInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        if (instruction.hasReturnValue()) {

            Element elem = instruction.getOperand();

            if (elem.isLiteral()) {

                LiteralElement literal = (LiteralElement) elem;

                sb.append('\t');
                switch (instruction.getReturnType().getTypeOfElement()) {
                    case INT32 -> {
                        var value = literal.getLiteral();

                        sb.append(this.buildJasminIntegerPushInstruction(Integer.parseInt(value)));
                    }
                    case BOOLEAN -> sb.append("bipush ").append(literal.getLiteral());
                    case ARRAYREF, OBJECTREF, STRING, THIS, CLASS, VOID ->
                            reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
                }
                sb.append('\n');
            } else {
                Operand op = (Operand) instruction.getOperand();

                if (op.isParameter()) {

                } else {

                    var descriptor = varTable.get(op.getName());

                    var regNum = descriptor.getVirtualReg();

                    sb.append("\t");
                    switch (instruction.getReturnType().getTypeOfElement()) {
                        case INT32:
                        case BOOLEAN:
                            sb.append('i');
                            break;
                        case ARRAYREF:
                        case OBJECTREF:
                        case STRING:
                        case THIS:
                            sb.append('a');
                            break;
                        case CLASS:
                            // TODO: ?
                            break;
                        case VOID:
                            reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
                            break;
                        default:
                    }
                    sb.append("load");

                    sb.append(regNum < 4 ? '_' : ' ').append(regNum).append('\n');
                }
            }
        }

        sb.append('\t');
        switch (instruction.getReturnType().getTypeOfElement()) {
            case INT32:
            case BOOLEAN:
                sb.append('i');
                break;
            case ARRAYREF:
            case OBJECTREF:
            case STRING:
            case THIS:
                sb.append('a');
                break;
            case CLASS:
                // TODO: ?
                break;
            case VOID:
                // Do nothing
                break;
        }
        sb.append("return");

        return sb.toString();
    }

    private String buildJasminPutfieldOperation(PutFieldInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        var firstOperand = (Operand) instruction.getFirstOperand();
        var secondOperand = (Operand) instruction.getSecondOperand();

        var firstDescriptor = varTable.get(firstOperand.getName());
        var firstRegNum = firstDescriptor.getVirtualReg();

        var thirdOperand = instruction.getThirdOperand();

        sb.append("\taload").append(firstRegNum < 4 ? '_' : ' ').append(firstRegNum).append('\n');

        sb.append('\t');
        if (thirdOperand.isLiteral()) {
            var literal = (LiteralElement) thirdOperand;

            var value = literal.getLiteral();

            switch (literal.getType().getTypeOfElement()) {
                case INT32 -> sb.append(this.buildJasminIntegerPushInstruction(Integer.parseInt(value)));
                case BOOLEAN -> sb.append("iconst_").append(value); // this works since true - 1 and false - 0
                case ARRAYREF -> {
                    instruction.show();
                }
                case OBJECTREF, CLASS, THIS, STRING, VOID -> {
                    // Non literals
                    instruction.show();
                    sb.append("AEDS");
                }
            }

        } else {
            var op = (Operand) thirdOperand;

            var thirdDescriptor = varTable.get(op.getName());
            var regNum = thirdDescriptor.getVirtualReg();

            switch (op.getType().getTypeOfElement()) {
                case INT32, BOOLEAN -> sb.append('i');
                case ARRAYREF, OBJECTREF, STRING, CLASS, THIS -> sb.append('a');
                case VOID ->
                        reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
            }
            sb.append("load").append(regNum < 4 ? '_' : ' ').append(regNum);

        }
        sb.append('\n');

        sb.append("\tputfield ");

        var className = firstOperand.getName();
        if (Objects.equals(className, ElementType.THIS.toString().toLowerCase())) {
            className = ((ClassType) firstOperand.getType()).getName();
        }

        sb.append(className);
        sb.append('/');

        sb.append(secondOperand.getName());
        sb.append(' ').append(this.buildJasminTypeDescriptor(secondOperand.getType(), reports));

        return sb.toString();
    }

    private String buildJasminGetfieldOperation(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        instruction.show();

        var firstOperand = (Operand) instruction.getFirstOperand();
        var secondOperand = (Operand) instruction.getSecondOperand();

        var firstDescriptor = varTable.get(firstOperand.getName());
        var firstRegNum = firstDescriptor.getVirtualReg();

        sb.append("\taload").append(firstRegNum < 4 ? '_' : ' ').append(firstRegNum).append('\n');

        sb.append("\tgetfield ");

        var className = firstOperand.getName();
        if (Objects.equals(className, ElementType.THIS.toString().toLowerCase())) {
            className = ((ClassType) firstOperand.getType()).getName();
        }

        sb.append(className);
        sb.append('/');

        sb.append(secondOperand.getName());
        sb.append(' ').append(this.buildJasminTypeDescriptor(secondOperand.getType(), reports));

        return sb.toString();
    }

    private String buildJasminUnaryOperatorInstruction(UnaryOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        Operand op = (Operand) instruction.getOperand();

        var descriptor = varTable.get(op.getName());

        Operation operation = instruction.getOperation();

        switch (operation.getOpType()) {

            case ADD -> {
            }
            case SUB -> {
            }
            case MUL -> {
            }
            case DIV -> {
            }
            case SHR -> {
            }
            case SHL -> {
            }
            case SHRR -> {
            }
            case XOR -> {
            }
            case AND -> {
            }
            case OR -> {
            }
            case LTH -> {
            }
            case GTH -> {
            }
            case EQ -> {
            }
            case NEQ -> {
            }
            case LTE -> {
            }
            case GTE -> {
            }
            case ANDB -> {
            }
            case ORB -> {
            }
            case NOTB -> {
            }
            case NOT -> {
            }
        }

        return sb.toString();
    }

    private String buildJasminBinaryOperatorInstruction(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        var operation = instruction.getOperation();

        var dType = switch (operation.getTypeInfo().getTypeOfElement()) {
            case INT32 -> "i";
            case BOOLEAN -> "z";
            case ARRAYREF, OBJECTREF, STRING -> "a";
            case CLASS, THIS, VOID -> "";
        };

        sb.append('\t');
        var op1 = instruction.getLeftOperand();
        if (op1.isLiteral()) {
            var literal = (LiteralElement) op1;

            var value = literal.getLiteral();

            switch (literal.getType().getTypeOfElement()) {
                case INT32 -> sb.append(this.buildJasminIntegerPushInstruction(Integer.parseInt(value)));
                case BOOLEAN -> sb.append("iconst_").append(value); // this works since true - 1 and false - 0
                case ARRAYREF -> {
                    instruction.show();
                }
                case OBJECTREF, CLASS, THIS, STRING, VOID -> {
                    // Non literals
                    instruction.show();
                    sb.append("AEDS");
                }
            }
        } else {
            var op = (Operand) op1;

            var descriptor = varTable.get(op.getName());

            int regNum = descriptor.getVirtualReg();

            sb.append(dType).append("load").append(regNum < 4 ? '_' : ' ').append(regNum);
        }
        sb.append('\n');

        sb.append('\t');
        var op2 = instruction.getRightOperand();
        if (op2.isLiteral()) {
            var literal = (LiteralElement) op2;

            var value = literal.getLiteral();

            switch (literal.getType().getTypeOfElement()) {
                case INT32 -> sb.append(this.buildJasminIntegerPushInstruction(Integer.parseInt(value)));
                case BOOLEAN -> sb.append("iconst_").append(value); // this works since true - 1 and false - 0
                case ARRAYREF -> {
                    instruction.show();
                }
                case OBJECTREF, CLASS, THIS, STRING, VOID -> {
                    // Non literals
                    instruction.show();
                    sb.append("AEDS");
                }
            }
        } else {
            var op = (Operand) op2;

            var descriptor = varTable.get(op.getName());

            int regNum = descriptor.getVirtualReg();

            sb.append(dType).append("load").append(regNum < 4 ? '_' : ' ').append(regNum);
        }
        sb.append('\n');

        sb.append('\t').append(dType);
        switch (operation.getOpType()) {

            case ADD -> {
                sb.append("add");
            }
            case SUB -> {
                sb.append("sub");
            }
            case MUL -> {
                sb.append("mul");
            }
            case DIV -> {
                sb.append("div");
            }
            case SHR -> {
            }
            case SHL -> {
            }
            case SHRR -> {
            }
            case XOR -> {
            }
            case AND -> {
            }
            case OR -> {
            }
            case LTH -> {
            }
            case GTH -> {
            }
            case EQ -> {
            }
            case NEQ -> {
            }
            case LTE -> {
            }
            case GTE -> {
            }
            case ANDB -> {
            }
            case ORB -> {
            }
            case NOTB -> {
            }
            case NOT -> {
            }
        }

        return sb.toString();
    }

    private String buildJasminSingleOpInstruction(SingleOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        Element elem = instruction.getSingleOperand();

        if (elem.isLiteral()) {

            sb.append('\t');
            LiteralElement literal = (LiteralElement) elem;

            var value = literal.getLiteral();

            switch (elem.getType().getTypeOfElement()) {
                case INT32 -> sb.append(this.buildJasminIntegerPushInstruction(Integer.parseInt(value)));
                case BOOLEAN -> sb.append("iconst_").append(value); // this works since true - 1 and false - 0
                case ARRAYREF -> {
                    instruction.show();
                }
                case OBJECTREF, CLASS, THIS, STRING, VOID -> {
                    // Non literals
                    instruction.show();
                    sb.append("AEDS");
                }
            }
        } else {

            Operand op = (Operand) elem;

            var descriptor = varTable.get(op.getName());

            int regNum = descriptor.getVirtualReg();

            sb.append('\t');
            switch (op.getType().getTypeOfElement()) {

                case INT32:
                    sb.append('i');
                    break;
                case BOOLEAN:
                    sb.append('z');
                    break;
                case ARRAYREF:
                    sb.append("aa");
                    break;
                case OBJECTREF:
                case STRING:
                case CLASS:
                case THIS:
                    sb.append('a');
                    break;
                case VOID:
                    reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
                    break;
            }

            sb.append("load");

            sb.append(regNum < 4 ? '_' : ' ').append(regNum);
        }

        return sb.toString();
    }

    private String buildJasminTypeDescriptor(Type type, List<Report> reports) {

        return switch (type.getTypeOfElement()) {
            case ARRAYREF -> this.buildJasminArrayTypeDescriptor((ArrayType) type, reports);
            case OBJECTREF, CLASS, THIS -> this.buildJasminClassaTypeDescriptor((ClassType) type, reports);
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case STRING -> "Ljava/lang/String;";
            case VOID -> "V";
        };
    }

    private String buildJasminArrayTypeDescriptor(ArrayType type, List<Report> reports) {
        return "[".repeat(Math.max(0, type.getNumDimensions())) + this.buildJasminTypeDescriptor(type.getElementType(), reports);
    }

    private String buildJasminClassaTypeDescriptor(ClassType type, List<Report> reports) {
        return "L" + type.getName() + ";";
    }
}
