package visitors;

import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.RsqlToCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Selectors through collections: EXISTS subqueries, end to end against H2. */
class CollectionFilterTest {

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
        Director kubrick = new Director("Stanley", "Kubrick");   // no movies
        em.persist(nolan);
        em.persist(tarantino);
        em.persist(kubrick);
        Movie darkKnight = movie("The Dark Knight", 2008, nolan, "batman", "classic");
        darkKnight.setLocations(new Location("Chicago", "USA", "P-1"), new Location("London", "UK", "P-2"));
        em.persist(darkKnight);
        Movie inception = movie("Inception", 2010, nolan, "dream");
        inception.setLocations(new Location("Paris", "France", "P-3"), new Location("London", "UK", "P-4"),
                new Location("Tangier", "Morocco", "P-5"));
        em.persist(inception);
        em.persist(movie("Interstellar", 2014, nolan));          // no tags
        em.persist(movie("Pulp Fiction", 1994, tarantino, "classic", "cult"));
        em.persist(movie("Kill Bill", 2003, tarantino, "cult"));
        em.getTransaction().commit();
        em.close();
    }

    private static Movie movie(String name, int year, Director director, String... tags) {
        Movie movie = new Movie(name, year, "drama", "someone", director);
        movie.setTags(tags);
        return movie;
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    private List<String> movies(String rsql) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<Movie> cq = cb.createQuery(Movie.class);
            Root<Movie> root = cq.from(Movie.class);
            cq.where(RsqlToCriteria.parse(rsql, cb, cq, root));
            return em.createQuery(cq).getResultList().stream().map(Movie::getName).sorted().toList();
        } finally {
            em.close();
        }
    }

    private List<String> directors(String rsql) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<Director> cq = cb.createQuery(Director.class);
            Root<Director> root = cq.from(Director.class);
            cq.where(RsqlToCriteria.parse(rsql, cb, cq, root));
            return em.createQuery(cq).getResultList().stream().map(Director::getLastName).sorted().toList();
        } finally {
            em.close();
        }
    }

    private static List<String> names(String expected) {
        return expected.isEmpty() ? List.of() : Arrays.stream(expected.split(";")).sorted().toList();
    }

    // Expected movie names separated by ';', empty for none
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "tags==classic                  | Pulp Fiction;The Dark Knight",
            "tags==cult;tags==classic       | Pulp Fiction",
            "tags==c*                       | Kill Bill;Pulp Fiction;The Dark Knight",
            "tags=in=(dream,cult)           | Inception;Kill Bill;Pulp Fiction",
            "tags!=classic                  | Inception;Interstellar;Kill Bill",
            "tags=out=(classic,cult)        | Inception;Interstellar",
            "tags==null                     | Interstellar",
            "tags!=null                     | Inception;Kill Bill;Pulp Fiction;The Dark Knight",
            "tags==classic,year=gt=2009     | Inception;Interstellar;Pulp Fiction;The Dark Knight",
            "(tags==cult;year<2000),tags==dream | Inception;Pulp Fiction",
            "director.movies.name==Inception | Inception;Interstellar;The Dark Knight",
            "director.movies.tags==cult     | Kill Bill;Pulp Fiction",
            "tags=ilike=CLASSIC             | Pulp Fiction;The Dark Knight",
    })
    void movieFilters(String rsql, String expected) {
        assertThat(movies(rsql), is(names(expected)));
    }

    // Expected director last names separated by ';', empty for none
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "movies.name==Inception                 | Nolan",
            "movies.year=lt=2000                    | Tarantino",
            "movies.name!=Inception                 | Kubrick;Tarantino",
            "movies.name=out=(Inception,'Kill Bill') | Kubrick",
            "movies.tags==cult                      | Tarantino",
            "movies.tags!=cult                      | Kubrick;Nolan",
            "movies.director.lastName==Nolan        | Nolan",
            "movies==null                           | Kubrick",
            "movies!=null                           | Nolan;Tarantino",
            "movies.year=gt=2005;movies.year=lt=2000 | ``",
            "movies.year=gt=2005,movies.year=lt=2000 | Nolan;Tarantino",
            "movies.name==Inception;lastName==Nolan | Nolan",
    })
    void directorFilters(String rsql, String expected) {
        assertThat(directors(rsql), is(names(expected)));
    }

    @Test
    void sql_is_a_correlated_exists_subquery() {
        SqlCapture.clear();
        directors("movies.name!=Inception");
        String sql = SqlCapture.statements().getLast().toLowerCase();
        assertTrue(sql.contains("not exists"), sql);
        assertFalse(sql.contains("distinct"), sql);
    }

    // --- element collection of embeddables: Movie.locations ---

    // Expected movie names separated by ';', empty for none
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "locations.city==London                  | Inception;The Dark Knight",
            "locations.city==Paris                   | Inception",
            "locations.country=in=(USA,Morocco)      | Inception;The Dark Knight",
            "locations.city=ilike=chi*               | The Dark Knight",
            "locations.city!=Paris                   | Interstellar;Kill Bill;Pulp Fiction;The Dark Knight",
            "locations.country=out=(UK)              | Interstellar;Kill Bill;Pulp Fiction",
            "locations==null                         | Interstellar;Kill Bill;Pulp Fiction",
            "locations!=null                         | Inception;The Dark Knight",
            "locations.city==London;year=gt=2009     | Inception",
            "locations.city==Paris,tags==cult        | Inception;Kill Bill;Pulp Fiction",
            "director.movies.locations.city==Tangier | Inception;Interstellar;The Dark Knight",
    })
    void embeddableCollectionFilters(String rsql, String expected) {
        assertThat(movies(rsql), is(names(expected)));
    }

    @Test
    void embeddable_conditions_may_match_different_elements() {
        // no single location is in Paris and in the UK, but Inception has one of each
        assertThat(movies("locations.city==Paris;locations.country==UK"), is(List.of("Inception")));
    }

    @Test
    void embeddable_collection_errors() {
        assertEquals("Unknown field 'permitNumber' on Location",
                assertThrows(RsqlParseException.class, () -> movies("locations.permitNumber==P-1")).getMessage());
        assertEquals("Unknown field 'street' on Location",
                assertThrows(RsqlParseException.class, () -> movies("locations.street==x")).getMessage());
        assertEquals("Fields of type Location cannot be compared to a value",
                assertThrows(RsqlParseException.class, () -> movies("locations==London")).getMessage());
        assertEquals("Field 'city' on Location has no fields",
                assertThrows(RsqlParseException.class, () -> movies("locations.city.name==x")).getMessage());
        assertThrows(RsqlParseException.class, () -> movies("locations=in=(x,null)"));
    }

    @Test
    void embeddable_collection_is_not_sortable() {
        EntityManager em = emf.createEntityManager();
        try {
            Root<Movie> root = cb.createQuery(Movie.class).from(Movie.class);
            assertThrows(RsqlParseException.class, () -> RsqlToCriteria.checkPath("locations.city", root));
        } finally {
            em.close();
        }
    }

    @Test
    void conditions_on_one_collection_may_match_different_elements() {
        // Pulp Fiction is from 1994 and Kill Bill from 2003: two different movies satisfy the two conditions
        assertThat(directors("movies.year<2000;movies.name=='Kill Bill'"), is(List.of("Tarantino")));
    }

    @Test
    void every_row_appears_once_and_count_matches() {
        // Pulp Fiction has two tags matching c*, Nolan has three movies matching *
        assertThat(movies("tags==c*"), is(names("Kill Bill;Pulp Fiction;The Dark Knight")));
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<Director> root = count.from(Director.class);
            count.select(cb.count(root)).where(RsqlToCriteria.parse("movies.name==*", cb, count, root));
            assertEquals(2L, em.createQuery(count).getSingleResult());

            CriteriaQuery<Director> page = cb.createQuery(Director.class);
            Root<Director> pageRoot = page.from(Director.class);
            page.where(RsqlToCriteria.parse("movies.name==*", cb, page, pageRoot))
                    .orderBy(cb.asc(pageRoot.get("lastName")));
            List<String> first = em.createQuery(page).setFirstResult(0).setMaxResults(1).getResultList()
                    .stream().map(Director::getLastName).toList();
            List<String> second = em.createQuery(page).setFirstResult(1).setMaxResults(1).getResultList()
                    .stream().map(Director::getLastName).toList();
            assertThat(first, is(List.of("Nolan")));
            assertThat(second, is(List.of("Tarantino")));
        } finally {
            em.close();
        }
    }

    @Test
    void works_on_a_join() {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<Movie> cq = cb.createQuery(Movie.class);
            Root<Movie> movie = cq.from(Movie.class);
            Join<Movie, Director> director = movie.join("director");
            cq.where(RsqlToCriteria.parse("movies.name=='Kill Bill'", cb, cq, director));
            assertThat(em.createQuery(cq).getResultList().stream().map(Movie::getName).sorted().toList(),
                    is(List.of("Kill Bill", "Pulp Fiction")));
        } finally {
            em.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tags=in=(cult,null)", "tags=out=(null)", "movies=gt=null"})
    void null_in_lists_on_the_collection_itself_is_rejected(String rsql) {
        assertThrows(RsqlParseException.class, () -> {
            if (rsql.startsWith("movies")) directors(rsql);
            else movies(rsql);
        });
    }

    @Test
    void entity_collection_itself_cannot_be_compared_to_a_value() {
        // the elements are Movies: navigate to one of their fields, or test for empty with ==null / !=null
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> directors("movies==Inception"));
        assertEquals("Fields of type Movie cannot be compared to a value", e.getMessage());
        assertThrows(RsqlParseException.class, () -> directors("movies=in=(1,2)"));
    }

    @Test
    void collection_errors_keep_their_messages() {
        RsqlParseException unknown = assertThrows(RsqlParseException.class, () -> directors("movies.nope==1"));
        assertEquals("Unknown field 'nope' on Movie", unknown.getMessage());
        assertEquals("movies.nope", unknown.getSelector().orElseThrow());
        assertThrows(RsqlParseException.class, () -> directors("movies.year==abc"));
        assertThrows(RsqlParseException.class, () -> directors("movies.name.length==1"));
    }

    @Test
    void without_the_query_collections_are_still_rejected() {
        EntityManager em = emf.createEntityManager();
        try {
            Root<Director> root = cb.createQuery(Director.class).from(Director.class);
            RsqlParseException e = assertThrows(RsqlParseException.class,
                    () -> RsqlToCriteria.parse("movies.name==Inception", cb, root));
            assertEquals("Field 'movies' on Director is a collection and cannot be used", e.getMessage());
            assertThrows(RsqlParseException.class, () -> RsqlToCriteria.checkPath("movies.name", root));
        } finally {
            em.close();
        }
    }
}
