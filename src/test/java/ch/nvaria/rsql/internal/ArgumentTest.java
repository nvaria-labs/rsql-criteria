package ch.nvaria.rsql.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ArgumentTest {

    // Arguments as they appear in the query; '|' separates the CSV columns
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            // query text       | value()    | likePattern()   | wildcard | null
            "Nolan              | Nolan      | Nolan           | false    | false",
            "'O\\'Brien'        | O'Brien    | O'Brien         | false    | false",
            "\"Kill \\\"Bill\\\"\" | Kill \"Bill\" | Kill \"Bill\" | false | false",
            "'a\\\\b'           | a\\b       | a\\\\b          | false    | false",
            "Que*Tarantino      | Que*Tarantino | Que%Tarantino | true    | false",
            "'50%*'             | 50%*       | 50\\%%          | true     | false",
            "'a_b*'             | a_b*       | a\\_b%          | true     | false",
            "*\\**              | ***        | %*%             | true     | false",
            "\\*                | *          | *               | false    | false",
            "null               | null       | null            | false    | true",
            "NULL               | NULL       | NULL            | false    | true",
            "'null'             | null       | null            | false    | false",
            "\"null\"           | null       | null            | false    | false",
    })
    void escapesWildcardsAndNull(String text, String value, String like, boolean wildcard, boolean isNull) {
        Argument arg = Argument.of(text);
        assertThat(arg.value(), is(value));
        assertThat(arg.likePattern(), is(like));
        assertThat(arg.hasWildcard(), is(wildcard));
        assertThat(arg.isNull(), is(isNull));
    }
}
