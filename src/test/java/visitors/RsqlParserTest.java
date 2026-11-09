package visitors;

import ch.nvaria.rsql.RsqlLimits;
import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.internal.RsqlParsing;
import ch.nvaria.rsql.internal.parser.RsqlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // --- limits ---

    private static String nested(int depth) {
        return "(".repeat(depth) + "a==1" + ")".repeat(depth);
    }

    private static String inList(int size) {
        StringBuilder sb = new StringBuilder("genre=in=(");
        for (int i = 0; i < size; i++) sb.append(i == 0 ? "" : ",").append("g").append(i);
        return sb.append(")").toString();
    }

    private static String rejection(String rsql, RsqlLimits limits) {
        return assertThrows(RsqlParseException.class, () -> RsqlParsing.parse(rsql, limits)).getMessage();
    }

    @Test
    void default_limits_accept_ordinary_queries() {
        RsqlParsing.parse(nested(15));
        RsqlParsing.parse("x=in=(" + "a,".repeat(499) + "a)");
        RsqlParsing.parse("name==" + "a".repeat(4090));
    }

    @Test
    void too_long_is_rejected() {
        assertEquals("Query too long: 4097 characters, at most 4096 allowed",
                rejection("name==" + "a".repeat(4091), RsqlLimits.DEFAULTS));
    }

    @Test
    void too_deep_is_rejected() {
        assertEquals("Query nested too deeply: at most 16 levels of parentheses allowed",
                rejection(nested(17), RsqlLimits.DEFAULTS));
        // the list parentheses count as a level
        rejection("(".repeat(16) + "a=in=(1,2)" + ")".repeat(16), RsqlLimits.DEFAULTS);
    }

    @Test
    void deep_nesting_within_length_does_not_overflow_the_stack() {
        // 2000 levels fit into a raised length limit; the depth check must reject before parsing
        String rsql = nested(2000);
        rejection(rsql, RsqlLimits.DEFAULTS.withMaxLength(rsql.length()));
    }

    @Test
    void too_many_list_arguments_are_rejected() {
        RsqlLimits limits = RsqlLimits.DEFAULTS.withMaxInArguments(3);
        RsqlParsing.parse(inList(3), limits);
        assertEquals("Too many arguments for 'genre': at most 3 allowed", rejection(inList(4), limits));
        rejection("name==x;(year==1,genre=out=(a,b,c,d))", limits);
    }

    @Test
    void or_commas_do_not_count_as_list_arguments() {
        RsqlLimits limits = RsqlLimits.DEFAULTS.withMaxInArguments(1);
        RsqlParsing.parse("a==1,b==2,c==3,(d==4,e==5)", limits);
        RsqlParsing.parse("a=in=(1),b=in=(2),c==3", limits);
    }

    @Test
    void custom_limits_can_be_raised() {
        RsqlParsing.parse(nested(30), RsqlLimits.DEFAULTS.withMaxDepth(30));
        RsqlParsing.parse(inList(600), RsqlLimits.DEFAULTS.withMaxInArguments(600));
    }

    @Test
    void limits_must_be_positive() {
        assertThrows(IllegalArgumentException.class, () -> new RsqlLimits(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> RsqlLimits.DEFAULTS.withMaxDepth(-1));
    }

    // --- readable syntax errors ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "``             | 1  | Unexpected end of query at position 1, expected a field name or '('",
            "name==         | 7  | Unexpected end of query at position 7, expected a value or '('",
            "==x            | 1  | Unexpected '==' at position 1, expected a field name or '('",
            "name           | 5  | Unexpected end of query at position 5, expected an operator such as == or =gt=",
            "name x         | 6  | Unexpected 'x' at position 6, expected an operator such as == or =gt=",
            "(name==x       | 9  | Unexpected end of query at position 9, expected ')'",
            "name==x;;a==1  | 9  | Unexpected ';' at position 9, expected a field name or '('",
            "name=='abc     | 7  | Unclosed quote at position 7",
            "name==\"abc    | 7  | Unclosed quote at position 7",
            "name~=x        | 5  | Unexpected character '~' at position 5",
    })
    void syntaxErrorsAreReadable(String rsql, int position, String message) {
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> RsqlParsing.parse(rsql));
        assertEquals(message, e.getMessage());
        assertEquals(position, e.getPosition().orElseThrow());
        assertTrue(e.getSelector().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "name==", "==x", "name==x;", "(name==x", "name==x)", "name=in=(a,b", "name=='x", "a~b"})
    void syntaxErrorsNameNoInternals(String rsql) {
        String message = assertThrows(RsqlParseException.class, () -> RsqlParsing.parse(rsql)).getMessage();
        for (String internal : List.of("UNRESERVED_STR", "COMP_FIQL", "EOF", "<EOF>", "mismatched", "token recognition")) {
            assertFalse(message.contains(internal), message);
        }
    }

    @Test
    void list_limit_error_names_the_selector() {
        RsqlParseException e = assertThrows(RsqlParseException.class,
                () -> RsqlParsing.parse("a==1;genre=in=(x,y,z)", RsqlLimits.DEFAULTS.withMaxInArguments(2)));
        assertEquals("genre", e.getSelector().orElseThrow());
        assertEquals(6, e.getPosition().orElseThrow());
    }

    // --- whitespace and and/or keywords ---

    @ParameterizedTest
    @ValueSource(strings = {
            "a==1 and b==2", "a==1 AND b==2", "a==1 And b==2", "a==1  and  b==2", "a==1\tand\tb==2",
            "a==1\nand b==2", "a==1 and\nb==2", "a==1\r\nAND\r\nb==2", "(a==1) and (b==2)", "a == 1 ; b == 2",
            "a==1\n;\nb==2"})
    void andInAnyCaseAndWhitespace(String rsql) {
        RsqlParser.InputContext tree = RsqlParsing.parse(rsql);
        assertThat(tree.or().and(), hasSize(1));
        assertThat(tree.or().and(0).constraint(), hasSize(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a==1 or b==2", "a==1 OR b==2", "a==1\nOr\nb==2", "a==1\t,\tb==2"})
    void orInAnyCaseAndWhitespace(String rsql) {
        assertThat(RsqlParsing.parse(rsql).or().and(), hasSize(2));
    }

    @Test
    void line_breaks_are_not_part_of_unquoted_values() {
        RsqlParser.InputContext tree = RsqlParsing.parse("name==Inception\r\n;year==2010");
        assertThat(tree.or().and(0).constraint(0).comparison().arguments().getText(), is("Inception"));
    }

    @Test
    void line_breaks_inside_quotes_are_kept() {
        RsqlParser.InputContext tree = RsqlParsing.parse("name=='two\nlines'");
        assertThat(tree.or().and(0).constraint(0).comparison().arguments().getText(), is("'two\nlines'"));
    }

    @Test
    void words_containing_and_or_or_are_values() {
        RsqlParser.InputContext tree = RsqlParsing.parse("brand==android;org==oracle");
        assertThat(tree.or().and(0).constraint(), hasSize(2));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "(a==1)and(b==2)  | Unexpected 'and' at position 7, expected end of query",
            "a==1 AND         | Unexpected 'AND' at position 6, expected end of query",
            "a==1 and ;b==2   | Unexpected ';' at position 10, expected a field name or '('",
    })
    void keywordErrorsPointAtTheWord(String rsql, String message) {
        assertEquals(message, assertThrows(RsqlParseException.class, () -> RsqlParsing.parse(rsql)).getMessage());
    }

    @Test
    void keywordErrorAfterLineBreakPointsAtTheWord() {
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> RsqlParsing.parse("a==1 \n and"));
        assertEquals("Unexpected 'and' at position 8, expected end of query", e.getMessage());
    }
}
