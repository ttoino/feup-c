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
LITERAL: ( NUMBER_LITERAL | BOOLEAN_LITERAL | CHAR_LITERAL | STRING_LITERAL ) ;
NUMBER_LITERAL: INTEGER | FLOAT;
BOOLEAN_LITERAL: ( 'false' | 'true' ) ;
STRING_LITERAL: '"' ( TEXT_CHAR | '\'' )* '"' ;
CHAR_LITERAL: '\'' ( TEXT_CHAR | '"' ) '\'' ;

// these are keywords, should take precedence
ACCESS_MODIFIER: ( 'public' | 'private' | 'protected' ) ;
NON_ACCESS_MODIFIER: ( 'static' | 'final' | 'abstract' ) ; // TODO: may need to change this when we create method local variables

TYPE: ( 'int' | 'long' | 'float' | 'short' | 'byte' | 'char' | 'boolean' | 'String' ) ( '[' ']' )?;

INTEGER : DIGIT+ | '0x' HEX_DIGIT+ | '0b' BIN_DIGIT ;
FLOAT : ( DIGIT+ '.' (DIGIT+)? | (DIGIT+)? '.' DIGIT+ ) ( [eE] DIGIT+ )?;
fragment ESCAPED_CHAR : '\\' ( [tbnrf'"\\] | 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT);
fragment TEXT_CHAR : ~['"\\] | ESCAPED_CHAR ;

fragment SYMBOL : ( SPECIAL_CHARS | DOLLAR | UNDERSCORE ) ;

// IDs in the program (variable/method/class names)
ID : ( LETTER | UNDERSCORE | DOLLAR )( LETTER | UNDERSCORE | DIGIT | DOLLAR )* ;

// white-space and new-line characters
EOL : [\n\r] ;
WHITE_SPACE : [ \t\f] ;

// basic characters
fragment SPECIAL_CHARS: [!"#%&()=?'.:,;\\|] ;
fragment DIGIT : [0-9] ;
fragment HEX_DIGIT : [0-9a-fA-F] ;
fragment BIN_DIGIT : [01] ;
fragment LETTER : [a-zA-Z] ;
fragment UNDERSCORE : '_' ;
fragment DOLLAR : '$' ;

program
    : ( import_statement )* class_declaration EOF
    ;

import_statement : 'import' ID ( '.' ID )* ';' #ImportStatement ;

class_declaration : 'class' className=ID ( 'extends' parentClass=ID )? '{' program_definition '}' #ClassDeclaration ;

program_definition : ( variable_declaration | method_declaration )* ;

variable_declaration: accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER* assignment_statement ';' ; // TODO: check if this could be better

method_declaration: accessModifier=ACCESS_MODIFIER? NON_ACCESS_MODIFIER* simple_method_declaration;

simple_method_declaration
    : returnType=TYPE methodName=ID '(' parameter_list? ')' '{' statement* ( 'return' returnValue=( ID | LITERAL ) ';' )? '}' #Method
    | 'void' methodName=ID '(' parameter_list? ')' '{' statement* ( 'return' ';' )? '}' #VoidMethod
    ;

method_call
    : method_call '.' method_call
    | ( ID ( '.' ID )? '.' )? methodName=ID '(' argument_list? ')'
    ;

parameter_list : argType+=TYPE argName+=ID ( ',' argType+=TYPE argName+=ID )* ;
argument_list : argName+=ID ( ',' argName+=ID )* ;

assignment_statement: varType=TYPE id=ID ( op='=' expression )? ; // TODO: there might be edge cases with this

statement
    : expression ';' #ExpressionStatement
    | variable_declaration #AssignmentStatement // TODO: ew
    | method_call ';' #MethodCallStatement
    | '{' statement* '}' #StatementBlock
    | 'if' '(' expression ')' statement ( 'else' statement )? #IfStatement
    | 'while' '(' expression ')' statement #WhileStatement
    | 'do' statement 'while' '(' expression ')' ';' #DoStatement
    | 'for' '(' expression? ';' expression? ';' expression? ')' statement #ForStatement
    | 'for' '(' varType=TYPE id=ID ':' expression ')' statement #ForEachStatement
    | 'switch' '(' expression ')' '{' case_statement* '}' #SwitchStatement
    ;

case_statement
    : 'case' val=LITERAL ':' statement* #CaseStatement
    | 'default' ':' statement* #DefaultStatement
    ;

expression
    : '(' expression ')' #ExplicitPriority
    | op=('!' | '++' | '--' | '~') expression #UnaryOp
    | expression op=('++' | '--') #UnaryOp
    | expression op=('*' | '/' | '%') expression #BinaryOp
    | expression op=('+' | '-') expression #BinaryOp
    | expression op=('<<' | '>>' | '>>>') expression #BinaryOp
    | expression op=('>' | '<' | '>=' | '<=') expression #BinaryOp
    | expression op=('==' | '!=') expression #BinaryOp
    | expression op='&' expression #BinaryOp
    | expression op='|' expression #BinaryOp
    | expression op='&&' expression #BinaryOp
    | expression op='||' expression #BinaryOp
    | expression op='?=' expression #BinaryOp
    | id=ID op=('=' | '+=' | '-=' | '*=' | '/=' | '%=') expression #AssignmentExpression
    | value=LITERAL #Literal
    | id=ID #Identifier
    ;