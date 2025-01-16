parser grammar RsqlParser;

options { tokenVocab=RsqlLexer; }

input : or EOF  ;

or : and (OR and)*  ;

and : constraint (AND constraint)*  ;

constraint : (group | comparison)  ;

group : LPAREN or RPAREN  ;

comparison : selector operator arguments  ;

selector : UNRESERVED_STR  ;

operator : (COMP_FIQL | COMP_ALT)  ;

arguments : (LPAREN commaSepArguments RPAREN | argument)  ;

commaSepArguments : argument (OR argument)*  ;

argument : (UNRESERVED_STR | (DOUBLE_QUOTED_STR | SINGLE_QUOTED_STR))  ;
