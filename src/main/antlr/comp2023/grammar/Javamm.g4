grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

// Skip comments
MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;
SINGLE_LINE_COMMENT : '//' (~[\r\n])* -> skip;

// Skip white space characters
WS : ( EOL | WHITE_SPACE )+ -> skip ;

// white-space and new-line characters
EOL : [\n\r] ;
WHITE_SPACE : [ \t\f] ;

// these are keywords, should take precedence
MODIFIER: ( PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | ABSTRACT | TRANSIENT | VOLATILE ) ;

// Keywords
ABSTRACT : 'abstract' ;
ASSERT : 'assert' ;
BREAK : 'break' ;
CASE : 'case' ;
CATCH : 'catch' ;
CLASS : 'class' ;
CONTINUE : 'continue' ;
DEFAULT : 'default' ;
DO : 'do' ;
ELSE : 'else' ;
ENUM : 'enum' ;
EXTENDS : 'extends' ;
FINAL : 'final' ;
FINALLY : 'finally' ;
FOR : 'for';
IF : 'if' ;
IMPLEMENTS : 'implements' ;
IMPORT : 'import' ;
INSTANCEOF : 'instanceof' ;
INTERFACE : 'interface' ;
NATIVE : 'native' ;
NEW : 'new' ;
PACKAGE : 'package';
PRIVATE : 'private' ;
PROTECTED : 'protected' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
STATIC : 'static' ;
SUPER : 'super';
SWITCH : 'switch' ;
SYNCHRONIZED : 'synchronized' ;
THIS : 'this' ;
THROW : 'throw' ;
THROWS : 'throws' ;
TRANSIENT : 'transient' ;
TRY : 'try' ;
VOID : 'void' ;
VOLATILE : 'volatile' ;
WHILE : 'while' ;

// Unused keywords
CONST : 'const' ;
GOTO : 'goto' ;

// Primitive Types
PRIMITIVE_TYPE : ( BOOLEAN_TYPE | BYTE_TYPE | CHAR_TYPE | DOUBLE_TYPE | FLOAT_TYPE | INTEGER_TYPE | LONG_TYPE ) ;
BOOLEAN_TYPE : 'boolean' ;
BYTE_TYPE : 'byte' ;
CHAR_TYPE : 'char' ;
DOUBLE_TYPE : 'double' ;
FLOAT_TYPE : 'float' ;
INTEGER_TYPE : 'int' ;
LONG_TYPE : 'long' ;

// literals should have priority in their interpretation
LITERAL: ( NULL_LITERAL | NUMBER_LITERAL | BOOLEAN_LITERAL | CHAR_LITERAL | STRING_LITERAL ) ;
NULL_LITERAL: 'null' ;
NUMBER_LITERAL: INTEGER_LITERAL | FLOAT_LITERAL;
BOOLEAN_LITERAL: ( 'false' | 'true' ) ;
STRING_LITERAL: '"' ( TEXT_CHAR | '\'' )* '"' ;
CHAR_LITERAL: '\'' ( TEXT_CHAR | '"' ) '\'' ;
// This would be nice but we can't have octal right now :(
//INTEGER_LITERAL : '0' OCT_DIGIT+ | DIGIT+ | '0x' HEX_DIGIT+ | '0b' BIN_DIGIT+ ;
INTEGER_LITERAL : '0' | [1-9] DIGIT* ;
FLOAT_LITERAL : ( DIGIT+ '.' DIGIT* | DIGIT* '.' DIGIT+ ) ( [eE] DIGIT+ )?;

// IDs in the program (variable/method/class names)
// CLASS_NAME : ID ;
// FULLY_QUALIFIED_NAME : ( ID DOT )* ID ; // this would give errors because of the lexer, hard code them all
ID : ( LETTER | UNDERSCORE | DOLLAR )( LETTER | UNDERSCORE | DIGIT | DOLLAR )* ;

