lexer grammar RsqlLexer;

SKIP0 : ' ' -> skip ;
SKIP1 : '\t' -> skip ;
fragment ALPHA : [a-zA-Z]  ;
fragment ESCAPED_CHAR : '\\' .  ;
UNRESERVED_STR : (~["'();,=<>!~ ])+  ;
SINGLE_QUOTED_STR : '\'' ((ESCAPED_CHAR | ~['\\]))* '\''  ;
DOUBLE_QUOTED_STR : '"' ((ESCAPED_CHAR | ~["\\]))* '"'  ;
AND : (';' | ' and ')  ;
OR : (',' | ' or ')  ;
LPAREN : '('  ;
RPAREN : ')'  ;
COMP_FIQL : ('=' (ALPHA)* | '!') '='  ;
COMP_ALT : ('>' | '<') ('=')?  ;
