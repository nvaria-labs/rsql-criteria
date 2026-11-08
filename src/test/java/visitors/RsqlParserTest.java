/*
 * Copyright 2024-2026 Nvaria GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package visitors;

import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.internal.RsqlParsing;
import ch.nvaria.rsql.internal.parser.RsqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Grammar-level tests: syntax only, no JPA. */
class RsqlParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "name==\"Kill Bill\";year=gt=2003",
            "name=='Kill Bill' and year>2003",
            "genres=in=(sci-fi,action);(director=='Christopher Nolan',actor==*Bale);year=ge=2000",
            "genres=in=(sci-fi,action) and (director=='Christopher Nolan' or actor==*Bale) and year>=2000",
            "director.lastName==Nolan;year=ge=2000;year=lt=2010",
            "director.lastName==Nolan and year>=2000 and year<2010",
            "genres=in=(sci-fi,action);genres=out=(romance,animated,horror),director==Que*Tarantino",
            "genres=in=(sci-fi,action) and genres=out=(romance,animated,horror) or director==Que*Tarantino",
    })
    void validQueriesParse(String rsql) {
        RsqlParsing.parse(rsql);
    }

    @Test
    void andBindsTighterThanOr() {
        RsqlParser.InputContext tree = RsqlParsing.parse("a==1;b==2,c==3");
        assertThat(tree.or().and(), hasSize(2));
        assertThat(tree.or().and(0).constraint(), hasSize(2));
        assertThat(tree.or().and(1).constraint(), hasSize(1));
    }

    @Test
    void keywordAndIsEquivalentToSemicolon() {
        RsqlParser.InputContext tree = RsqlParsing.parse("director.lastName==Nolan and year>=2000 and year<2010");
        assertThat(tree.or().and(), hasSize(1));
        assertThat(tree.or().and(0).constraint(), hasSize(3));
        assertThat(tree.or().and(0).constraint(2).comparison().operator().getText(), is("<"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "name==", "==x", "name==x;", "(name==x", "name==x)", "name=in=(a,b"})
    void malformedQueriesAreRejected(String rsql) {
        assertThrows(RsqlParseException.class, () -> RsqlParsing.parse(rsql));
    }
}