// basic characters and fragment tokens
fragment TEXT_CHAR : ~['"\\\r\n\f] | ESCAPED_CHAR ;
fragment ESCAPED_CHAR : '\\' ( [tbnrfs'"\\] | 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT | OCT_DIGIT OCT_DIGIT? OCT_DIGIT?);
fragment SPECIAL_CHARS: [!"#%&()=?'.:,;\\|] ;
fragment DIGIT : [0-9] ;
fragment HEX_DIGIT : [0-9a-fA-F] ;
fragment BIN_DIGIT : [01] ;
fragment OCT_DIGIT : [0-7] ;
fragment LETTER : [a-zA-Z] ;
fragment UNDERSCORE : '_' ;
fragment DOLLAR : '$' ;
DOT : '.' ;
SC : ';' ;
COMMA : ',' ;
COLON : ':' ;
QM : '?';
LB : '{' ;
RB : '}' ;
LP : '(' ;
RP : ')' ;
LSB : '[' ;
RSB : ']' ;

program: package_declaration? ( import_statement )* class_declaration EOF ;

package_declaration : PACKAGE ( packagePath+=ID DOT )* packageName=ID #PackageDeclaration;

import_statement : IMPORT ( classPackage+=ID DOT )* className=ID SC #ImportStatement ;

class_declaration : classModifiers+=MODIFIER* CLASS className=ID class_extension? LB program_definition RB #ClassDeclaration ;

class_extension : EXTENDS ( parentPackage+=ID DOT )* parentClass=ID #ParentClass ;

program_definition : ( ( variable_declaration SC ) | method_declaration )* ;

method_declaration: (modifiers+=MODIFIER)* type methodName=ID LP parameter_list? RP LB statement* RB #MethodDeclaration ;
variable_declaration: (modifiers+=MODIFIER)* assignment_statement ;

parameter_list : type argName+=ID ( COMMA type argName+=ID )* #ParameterList ;
argument_list : expression ( COMMA expression )* #ArgumentList ;

assignment_statement: type id=ID ( op='=' expression )? #VariableDeclaration ; // TODO: there might be edge cases with this

statement
    : LB statement* RB #StatementBlock
    | IF LP expression RP statement ELSE statement #IfStatement
    | WHILE LP expression RP statement #WhileStatement
    | DO statement WHILE LP expression RP SC #DoStatement
    | FOR LP expression? SC expression? SC expression? RP statement #ForStatement
    | FOR LP type id=ID COLON expression RP statement #ForEachStatement
    | SWITCH LP expression RP LB case_statement* RB #SwitchStatement
    | RETURN expression? SC #ReturnStatement
    | BREAK SC #BreakStatement
    | CONTINUE SC #ContinueStatement
    | expression SC #ExpressionStatement
    | variable_declaration SC #AssignmentStatement
    ;

case_statement
    : CASE val=LITERAL COLON statement* #CaseStatement
    | DEFAULT COLON statement* #DefaultStatement
    ;

type
    : id=PRIMITIVE_TYPE #PrimitiveType
    | VOID #VoidType
    | ( typePrefix+=ID DOT )* id=ID #ComplexType
    | type LSB RSB #ArrayType
    ;

expression
    : LP expression RP #ExplicitPriority
    | NEW id=ID LP argument_list? RP #NewObject
    | NEW type LSB expression RSB #NewArray
    | expression DOT member=ID LP argument_list? RP #MethodCall
    | expression DOT member=ID #PropertyAccess
    | expression LSB expression RSB #ArrayAccess
    | expression op=('++' | '--') #UnaryPostOp
    | op=('!' | '++' | '--' | '+' | '-' | '~') expression #UnaryPreOp
    | expression op=('*' | '/' | '%') expression #BinaryOp
    | expression op=('+' | '-') expression #BinaryOp
    | expression op=('<<' | '>>' | '>>>') expression #BinaryOp
    | expression op=('>' | '<' | '>=' | '<=') expression #BinaryOp
    | expression op=('==' | '!=') expression #BinaryOp
    | expression op='&' expression #BinaryOp
    | expression op='^' expression #BinaryOp
    | expression op='|' expression #BinaryOp
    | expression op='&&' expression #BinaryOp
    | expression op='||' expression #BinaryOp
    | expression QM expression COLON expression #TernaryOp
    | expression op=('=' | '+=' | '-=' | '*=' | '/=' | '%=' | '<<=' | '>>=' | '>>>=') expression #AssignmentExpression
    | value=LITERAL #LiteralExpression
    | id=ID #IdentifierExpression
    | THIS #ThisExpression
    ;
