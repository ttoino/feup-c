grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

// Skip comments
MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;
SINGLE_LINE_COMMENT : '//' (~[\r\n])* -> skip;

// Skip white space characters
WS : ( EOL | WHITE_SPACE )+ -> skip ;

// literals should have priority in their interpretation
LITERAL: ( NUMBER_LITERAL | BOOLEAN_LITERAL | CHAR_LITERAL /*| STRING_LITERAL*/ ) ;
NUMBER_LITERAL: NUMBER ;
BOOLEAN_LITERAL: ( 'false' | 'true' ) ;
// STRING_LITERAL: '"' TEXT '"' ; // TODO: handle double quote
CHAR_LITERAL: '\'' ( LETTER | DIGIT | SYMBOL | EOL | WHITE_SPACE ) '\'' ; // TODO: handle single quoted

// these are keywords, should take precedence
ACCESS_MODIFIER: ( 'public' | 'private' | 'protected' ) ;
NON_ACCESS_MODIFIER: ( 'static' | 'final' | 'abstract' ) ; // TODO: may need to change this when we create method local variables

TYPE: ( 'int' | 'long' | 'short' | 'byte' | 'char' | 'boolean' | 'String' ) ( '[' ']' )?;

NUMBER : DIGIT+ ;
// TEXT : (LETTER | DIGIT /*| SYMBOL */| WHITE_SPACE)+ ;

SYMBOL : ( SPECIAL_CHARS | DOLLAR | UNDERSCORE ) ;

// IDs in the program (variable/method/class names)
ID : ( LETTER | UNDERSCORE | DOLLAR )( LETTER | UNDERSCORE | DIGIT | DOLLAR )* ;

// white-space and new-line characters
EOL : [\n\r] ;
WHITE_SPACE : [ \t\f] ;

// basic characters
SPECIAL_CHARS: [!"#%&()=?'.:,;\\|] ; // TODO: handle escapes
DIGIT : [0-9] ;
LETTER : [a-zA-Z] ;
UNDERSCORE : '_' ;
DOLLAR : '$' ;

program
    : ( import_statement )* class_declaration EOF
    ;

import_statement : 'import' ID ( '.' ID )* ';' #ImportStatement ;

class_declaration : 'class' className=ID ( 'extends' parentClass=ID )? '{' program_definition '}' #ClassDeclaration ;

program_definition : ( variable_declaration | method_declaration )* ;

variable_declaration: accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER*  assignment_statement ;

method_declaration
    : accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER* returnType=TYPE methodName=ID '(' parameter_list? ')' '{' statement* ( 'return' returnValue=( ID | LITERAL ) ';' )? '}' #Method
    | accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER* returnType='void' methodName=ID '(' parameter_list? ')' '{' statement* ( 'return' ';' )? '}' #VoidMethod
    | accessModifier='public' 'static' returnType='void' methodName='main' '(' argType='String[]' argName='args' ')' '{' statement* ( 'return' ';' )? '}' #MainMethod // isolate the main method so it is distinct in the AST
    ;

method_call
    : method_call '.' method_call
    | ( ID ( '.' ID )? '.' )? methodName=ID '(' argument_list? ')'
    ;

parameter_list : argType=TYPE argName=ID ( ',' argType=TYPE argName=ID )* ;
argument_list : argName=ID ( ',' argName=ID )* ;

assignment_statement: varType=TYPE id=ID op='=' value=statement ; // TODO: there might be edge cases with this

statement
    : expression ';' #ExpressionStatement
    | assignment_statement ';' #AssignmentStatement
    | method_call ';' #MethodCallStatement
    ;

expression
    : expression op=('*' | '/') expression #BinaryOp
    | expression op=('+' | '-') expression #BinaryOp
    | value=LITERAL #Literal
    | value=ID #Identifier
    ;