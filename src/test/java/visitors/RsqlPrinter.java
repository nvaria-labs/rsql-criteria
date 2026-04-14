package visitors;


import ch.nvaria.generated.RsqlParser;
import ch.nvaria.generated.RsqlParserBaseListener;
import org.antlr.v4.runtime.RuleContext;


public  class RsqlPrinter extends RsqlParserBaseListener {


    public void enterAnd(RsqlParser.AndContext ctx) {
        String id = ctx.AND().getFirst().getText(); // prop : ID '=' STRING '\n' ;

        System.out.println(id);
      }
}