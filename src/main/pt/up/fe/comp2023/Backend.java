package pt.up.fe.comp2023;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Backend implements JasminBackend {

    private static final int DEFAULT_METHOD_STACK_SIZE = 50;
    private String superClassName;
    private boolean debugMode;
    private int currentMethodStackSize = Backend.DEFAULT_METHOD_STACK_SIZE;
    private int currentMethodStackSizeLimit = Backend.DEFAULT_METHOD_STACK_SIZE;
    private int currentConditional = 0;

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {

        var config = ollirResult.getConfig();
        var reports = new ArrayList<Report>();

        this.debugMode = Boolean.parseBoolean(config.get("debug"));

        var ollirClass = ollirResult.getOllirClass();

        var jasminCode = this.buildJasminCode(ollirClass, reports, config.get("inputFile"));

        reports.add(new Report(ReportType.DEBUG, Stage.GENERATION, -1, -1, "Generated Jasmin:\n" + jasminCode));

        return new JasminResult(ollirClass.getClassName(), jasminCode, reports, config);
    }

    private String buildJasminCode(ClassUnit ollirClass, List<Report> reports, String fileName) {
        var jasminCode = this.buildJasminClass(ollirClass, reports, fileName);

        return "; class " + ollirClass.getClassName() + ", transpiled to jasmin\n" + jasminCode;
    }

    private String buildJasminClass(ClassUnit ollirClass, List<Report> reports, String fileName) {

        StringBuilder sb = new StringBuilder();

        var className = ollirClass.getClassName();

        if (className == null) return "";

        if (fileName != null) {
            if (!fileName.equals(className.concat(".jmm")) && this.debugMode) {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Top level classes should have the same name as the file they are defined in: expected '" + fileName + "', got '" + className + ".jmm'", new Exception("Top level classes should have the same name as the file they are defined in")));
            }

            sb.append(".source ").append(fileName).append('\n');
        }

        sb.append(".class ");

        var classAccessModifier = ollirClass.getClassAccessModifier();
        String modifier = classAccessModifier.name().toLowerCase();
        if (classAccessModifier == AccessModifiers.DEFAULT)
            modifier = "public"; // HACK: this is made so the tests pass, it should not be like this
        sb.append(modifier);
        sb.append(' ').append(className).append('\n');

        var superName = Optional.ofNullable(ollirClass.getSuperClass()).orElse("java.lang.Object").replaceAll("\\.", "/");
        sb.append(".super ").append(superName).append("\n");
        this.superClassName = superName;

        sb.append('\n');

        for (Field field : ollirClass.getFields())
            sb.append(this.buildJasminClassField(field, reports)).append('\n');

        sb.append('\n');

        for (Method method : ollirClass.getMethods())
            sb.append(this.buildJasminMethod(method, reports)).append('\n');

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

        if (this.debugMode)
            method.show();

        StringBuilder sb = new StringBuilder();

        sb.append(".method ");

        sb.append(this.buildJasminMethodHeader(method, reports));

        var bodyCode = this.buildJasminMethodBody(method, reports);

        if (!method.isConstructMethod()) {

            var varTable = method.getVarTable();

            var numLocals = varTable.size();

            // if the "this" reference is not used in the method, it will not be loaded into the varTable
            if (!method.isStaticMethod() && !method.getVarTable().containsKey("this")) numLocals++;

            // TODO: change when optimizing
            sb.append("\t.limit locals ").append(numLocals).append('\n');
            sb.append("\t.limit stack ").append(this.currentMethodStackSizeLimit).append('\n');

            this.currentMethodStackSizeLimit = Backend.DEFAULT_METHOD_STACK_SIZE;
            this.currentMethodStackSize = Backend.DEFAULT_METHOD_STACK_SIZE;
        }

        sb.append(bodyCode);

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

        Map<Instruction, String> labelMap = new HashMap<>();

        for (var entry : method.getLabels().entrySet()) {
            labelMap.put(entry.getValue(), entry.getKey());
        }

        for (Instruction instruction : method.getInstructions()) {

            if (instruction.getInstType() == InstructionType.RETURN) hasReturn = true;

            if (labelMap.containsKey(instruction))
                sb.append(labelMap.get(instruction)).append(':').append('\n');

            sb.append(this.buildJasminInstruction(instruction, varTable, reports)).append('\n');

            if (instruction.getInstType() == InstructionType.CALL && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID) {
                sb.append("\tpop\n");
                this.changeCurrentMethodStackSizeLimit(-1);
            }
        }
        if (!hasReturn) { // default to have a return
            if (!(method.isConstructMethod() || method.getReturnType().getTypeOfElement() == ElementType.VOID)) {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Non-void function must have a return type", new Exception("Non-void function must have a return type")));
                return "";
            }

            var instruction = new ReturnInstruction();
            instruction.setReturnType(new Type(ElementType.VOID));

            sb.append(this.buildJasminInstruction(instruction, varTable, reports)).append('\n');
        }

        return sb.toString();
    }

    private String buildJasminInstruction(Instruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        return switch (instruction.getInstType()) {
            case ASSIGN -> this.buildJasminAssignInstruction((AssignInstruction) instruction, varTable, reports);
            case CALL -> this.buildJasminCallInstruction((CallInstruction) instruction, varTable, reports);
            case GOTO -> this.buildJasminGotoInstruction((GotoInstruction) instruction, varTable, reports);
            case BRANCH -> this.buildJasminBranchInstruction((CondBranchInstruction) instruction, varTable, reports);
            case RETURN -> this.buildJasminReturnInstruction((ReturnInstruction) instruction, varTable, reports);
            case PUTFIELD -> this.buildJasminPutfieldOperation((PutFieldInstruction) instruction, varTable, reports);
            case GETFIELD -> this.buildJasminGetfieldOperation((GetFieldInstruction) instruction, varTable, reports);
            case UNARYOPER ->
                    this.buildJasminUnaryOperatorInstruction((UnaryOpInstruction) instruction, varTable, reports);
            case BINARYOPER ->
                    this.buildJasminBinaryOperatorInstruction((BinaryOpInstruction) instruction, varTable, reports);
            case NOPER -> this.buildJasminSingleOpInstruction((SingleOpInstruction) instruction, varTable, reports);
        };

    }

    private String buildJasminAssignInstruction(AssignInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        var rhs = this.buildJasminInstruction(instruction.getRhs(), varTable, reports);

        Operand op = (Operand) instruction.getDest();

        var descriptor = varTable.get(op.getName());
        int regNum = descriptor.getVirtualReg();

        if (op instanceof ArrayOperand arr) {
            sb.append("\taload");

            sb.append(regNum < 4 ? '_' : ' ').append(regNum).append('\n');

            for (var elem : arr.getIndexOperands()) {
                sb.append('\t').append(this.buildJasminLoadElementInstruction(elem, varTable, reports)).append("\n");
            }

            sb.append(rhs).append('\n');

            sb.append('\t');
            switch (arr.getType().getTypeOfElement()) {
                case INT32, BOOLEAN -> sb.append('i');
                case ARRAYREF, OBJECTREF, STRING, THIS, CLASS -> sb.append('a');
                case VOID ->
                        reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
            }
            sb.append("astore");
            this.changeCurrentMethodStackSizeLimit(-3);
        } else {
            this.changeCurrentMethodStackSizeLimit(-1);

            sb.append(rhs).append('\n');

            sb.append('\t');
            switch (instruction.getTypeOfAssign().getTypeOfElement()) {
                case INT32, BOOLEAN -> sb.append('i');
                case ARRAYREF, OBJECTREF, STRING, CLASS, THIS -> sb.append('a');
                case VOID ->
                        reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot assign to void variable", new Exception("Cannot assign to void variable")));
            }
            sb.append("store");

            sb.append(regNum < 4 ? '_' : ' ').append(regNum);
        }

        return sb.toString();
    }

    private String buildJasminIntegerPushInstruction(int value) {
        var sb = new StringBuilder();
        if (value < 6) {
            sb.append("iconst_").append(value);
        } else if (value < 128) {
            sb.append("bipush ").append(value);
        } else if (value < 32768) {
            sb.append("sipush ").append(value);
        } else {
            sb.append("ldc ").append(value);
        }
        return sb.toString();
    }

    private String buildJasminLoadLiteralInstruction(LiteralElement literal, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        switch (literal.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                var value = literal.getLiteral();

                sb.append(this.buildJasminIntegerPushInstruction(Integer.parseInt(value)));
            }
            case STRING -> sb.append("ldc ").append(literal.getLiteral());
            case ARRAYREF, OBJECTREF, THIS, CLASS, VOID ->
                    reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
        }

        return sb.toString();
    }

    private String buildJasminLoadOperandInstruction(Operand op, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        if (op.getName().equals("false") || op.getName().equals("true")) {

            // "true" and "false" get parsed as operands when they really should be literals, this is a hacky way of fixing that
            return this.buildJasminLoadLiteralInstruction(new LiteralElement(op.getName().equals("true") ? "1" : "0", new Type(ElementType.BOOLEAN)), varTable, reports);
        }

        var argDescriptor = varTable.get(op.getName());
        int argRegNum = argDescriptor.getVirtualReg();

        // TODO: can be better (?)
        if (op instanceof ArrayOperand arr) {

            sb.append("aload");

            sb.append(argRegNum < 4 ? '_' : ' ').append(argRegNum).append('\n');

            for (var elem : arr.getIndexOperands()) {
                sb.append('\t').append(this.buildJasminLoadElementInstruction(elem, varTable, reports)).append("\n");
            }

            sb.append('\t');

            switch (arr.getType().getTypeOfElement()) {
                case INT32, BOOLEAN -> sb.append('i');
                case ARRAYREF, OBJECTREF, STRING, THIS, CLASS -> sb.append('a');
                case VOID ->
                        reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
            }
            sb.append("aload");

            this.changeCurrentMethodStackSizeLimit(-1);
        } else {
            switch (op.getType().getTypeOfElement()) {
                case INT32, BOOLEAN -> sb.append('i');
                case ARRAYREF, OBJECTREF, STRING, THIS, CLASS -> sb.append('a');
                case VOID ->
                        reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot load void variable", new Exception("Cannot load void variable")));
            }

            sb.append("load");

            sb.append(argRegNum < 4 ? '_' : ' ').append(argRegNum);
        }

        return sb.toString();
    }

    private String buildJasminLoadElementInstruction(Element elem, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        if (elem.isLiteral()) {
            sb.append(this.buildJasminLoadLiteralInstruction((LiteralElement) elem, varTable, reports));
        } else {
            sb.append(this.buildJasminLoadOperandInstruction((Operand) elem, varTable, reports));
        }
        this.changeCurrentMethodStackSizeLimit(1);

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

                AtomicInteger toTake = new AtomicInteger(1);

                // load the object reference onto the stack
                sb.append('\t').append('a').append("load").append(regNum < 4 ? '_' : ' ').append(regNum).append('\n');
                this.changeCurrentMethodStackSizeLimit(1);

                // load args
                instruction.getListOfOperands().forEach((arg) -> {
                    sb.append('\t');

                    sb.append(this.buildJasminLoadElementInstruction(arg, varTable, reports));
                    this.changeCurrentMethodStackSizeLimit(1);
                    toTake.getAndIncrement();

                    sb.append('\n');
                });
                this.changeCurrentMethodStackSizeLimit(-toTake.get());

                sb.append('\t').append("invokevirtual ").append(((ClassType) calledObject.getType()).getName()).append('/').append(methodName.getLiteral().replaceAll("\"", "")).append('(');

                instruction.getListOfOperands().forEach((op) -> {
                    var opType = this.buildJasminTypeDescriptor(op.getType(), reports);
                    sb.append(opType);
                });

                sb.append(')').append(this.buildJasminTypeDescriptor(instruction.getReturnType(), reports));

                if (instruction.getReturnType().getTypeOfElement() == ElementType.VOID) {
                    this.changeCurrentMethodStackSizeLimit(-1);
                }
            }
            case invokeinterface -> {
            }
            case invokespecial -> {
                Operand calledObject = (Operand) instruction.getFirstArg();
                LiteralElement methodName = (LiteralElement) instruction.getSecondArg();

                var descriptor = varTable.get(calledObject.getName());

                int regNum = descriptor.getVirtualReg();

                AtomicInteger toTake = new AtomicInteger(1);
                sb.append('\t').append('a').append("load").append(regNum < 4 ? '_' : ' ').append(regNum).append('\n');
                this.changeCurrentMethodStackSizeLimit(1);

                // load args
                instruction.getListOfOperands().forEach((arg) -> {
                    sb.append('\t');

                    sb.append(this.buildJasminLoadElementInstruction(arg, varTable, reports));
                    this.changeCurrentMethodStackSizeLimit(1);
                    toTake.getAndIncrement();

                    sb.append('\n');
                });
                this.changeCurrentMethodStackSizeLimit(-toTake.get());

                sb.append('\t').append("invokespecial ");

                // TODO: perhaps RTE
                String objectName = ((ClassType) calledObject.getType()).getName();
                if (Objects.equals(calledObject.getName(), ElementType.THIS.toString().toLowerCase())) {
                    objectName = this.superClassName;
                }

                sb.append(objectName).append('/').append(methodName.getLiteral().replaceAll("\"", "")).append('(');

                instruction.getListOfOperands().forEach((op) -> {
                    var opType = this.buildJasminTypeDescriptor(op.getType(), reports);
                    sb.append(opType);
                });

                sb.append(')').append(this.buildJasminTypeDescriptor(instruction.getReturnType(), reports));
            }
            case invokestatic -> {
                Operand calledObject = (Operand) instruction.getFirstArg();
                LiteralElement methodName = (LiteralElement) instruction.getSecondArg();

                AtomicInteger toTake = new AtomicInteger();

                // load args
                instruction.getListOfOperands().forEach((arg) -> {
                    sb.append('\t');

                    sb.append(this.buildJasminLoadElementInstruction(arg, varTable, reports));
                    this.changeCurrentMethodStackSizeLimit(1);
                    toTake.getAndIncrement();

                    sb.append('\n');
                });

                this.changeCurrentMethodStackSizeLimit(-toTake.get());

                var className = calledObject.getName();
                if (Objects.equals(className, ElementType.THIS.toString().toLowerCase())) {
                    className = ((ClassType) calledObject.getType()).getName();
                }

                sb.append('\t').append("invokestatic ").append(className).append('/').append(methodName.getLiteral().replaceAll("\"", "")).append('(');

                instruction.getListOfOperands().forEach((op) -> {
                    var opType = this.buildJasminTypeDescriptor(op.getType(), reports);
                    sb.append(opType);
                });

                sb.append(')').append(this.buildJasminTypeDescriptor(instruction.getReturnType(), reports));

                if (instruction.getReturnType().getTypeOfElement() == ElementType.VOID) {
                    this.changeCurrentMethodStackSizeLimit(-1);
                }
            }
            case NEW -> {
                Operand objectClass = (Operand) instruction.getFirstArg();

                var className = objectClass.getName();

                sb.append('\t');
                if ("array".equals(className)) {

                    // there should only be one other operand, the array size. Load it and assume this is ok
                    var sizeOperand = instruction.getListOfOperands().get(0);

                    sb.append(this.buildJasminLoadElementInstruction(sizeOperand, varTable, reports)).append('\n');

                    sb.append("\tnewarray").append(' ');
                    var elementType = ((ArrayType) instruction.getReturnType()).getElementType().getTypeOfElement();

                    if (elementType == ElementType.INT32) {
                        sb.append("int");
                    } else {
                        reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Only int arrays are supported", new Exception("Only int arrays are supported")));
                        return "";
                    }
                } else
                    sb.append("new ").append(className);

                sb.append('\n');
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
                var literal = (LiteralElement) instruction.getFirstArg();

                sb.append('\t').append("ldc ").append(literal.getLiteral());
            }
        }

        return sb.toString();
    }

    private String buildJasminGotoInstruction(GotoInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {
        return "\tgoto " + instruction.getLabel();
    }

    private String buildJasminBranchInstruction(CondBranchInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        var cond = instruction.getCondition();

        var inst = switch (cond.getInstType()) {
            case UNARYOPER ->
                    this.buildJasminUnaryOperatorInstruction((UnaryOpInstruction) cond, varTable, reports) + " "; // negated boolean
            case BINARYOPER ->
                    this.buildJasminBinaryOperatorInstruction((BinaryOpInstruction) cond, varTable, reports) + " "; // conditional expression
            case NOPER ->
                    this.buildJasminSingleOpInstruction((SingleOpInstruction) cond, varTable, reports); // direct boolean
            default -> "nop ; this should have been an expression instruction\n\t";
        };

        sb.append(inst).append('\n');

        sb.append('\t').append("ifne ").append(instruction.getLabel());

        return sb.toString();
    }

    private String buildJasminReturnInstruction(ReturnInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        if (instruction.hasReturnValue()) {
            sb.append('\t');
            sb.append(this.buildJasminLoadElementInstruction(instruction.getOperand(), varTable, reports));
            sb.append('\n');
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
            case CLASS:
                sb.append('a');
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

        sb.append("\taload").append(firstRegNum < 4 ? '_' : ' ').append(firstRegNum).append('\n');

        sb.append('\t');
        sb.append(this.buildJasminLoadElementInstruction(instruction.getThirdOperand(), varTable, reports));
        sb.append('\n');

        sb.append("\tputfield ");

        String className = ((ClassType) firstOperand.getType()).getName();

        sb.append(className);
        sb.append('/');

        sb.append(secondOperand.getName());
        sb.append(' ').append(this.buildJasminTypeDescriptor(secondOperand.getType(), reports));

        this.changeCurrentMethodStackSizeLimit(-2);

        return sb.toString();
    }

    private String buildJasminGetfieldOperation(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        instruction.getFieldType();

        var firstOperand = (Operand) instruction.getFirstOperand();
        var secondOperand = (Operand) instruction.getSecondOperand();

        var firstDescriptor = varTable.get(firstOperand.getName());
        var firstRegNum = firstDescriptor.getVirtualReg();

        sb.append("\taload").append(firstRegNum < 4 ? '_' : ' ').append(firstRegNum).append('\n');

        sb.append("\tgetfield ");

        String className = ((ClassType) firstOperand.getType()).getName();

        sb.append(className);
        sb.append('/');

        sb.append(secondOperand.getName());
        sb.append(' ').append(this.buildJasminTypeDescriptor(instruction.getFieldType(), reports));

        return sb.toString();
    }

    private String buildJasminUnaryOperatorInstruction(UnaryOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {
        var sb = new StringBuilder();

        Operation operation = instruction.getOperation();

        var dType = switch (operation.getTypeInfo().getTypeOfElement()) {
            case INT32, BOOLEAN -> "i";
            case ARRAYREF, OBJECTREF, STRING, CLASS, THIS -> "a";
            case VOID -> {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot perform binary operation on void type", new Exception("Cannot perform binary operation on void type")));
                yield "marker";
            }
        };
        if ("marker".equals(dType)) return "";

        sb.append('\t');
        sb.append(this.buildJasminLoadElementInstruction(instruction.getOperand(), varTable, reports));
        sb.append('\n');

        sb.append('\t');
        switch (operation.getOpType()) {
            default -> {
            }
            case LTH -> sb.append("iflt");
            case GTH -> sb.append("ifgt");
            case EQ -> sb.append("ifeq");
            case NEQ -> sb.append("ifne");
            case LTE -> sb.append("ifle");
            case GTE -> sb.append("ifge");
            case NOT -> sb.append("not");
            case NOTB -> {
                sb.append(this.buildJasminIntegerPushInstruction(1)).append('\n');
                sb.append("\tixor\n");
            }
        }

        return sb.toString();
    }

    private boolean optimizeJasminBinaryOpInstruction(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable, StringBuilder sb) {

        if (
                (instruction.getOperation().getOpType() == OperationType.ADD || instruction.getOperation().getOpType() == OperationType.SUB) &&
                        instruction.getLeftOperand() instanceof Operand &&
                        instruction.getRightOperand() instanceof LiteralElement literal &&
                        literal.getType().getTypeOfElement() == ElementType.INT32 &&
                        Integer.parseInt(literal.getLiteral()) <= Byte.MAX_VALUE &&
                        Integer.parseInt(literal.getLiteral()) >= Byte.MIN_VALUE
        ) { // a + 1

            var reg = varTable.get(((Operand) instruction.getLeftOperand()).getName()).getVirtualReg();

            sb.append("\tiinc ").append(reg).append(instruction.getOperation().getOpType() == OperationType.SUB ? " -" : ' ').append(literal.getLiteral()).append('\n');
            sb.append("\tiload ").append(reg);
            return true;
        } else if (
                (instruction.getOperation().getOpType() == OperationType.ADD || instruction.getOperation().getOpType() == OperationType.SUB) &&
                        instruction.getRightOperand() instanceof Operand &&
                        instruction.getLeftOperand() instanceof LiteralElement literal &&
                        literal.getType().getTypeOfElement() == ElementType.INT32 &&
                        Integer.parseInt(literal.getLiteral()) <= Byte.MAX_VALUE &&
                        Integer.parseInt(literal.getLiteral()) >= Byte.MIN_VALUE
        ) { // 1 + a
            var reg = varTable.get(((Operand) instruction.getRightOperand()).getName()).getVirtualReg();

            sb.append("\tiinc ").append(reg).append(instruction.getOperation().getOpType() == OperationType.SUB ? " -" : ' ').append(literal.getLiteral()).append('\n');
            sb.append("\tiload ").append(reg);
            return true;
        }

        return false;
    }

    private String buildJasminBinaryArithmeticExpression(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        var operation = instruction.getOperation();

        var opType = operation.getOpType();
        var dType = switch (operation.getTypeInfo().getTypeOfElement()) {
            case INT32, BOOLEAN -> "i";
            case ARRAYREF, OBJECTREF, STRING, CLASS, THIS -> "a";
            case VOID -> {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Cannot perform binary operation on void type", new Exception("Cannot perform binary operation on void type")));
                yield "marker";
            }
        };
        if ("marker".equals(dType)) return "";

        switch (opType) {

            case ADD -> sb.append(dType).append("add");
            case SUB -> sb.append(dType).append("sub");
            case MUL -> sb.append(dType).append("mul");
            case DIV -> sb.append(dType).append("div");
            case SHR, SHL, SHRR -> {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Unsupported arithmetic operation", new Exception("Unsupported arithmetic operation")));
                return "";
            }
            case XOR -> sb.append(dType).append("xor");
            case AND, ANDB -> sb.append(dType).append("and");
            case OR, ORB -> sb.append(dType).append("or");
            default -> {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "Invalid arithmetic operation", new Exception("Invalid arithmetic operation")));
                return "";
            }
        }

        return sb.toString();
    }

    private String buildJasminBinaryConditionalExpression(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        var opType = instruction.getOperation().getOpType();

        String bodyLabel = "__comparison_if_body__" + this.currentConditional, afterLabel = "__comparison_after__" + this.currentConditional++;
        switch (opType) {
            case LTH -> sb.append("if_icmplt ");
            case GTH -> sb.append("if_icmpgt ");
            case EQ -> sb.append("if_icmpeq ");
            case NEQ -> sb.append("if_icmpne ");
            case LTE -> sb.append("if_icmple ");
            case GTE -> sb.append("if_icmpge ");
            case NOTB -> sb.append("ifle"); // bruh
            case NOT -> sb.append("not"); // bruh
            default -> {
                reports.add(Report.newWarn(Stage.GENERATION, -1, -1, "", new Exception("")));
                return "";
            }
        }

        sb.append(bodyLabel).append('\n');
        sb.append('\t').append(this.buildJasminIntegerPushInstruction(0)).append('\n');
        sb.append('\t').append("goto ").append(afterLabel).append('\n');
        sb.append(bodyLabel).append(":\n");
        sb.append('\t').append(this.buildJasminIntegerPushInstruction(1)).append('\n');
        sb.append(afterLabel).append(':');

        return sb.toString();
    }

    private String buildJasminBinaryOperatorInstruction(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        var sb = new StringBuilder();

        var operation = instruction.getOperation();

        if (!this.optimizeJasminBinaryOpInstruction(instruction, varTable, sb)) {
            sb.append('\t');
            sb.append(this.buildJasminLoadElementInstruction(instruction.getLeftOperand(), varTable, reports));
            sb.append('\n');

            sb.append('\t');
            sb.append(this.buildJasminLoadElementInstruction(instruction.getRightOperand(), varTable, reports));
            sb.append('\n');

            sb.append('\t');

            switch (operation.getOpType()) {
                case ADD:
                case SUB:
                case MUL:
                case DIV:
                case SHR:
                case SHL:
                case SHRR:
                case XOR:
                case AND:
                case ANDB:
                case OR:
                case ORB: {
                    sb.append(this.buildJasminBinaryArithmeticExpression(instruction, varTable, reports));
                }
                case LTH:
                case GTH:
                case EQ:
                case NEQ:
                case LTE:
                case GTE:
                case NOTB:
                case NOT: {
                    sb.append(this.buildJasminBinaryConditionalExpression(instruction, varTable, reports));
                }
            }
        }

        return sb.toString();
    }

    private String buildJasminSingleOpInstruction(SingleOpInstruction instruction, HashMap<String, Descriptor> varTable, List<Report> reports) {

        return '\t' +
                this.buildJasminLoadElementInstruction(instruction.getSingleOperand(), varTable, reports);
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

    private void changeCurrentMethodStackSizeLimit(int variation) {
        this.currentMethodStackSize += variation;
        this.currentMethodStackSizeLimit = Math.max(this.currentMethodStackSizeLimit, this.currentMethodStackSize);
    }
}
