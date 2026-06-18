grammar InvertedIndexQuery;

query       : orExpr EOF ;

orExpr      : andExpr (OR andExpr)* ;

andExpr     : unaryExpr ((AND | NOT) unaryExpr)* ;

unaryExpr   : NOT unaryExpr    # notExpr
            | nearExpr         # passNear
            ;

nearExpr    : adjExpr (NEAR_OP adjExpr)* ;

adjExpr     : primary (ADJ primary)* ;

primary     : TERM                         # termPrimary
            | PHRASE                       # phrasePrimary
            | LPAREN orExpr RPAREN         # parenExpr
            ;

// Lexer rules
NEAR_OP     : 'NEAR/' [0-9]+ ;
ADJ         : 'ADJ' ;
AND         : 'AND' ;
OR          : 'OR' ;
NOT         : 'NOT' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
PHRASE      : '"' ~["]+ '"' ;
TERM        : [a-zA-Z0-9\u0400-\u04FF]+ ;  // Latin + Cyrillic + digits
WS          : [ \t\r\n]+ -> skip ;
