grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : [0-9]+ ;
ID : [a-zA-Z_][a-zA-Z_0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program: (importDeclaration)* classDeclaration EOF ;

importDeclaration: 'import' ID ('.' ID)* ';' ;
classDeclaration: 'class' ID ( 'extends' ID )? '{' ( varDeclaration )* ( methodDeclaration )* '}' ;

varDeclaration: type ID ';' ;
methodDeclaration
    : 'public' type ID '(' ( type ID ( '.' type ID )* )? ')' '{' ( varDeclaration )* '}'
    | 'public' 'static' 'void' 'main' '(' 'String' '[' ']' ID  ')' '{' ( varDeclaration )* ( statement )* '}'
    ;

type
    : 'int' '[' ']'
    | 'boolean'
    | 'int'
    | ID
    ;

statement
    : '{' ( statement )* '}'
    | 'if' '(' expression ')' statement 'else' statement
    | 'while' '(' expression ')' statement
    | expression ';'
    | ID '=' expression ';'
    | ID '[' expression ']' '=' expression ';'
    ;

expression
    : expression ( '&&' | '<' | '+' | '-' | '*' | '/' ) expression
    | expression '[' expression ']'
    | expression '.' 'length'
    | expression '.' ID  '(' ( expression ( ',' expression )* )? ')'
    | 'new' 'int' '[' expression ']'
    | 'new' ID '(' ')'
    | '!' expression
    | '(' expression ')'
    | INTEGER
    | 'true'
    | 'false'
    | ID
    | 'this'
    ;

// DIGIT : [0-9] ;
// LETTER : [a-zA-Z] ;
// UNDERSCORE : '_' ;
// DOLLAR : '$' ;
// SPECIAL_CHARS: [!"#%&/()=?'.:,;\\|] ; // TODO: handle escapes
// SYMBOL : ( SPECIAL_CHARS | DOLLAR | UNDERSCORE) ;
//
// EOL : [\n\r] ;
// WHITE_SPACE : [ \t\f] ;
//
// TEXT : (LETTER | DIGIT | SYMBOL | WHITE_SPACE)+ ;
//
// NUMBER : DIGIT+ ;
// ID : (LETTER | UNDERSCORE | DOLLAR)(LETTER | UNDERSCORE | DIGIT | DOLLAR)* ;
//
// IMPORT_ID : ID('.'ID)* ;
//
// ACCESS_MODIFIER: ( 'public' | 'private' | 'protected' ) ;
// NON_ACCESS_MODIFIER: ( 'static' | 'final' | 'abstract' ) ; // TODO: may need to change this when create method local variables
//
// NUMBER_LITERAL: NUMBER ;
// BOOLEAN_LITERAL: ( 'false' | 'true' ) ;
// STRING_LITERAL: '"' TEXT '"' ; // TODO: handle double quote
// CHAR_LITERAL: '\'' ( SYMBOL | EOL | WHITE_SPACE ) '\'' ; // TODO: handle single quote
//
// LITERAL: ( NUMBER_LITERAL | BOOLEAN_LITERAL | STRING_LITERAL ) ;
//
// ID_OR_LITERAL: ( ID | LITERAL ) ;
//
// TYPE: ( 'int' | 'long' | 'short' | 'byte' | 'char' | 'boolean' | 'String' ) ;
//
// MULTI_LINE_COMMENT : '/*' (TEXT | EOL)* '*/' -> skip ;
// SINGLE_LINE_COMMENT : '//' TEXT EOL -> skip;
//
// WS : [ \t\n\r\f]+ -> skip ;
//
// program
//     : ( import_statement )* class_declaration EOF
//     ;
//
// import_statement : 'import' IMPORT_ID ';' #ImportStatement ;
//
// class_declaration : 'class' className=ID ( 'extends' parentClass=ID )? '{' program_definition '}' #ClassDeclaration ;
//
// program_definition : ( variable_declaration | method_declaration )* ;
//
// variable_declaration: accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER* assignment_statement;
//
// method_declaration
//     : accessModifier='public' 'static' returnType='void' methodName='main' '(' argType='String[]' argName='args' ')' '{' statement* '}' #MainMethod
//     | accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER* returnType='void' methodName=ID '(' argument_list ')' '{' statement* ( 'return' ';' )? '}' #VoidMethod
//     | accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER* returnType=TYPE methodName=ID '(' argument_list ')' '{' statement* ( 'return' returnValue=ID_OR_LITERAL ';' )? '}' #Method
//     ;
//
// argument_list
//     : argType=TYPE argName=ID ', ' argument_list
//     | argType=TYPE argName=ID
//     ;
//
// assignment_statement: id=ID op='=' value=statement ; // TODO: there might be edge cases with this
//
// statement
//     : expression ';'
//     | assignment_statement ';'
//     ;
//
// expression
//     : expression op=('*' | '/') expression #BinaryOp
//     | expression op=('+' | '-') expression #BinaryOp
//     | value=NUMBER #Integer
//     | value=ID #Identifier
//     ;
//