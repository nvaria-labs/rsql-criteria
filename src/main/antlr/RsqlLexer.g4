lexer grammar RsqlLexer;

// Whitespace: blanks, tabs and line breaks. Skipped between tokens, never part of an unquoted value.
fragment WS : [ \t\r\n] ;
// 'and' / 'or' in any case, with whitespace on both sides. Longer than a single skipped
// whitespace character, so the lexer prefers them where they fit.
AND : WS+ [aA][nN][dD] WS+  ;
OR : WS+ [oO][rR] WS+  ;
WHITESPACE : WS -> skip ;
fragment ALPHA : [a-zA-Z]  ;
fragment ESCAPED_CHAR : '\\' .  ;
UNRESERVED_STR : (~["'();,=<>!~ \t\r\n])+  ;
SINGLE_QUOTED_STR : '\'' ((ESCAPED_CHAR | ~['\\]))* '\''  ;
DOUBLE_QUOTED_STR : '"' ((ESCAPED_CHAR | ~["\\]))* '"'  ;
SEMICOLON : ';'  ;
COMMA : ','  ;
LPAREN : '('  ;
RPAREN : ')'  ;
COMP_FIQL : ('=' (ALPHA)* | '!') '='  ;
COMP_ALT : ('>' | '<') ('=')?  ;
