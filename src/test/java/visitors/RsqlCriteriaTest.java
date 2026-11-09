package visitors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ch.nvaria.rsql.RsqlLimits;
import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.RsqlToCriteria;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsqlCriteriaTest {

    static EntityManagerFactory emf;
    static CriteriaBuilder cb;

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("test-pu");
        cb = emf.getCriteriaBuilder();

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        Director nolan = new Director("Christopher", "Nolan");
        Director tarantino = new Director("Quentin", "Tarantino");
        em.persist(nolan);
        em.persist(tarantino);

        Movie darkKnight = new Movie("The Dark Knight", 2008, "action", "Christian Bale", nolan);
        darkKnight.setReleaseDate(LocalDate.of(2008, 7, 18));
        Movie inception = new Movie("Inception", 2010, "sci-fi", "Leonardo DiCaprio", nolan);
        inception.setReleaseDate(LocalDate.of(2010, 7, 16));
        inception.setFeatured(true);
        em.persist(darkKnight);
        em.persist(inception);
        em.persist(new Movie("Interstellar",     2014, "sci-fi",  "Matthew McConaughey",   nolan));
        em.persist(new Movie("Pulp Fiction",     1994, "crime",   "John Travolta",         tarantino));
        em.persist(new Movie("Kill Bill",        2003, "action",  "Uma Thurman",           tarantino));

        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    // --- helper ---

    private List<Movie> query(String rsql) {
        EntityManager em = emf.createEntityManager();
        CriteriaQuery<Movie> cq = cb.createQuery(Movie.class);
        Root<Movie> root = cq.from(Movie.class);
        Predicate predicate = RsqlToCriteria.parse(rsql, cb, root);
        cq.where(predicate);
        TypedQuery<Movie> q = em.createQuery(cq);
        List<Movie> results = q.getResultList();
        em.close();
        return results;
    }

    // --- equality ---

    @Test
    void eq_string() {
        assertThat(query("genre==action"), hasSize(2));
    }

    @Test
    void eq_quotedString() {
        assertThat(query("name==\"Kill Bill\""), hasSize(1));
        assertThat(query("name=='Kill Bill'"), hasSize(1));
    }

    @Test
    void neq_string() {
        assertThat(query("genre!=action"), hasSize(3));
    }

    // --- comparisons ---

    @Test
    void gt_int() {
        assertThat(query("year=gt=2003"), hasSize(3));
    }

    @Test
    void ge_int() {
        assertThat(query("year=ge=2003"), hasSize(4));
    }

    @Test
    void lt_int() {
        assertThat(query("year=lt=2003"), hasSize(1));
    }

    @Test
    void le_int() {
        assertThat(query("year=le=2003"), hasSize(2));
    }

    @Test
    void altOperator_gt() {
        assertThat(query("year>2003"), hasSize(3));
    }

    @Test
    void altOperator_ge() {
        String rsql = "year>=2003";
        assertThat(query(rsql), hasSize(4));
    }

    @Test
    void altOperator_lt() {
        assertThat(query("year<2003"), hasSize(1));
    }

    @Test
    void altOperator_le() {
        assertThat(query("year<=2003"), hasSize(2));
    }

    // --- IN / OUT ---

    @Test
    void in_multiValue() {
        assertThat(query("genre=in=(sci-fi,action)"), hasSize(4));
    }

    @Test
    void out_multiValue() {
        assertThat(query("genre=out=(sci-fi,action)"), hasSize(1));
    }

    // --- wildcards ---

    @Test
    void eq_wildcardSuffix() {
        assertThat(query("actor==*Bale"), hasSize(1));
    }

    @Test
    void eq_wildcardInfix() {
        assertThat(query("name==*Dark*"), hasSize(1));
    }

    @Test
    void neq_wildcard() {
        // NOT LIKE 'act%' excludes action (2 movies), keeps sci-fi (2) and crime (1)
        assertThat(query("genre!=act*"), hasSize(3));
    }

    // --- AND ---

    @Test
    void and_semicolon() {
        assertThat(query("director.lastName==Nolan;year=ge=2010"), hasSize(2));

    }

    @Test
    void and_keyword() {
        assertThat(query("director.lastName==Nolan and year>=2010"), hasSize(2));
    }

    @Test
    void and_multipleConstraints() {
        // Dark Knight 2008, genre action, Nolan
        assertThat(query("director.lastName==Nolan;year=gt=2007;genre==action"), hasSize(1));
    }

    // --- OR ---

    @Test
    void or_comma() {
        // Nolan (3) OR Tarantino (2) = all 5
        assertThat(query("director.lastName==Nolan,director.lastName==Tarantino"), hasSize(5));
    }

    @Test
    void or_keyword() {
        assertThat(query("director.lastName==Nolan or director.lastName==Tarantino"), hasSize(5));
    }

    // --- dot notation ---

    @Test
    void dotNotation_lastName() {
        assertThat(query("director.lastName==Nolan"), hasSize(3));
    }

    @Test
    void dotNotation_firstName() {
        assertThat(query("director.firstName==Quentin"), hasSize(2));
    }

    // --- group ---

    @Test
    void group_orInsideAnd() {
        // genre=in=(sci-fi,action) AND (director.lastName==Nolan OR year<2000)
        // sci-fi/action movies: Dark Knight(Nolan), Inception(Nolan), Interstellar(Nolan), Kill Bill(Tarantino,2003)
        // of those: Nolan=3, year<2000=0 → 3 movies
        assertThat(query("genre=in=(sci-fi,action);(director.lastName==Nolan,year<2000)"), hasSize(3));
    }

    // --- combined ---

    @Test
    void combined_testString1_equivalent() {
        // name=="Kill Bill";year=gt=2000
        assertThat(query("name==\"Kill Bill\";year=gt=2000"), hasSize(1));
    }

    @Test
    void combined_testString5_equivalent() {
        // director.lastName==Nolan;year=ge=2008;year=lt=2012
        assertThat(query("director.lastName==Nolan;year=ge=2008;year=lt=2012"), hasSize(2));
    }

    @Test
    void combined_inAndOut() {
        // genre=in=(sci-fi,action);genre=out=(action)  → only sci-fi: 2
        assertThat(query("genre=in=(sci-fi,action);genre=out=(action)"), hasSize(2));
    }

    // --- null ---

    @Test
    void eq_null_returnsRowsWithNullField() {
        // 3 movies have no releaseDate set
        assertThat(query("releaseDate==null"), hasSize(3));
    }

    @Test
    void neq_null_returnsRowsWithNonNullField() {
        // 2 movies have releaseDate set
        assertThat(query("releaseDate!=null"), hasSize(2));
    }

    @Test
    void null_caseInsensitive() {
        assertThat(query("releaseDate==NULL"), hasSize(3));
    }

    // --- error handling ---

    @Test
    void unknownOperator_throws() {
        assertThrows(RsqlParseException.class, () -> query("year=foo=2003"));
    }

    // --- joins ---

    @Test
    void selectorsResolveAgainstAJoin() {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<Movie> cq = cb.createQuery(Movie.class);
            Root<Movie> root = cq.from(Movie.class);
            Join<Movie, Director> director = root.join("director");
            cq.where(cb.and(
                    RsqlToCriteria.parse("lastName==Nolan", cb, director),
                    RsqlToCriteria.parse("year=lt=2012", cb, root)));
            assertThat(em.createQuery(cq).getResultList(), hasSize(2));
        } finally {
            em.close();
        }
    }

    @Test
    void unknownFieldOnAJoin_throws() {
        CriteriaQuery<Movie> cq = cb.createQuery(Movie.class);
        Join<Movie, Director> director = cq.from(Movie.class).join("director");
        assertThrows(RsqlParseException.class, () -> RsqlToCriteria.parse("year==2008", cb, director));
    }

    @Test
    void syntaxError_throws() {
        assertThrows(RsqlParseException.class, () -> query("==broken"));
    }

    @Test
    void unknownField_throws() {
        assertThrows(RsqlParseException.class, () -> query("nonExistentField==value"));
    }

    @Test
    void unknownNestedField_throws() {
        assertThrows(RsqlParseException.class, () -> query("director.nonExistent==value"));
    }

    // --- predicate is not null ---

    @Test
    void returnsNonNullPredicate() {
        CriteriaQuery<Movie> cq = cb.createQuery(Movie.class);
        Root<Movie> root = cq.from(Movie.class);
        Predicate p = RsqlToCriteria.parse("year=gt=2000", cb, root);
        assertNotNull(p);
    }

    // --- booleans ---

    @Test
    void boolean_true_and_false() {
        assertThat(query("featured==true"), hasSize(1));
        assertThat(query("featured==FALSE"), hasSize(4));
        assertThat(query("featured!=true"), hasSize(4));
    }

    @Test
    void boolean_rejects_other_values() {
        assertThrows(RsqlParseException.class, () -> query("featured==yes"));
        assertThrows(RsqlParseException.class, () -> query("featured==1"));
        assertThrows(RsqlParseException.class, () -> query("featured=in=(true,maybe)"));
    }

    // --- paths that cannot be navigated ---

    @Test
    void collection_attribute_is_rejected() {
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> query("tags==classic"));
        assertEquals("Field 'tags' on Movie is a collection and cannot be used", e.getMessage());
    }

    @Test
    void path_through_collection_is_rejected() {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<Director> cq = cb.createQuery(Director.class);
            Root<Director> director = cq.from(Director.class);
            RsqlParseException e = assertThrows(RsqlParseException.class,
                    () -> RsqlToCriteria.parse("movies.name==Inception", cb, director));
            assertEquals("Field 'movies' on Director is a collection and cannot be used", e.getMessage());
        } finally {
            em.close();
        }
    }

    @Test
    void path_through_basic_attribute_is_rejected() {
        // "name" exists on Movie, so it must not be resolved there again
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> query("year.name==x"));
        assertEquals("Field 'year' on Movie has no fields", e.getMessage());
        assertThrows(RsqlParseException.class, () -> query("director.lastName.length==5"));
    }

    // --- limits and messages ---

    @Test
    void limits_apply_through_public_api() {
        EntityManager em = emf.createEntityManager();
        try {
            Root<Movie> root = cb.createQuery(Movie.class).from(Movie.class);
            RsqlLimits limits = RsqlLimits.DEFAULTS.withMaxInArguments(2);
            assertNotNull(RsqlToCriteria.parse("genre=in=(action,crime)", cb, root, limits));
            assertThrows(RsqlParseException.class,
                    () -> RsqlToCriteria.parse("genre=in=(action,crime,drama)", cb, root, limits));
            assertThrows(RsqlParseException.class,
                    () -> RsqlToCriteria.parse("(".repeat(17) + "year==1" + ")".repeat(17), cb, root));
        } finally {
            em.close();
        }
    }

    @Test
    void comparing_an_association_names_only_the_simple_type() {
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> query("director==5"));
        assertEquals("Fields of type Director cannot be compared to a value", e.getMessage());
        assertThat(query("director!=null"), hasSize(5));
    }

    // --- where the error is ---

    @Test
    void semantic_errors_name_selector_and_position() {
        RsqlParseException unknown = assertThrows(RsqlParseException.class, () -> query("year==2000;yaer==2001"));
        assertEquals("Unknown field 'yaer' on Movie", unknown.getMessage());
        assertEquals("yaer", unknown.getSelector().orElseThrow());
        assertEquals(12, unknown.getPosition().orElseThrow());

        RsqlParseException coercion = assertThrows(RsqlParseException.class, () -> query("name==x,(director.lastName==N;year==abc)"));
        assertEquals("year", coercion.getSelector().orElseThrow());
        assertEquals(31, coercion.getPosition().orElseThrow());

        RsqlParseException operator = assertThrows(RsqlParseException.class, () -> query("year=foo=2003"));
        assertEquals("year", operator.getSelector().orElseThrow());
        assertEquals(1, operator.getPosition().orElseThrow());
    }

    @Test
    void query_level_errors_have_no_selector() {
        RsqlParseException e = assertThrows(RsqlParseException.class,
                () -> query("(".repeat(17) + "year==1" + ")".repeat(17)));
        assertTrue(e.getSelector().isEmpty());
        assertTrue(e.getPosition().isEmpty());
    }

    // --- case-insensitive matching and whitespace ---

    @Test
    void ilike_ignores_case() {
        assertThat(query("name=like=inception"), hasSize(0));   // H2 compares case-sensitively
        assertThat(query("name=ilike=inception"), hasSize(1));
        assertThat(query("name=ilike=*KNIGHT*"), hasSize(1));
        assertThat(query("director.lastName=ilike=nOlAn"), hasSize(3));
        assertThat(query("name=ilike=inception OR name=ilike='kill bill'"), hasSize(2));
    }

    @Test
    void ilike_rejects_null() {
        assertThrows(RsqlParseException.class, () -> query("name=ilike=null"));
    }

    @Test
    void line_break_before_separator_does_not_change_the_value() {
        assertThat(query("name==Inception\n;year==2010"), hasSize(1));
        assertThat(query("name==Inception\r\nAND\r\nyear==2010"), hasSize(1));
    }
}
