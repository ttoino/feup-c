package pt.up.fe.comp2023;

public final class Constants {
    public static final String[] INTEGER_TYPES = {
            "long", "int", "short", "byte", "char"
    };

    public static final String[] PRIMITIVE_TYPES = {
            "boolean", "byte", "char", "double", "float", "int", "long", "short"
    };

    public static final String[] INTEGER_OPS = {
            "++", "--", "+", "-", "~", "*", "/", "%", "<<", ">>", ">>>", ">", "<", ">=", "<=", "&", "^", "|", "=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", ">>>="
    };

    public static final String[] FLOAT_TYPES = {
            "float", "double"
    };

    public static final String[] FLOAT_OPS = {
            "++", "--", "+", "-", "*", "/", "%", ">", "<", ">=", "<=", "=", "+=", "-=", "*=", "/=", "%="
    };

    public static final String[] BOOLEAN_OPS = {
            "!", "&&", "||", "&", "|", "^"
    };

    public static final String[] UNIVERSAL_OPS = {
            "=", "==", "!="
    };

    public static final String[] UNIVERSAL_IMPORTS = {
            "System", "String"
    };

    public static final String[] CLASS_MODIFIERS = {
            "public", "final", "abstract"
    };

    public static final String[] FIELD_MODIFIERS = {
            "public", "protected", "private", "final", "static", "volatile", "transient"
    };

    public static final String[] CONSTRUCTOR_MODIFIERS = {
            "public", "protected", "private"
    };

    public static final String[] METHOD_MODIFIERS = {
            "public", "protected", "private", "final", "static", "native", "synchronized", "volatile", "transient", "abstract"
    };
}
