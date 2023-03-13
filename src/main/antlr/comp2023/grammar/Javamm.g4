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
LITERAL: ( NULL_LITERAL | NUMBER_LITERAL | BOOLEAN_LITERAL | CHAR_LITERAL | STRING_LITERAL /* | ARRAY_LITERAL */ ) ;
NULL_LITERAL: 'null' ;
NUMBER_LITERAL: INTEGER | FLOAT;
BOOLEAN_LITERAL: ( 'false' | 'true' ) ;
STRING_LITERAL: '"' ( TEXT_CHAR | '\'' )* '"' ;
CHAR_LITERAL: '\'' ( TEXT_CHAR | '"' ) '\'' ;
// ARRAY_LITERAL: LB ( LITERAL ( ',' LITERAL )* )? RB ;

// these are keywords, should take precedence
MODIFIER: ( 'public' | 'private' | 'protected' | 'static' | 'final' | 'abstract' ) ;

INTEGER : '0' OCT_DIGIT+ | DIGIT+ | '0x' HEX_DIGIT+ | '0b' BIN_DIGIT+ ;
FLOAT : ( DIGIT+ '.' DIGIT* | DIGIT* '.' DIGIT+ ) ( [eE] DIGIT+ )?;
fragment ESCAPED_CHAR : '\\' ( [tbnrfs'"\\] | 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT | OCT_DIGIT OCT_DIGIT? OCT_DIGIT?);
fragment TEXT_CHAR : ~['"\\\r\n\f] | ESCAPED_CHAR ;

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
fragment OCT_DIGIT : [0-7] ;
fragment LETTER : [a-zA-Z] ;
fragment UNDERSCORE : '_' ;
fragment DOLLAR : '$' ;


program
    : ( import_statement )* class_declaration EOF
    ;

import_statement : 'import' ID ( '.' ID )* ';' #ImportStatement ;

class_declaration : 'class' className=ID ( 'extends' parentClass=ID )? '{' program_definition '}' #ClassDeclaration ;

program_definition : ( variable_declaration | method_declaration )* ;

variable_declaration: (modifiers+=MODIFIER)* assignment_statement ';' ; // TODO: check if this could be better

method_declaration: (modifiers+=MODIFIER)* type methodName=ID '(' parameter_list? ')' '{' statement* '}';

parameter_list : type argName+=ID ( ',' type argName+=ID )* ;
argument_list : expression ( ',' expression )* ;

assignment_statement: type id=ID ( op='=' expression )? ; // TODO: there might be edge cases with this

statement
    : 'if' '(' expression ')' statement ( 'else' statement )? #IfStatement
    | 'while' '(' expression ')' statement #WhileStatement
    | 'do' statement 'while' '(' expression ')' ';' #DoStatement
    | 'for' '(' expression? ';' expression? ';' expression? ')' statement #ForStatement
    | 'for' '(' type id=ID ':' expression ')' statement #ForEachStatement
    | 'switch' '(' expression ')' '{' case_statement* '}' #SwitchStatement
    | 'return' expression? ';' #ReturnStatement
    | 'break' ';' #BreakStatement
    | 'continue' ';' #ContinueStatement
    | '{' statement* '}' #StatementBlock
    | expression ';' #ExpressionStatement
    | variable_declaration #AssignmentStatement // TODO: ew
    ;

case_statement
    : 'case' val=LITERAL ':' statement* #CaseStatement
    | 'default' ':' statement* #DefaultStatement
    ;

type
    : id=ID #SimpleType
    | type '[' ']' #Array
    ;

expression
    : '(' expression ')' #ExplicitPriority
    | 'new' id=ID '(' argument_list? ')' #NewObject
    | 'new' id=ID '[' expression ']' #NewArray
    | expression '.' member=ID '(' argument_list? ')' #MethodCall
    | expression '.' member=ID #PropertyAccess
    | expression '[' expression ']' #ArrayAccess
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
    | expression '?' expression ':' expression #TernaryOp
    | expression op=('=' | '+=' | '-=' | '*=' | '/=' | '%=' | '<<=' | '>>=' | '>>>=') expression #AssignmentExpression
    | value=LITERAL #LiteralExpression
    | id=ID #IdentifierExpression
    ;