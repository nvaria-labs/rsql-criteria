package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.RsqlParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeCoercionEnumTest {

    enum Genre { DRAMA, SCI_FI }

    /** Constants that differ only in case: only exact names are unambiguous. */
    enum Mode { fast, FAST, slow }

    @Test
    void exact_name() {
        assertEquals(Genre.DRAMA, TypeCoercion.coerce("DRAMA", Genre.class));
    }

    @Test
    void name_ignoring_case() {
        assertEquals(Genre.DRAMA, TypeCoercion.coerce("drama", Genre.class));
        assertEquals(Genre.SCI_FI, TypeCoercion.coerce("Sci_Fi", Genre.class));
    }

    @Test
    void exact_match_wins_over_case_insensitive_one() {
        assertEquals(Mode.fast, TypeCoercion.coerce("fast", Mode.class));
        assertEquals(Mode.FAST, TypeCoercion.coerce("FAST", Mode.class));
        assertEquals(Mode.slow, TypeCoercion.coerce("SLOW", Mode.class));
    }

    @Test
    void ambiguous_name_is_rejected() {
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> TypeCoercion.coerce("Fast", Mode.class));
        assertEquals("Cannot coerce 'Fast' to Mode", e.getMessage());
    }

    @Test
    void unknown_name_is_rejected() {
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> TypeCoercion.coerce("comedy", Genre.class));
        assertEquals("Cannot coerce 'comedy' to Genre", e.getMessage());
    }
}
