parser grammar RsqlParser;

options { tokenVocab=RsqlLexer; }

input : or EOF  ;

or : and ((OR | COMMA) and)*  ;

and : constraint ((AND | SEMICOLON) constraint)*  ;

constraint : (group | comparison)  ;

group : LPAREN or RPAREN  ;

comparison : selector operator arguments  ;

selector : UNRESERVED_STR  ;

operator : (COMP_FIQL | COMP_ALT)  ;

arguments : (LPAREN commaSepArguments RPAREN | argument)  ;

commaSepArguments : argument (COMMA argument)*  ;

argument : (UNRESERVED_STR | (DOUBLE_QUOTED_STR | SINGLE_QUOTED_STR))  ;
