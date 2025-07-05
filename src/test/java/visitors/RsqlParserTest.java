/*
 * Copyright 2024 Nvaria GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
