grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

DIGIT : [0-9] ;
LETTER : [a-zA-Z] ;
UNDERSCORE : '_' ;
DOLLAR : '$' ;
SYMBOL : ([!"#%&/()=?'.:,;\\|] | DOLLAR | UNDERSCORE) ;

EOL : [\n\r] ;
WHITE_SPACE : [ \t\f] ;

COMMENT_BODY : (LETTER | DIGIT | SYMBOL | WHITE_SPACE)+ ;

INTEGER : DIGIT+ ;
ID : (LETTER | UNDERSCORE | DOLLAR)(LETTER | UNDERSCORE | DIGIT | DOLLAR)* ;

MULTI_LINE_COMMENT : '/*' (COMMENT_BODY | EOL)* '*/' -> skip ;
SINGLE_LINE_COMMENT : '//' COMMENT_BODY EOL -> skip;

WS : (EOL | WHITE_SPACE)+ -> skip;

IMPORT_ID : ID('.'ID)* ;

program
    : import_statement* class_declaration EOF
    | statement+ EOF
    ;

import_statement : 'import' IMPORT_ID ';' ;

class_declaration : 'class' ID ( 'extends' ID )? '{' program_definition '}';

program_definition : ( variable_declaration | method_declaration )* ;

variable_declaration: id=ID op='=' value=expression;

method_declaration
    : 'public static void main(String[] args) {' statement* '}' #MainMethod
    ;

statement
    : expression ';'
    | ID '=' INTEGER ';'
    ;

expression
    : expression op=('*' | '/') expression #BinaryOp
    | expression op=('+' | '-') expression #BinaryOp
    | value=INTEGER #Integer
    | value=ID #Identifier
    ;
