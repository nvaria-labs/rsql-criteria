package visitors;

import ch.nvaria.generated.RsqlLexer;
import ch.nvaria.generated.RsqlParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;
import visitor.JPACriteriaRsqlVistor;

class RsqlParserTest {

    public static final String testString1 = "name==\"Kill Bill\";year=gt=2003";
    public static final String testString2 = "name=='Kill Bill' and year>2003";
    public static final String testString3 = "genres=in=(sci-fi,action);(director=='Christopher Nolan',actor==*Bale);year=ge=2000";
    public static final String testString4 = "genres=in=(sci-fi,action) and (director=='Christopher Nolan' or actor==*Bale) and year>=2000";
    public static final String testString5 = "director.lastName==Nolan;year=ge=2000;year=lt=2010";
    public static final String testString6 = "director.lastName==Nolan and year>=2000 and year<2010";
    public static final String testString7 = "genres=in=(sci-fi,action);genres=out=(romance,animated,horror),director==Que*Tarantino";
    public static final String testString8 = "genres=in=(sci-fi,action) and genres=out=(romance,animated,horror) or director==Que*Tarantino";

    @Test
    void parseStringTest(){
        CodePointCharStream rsqlcharstream =  CharStreams.fromString(testString6);
        Lexer lexer = new RsqlLexer(rsqlcharstream);
        TokenStream tokenStream = new CommonTokenStream(lexer);
        RsqlParser parser = new RsqlParser(tokenStream);
        ParseTree parseTree = parser.and();
        String result =   new JPACriteriaRsqlVistor().visit(parseTree);

        System.out.println(result);
    }
}
