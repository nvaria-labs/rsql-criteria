package visitors;

import ch.nvaria.rsql.JsonLayout;
import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.RsqlToCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Escapes, LIKE special characters and the null keyword, end to end against H2. */
class EscapingAndNullTest {

    static EntityManagerFactory emf;
    static CriteriaBuilder cb;

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("test-pu");
        cb = emf.getCriteriaBuilder();
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (String name : new String[]{"O'Brien", "O\\'Brien", "Kill \"Bill\"", "Rock 'n' \"Roll\"",
                "50% off", "500 off", "a_b", "axb", "a*b", "a\\b", "null"}) {
            Movie movie = new Movie(name, 2000, "g", "a", null);
            if (name.startsWith("5")) movie.setReleaseDate(LocalDate.of(2001, 1, 1));
            em.persist(movie);
        }
        em.persist(new Product("{\"k\":\"P1\",\"note\":null}"));
        em.persist(new Product("{\"k\":\"P2\"}"));
        em.persist(new Product("{\"k\":\"P3\",\"note\":\"x\"}"));
        em.persist(new Product("{\"k\":\"P4\",\"note\":\"null\"}"));
        em.persist(new Product("{\"k\":\"P5\",\"note\":\"50% off\"}"));
        em.persist(new Product("{\"k\":\"P6\",\"note\":\"500 off\"}"));
        em.getTransaction().commit();
        em.close();
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
            cq.where(RsqlToCriteria.parse(rsql, cb, root));
            return em.createQuery(cq).getResultList().stream().map(Movie::getName).toList();
        } finally {
            em.close();
        }
    }

    private List<String> products(String rsql) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<Product> cq = cb.createQuery(Product.class);
            Root<Product> root = cq.from(Product.class);
            cq.where(RsqlToCriteria.parseJsonColumn(rsql, cb, root, "attributes", JsonLayout.PLAIN));
            return em.createQuery(cq).getResultList().stream()
                    .map(p -> p.getAttributes().replaceAll(".*\"k\":\"(P\\d)\".*", "$1")).toList();
        } finally {
            em.close();
        }
    }

    // Expected names separated by ';', empty for none
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "name=='O\\'Brien'                  | O'Brien",
            "name==\"O'Brien\"                  | O'Brien",
            "name=='O\\\\\\'Brien'              | O\\'Brien",
            "name=='Kill \\\"Bill\\\"'          | Kill \"Bill\"",
            "name=='Rock \\'n\\' \"Roll\"'      | Rock 'n' \"Roll\"",
            "name=='50%*'                       | 50% off",
            "name=='a_b*'                       | a_b",
            "name==a\\*b                        | a*b",
            "name==*\\**                        | a*b",
            "name==*\\\\*                       | O\\'Brien;a\\b",
            "name=like='a?b'                    | ``",
            "name=like=a_*                      | a_b",
            "name!='50%*';name=='5*'            | 500 off",
            "name=='null'                       | null",
            "name==\"null\"                     | null",
            "releaseDate==null;name=='5*'       | ``",
            "releaseDate!=null                  | 50% off;500 off",
            // null keyword in lists: IS NULL / IS NOT NULL, in any position
            "name=in=(axb,null)                 | axb",
            "name=in=(axb,'null')               | axb;null",
            "releaseDate=in=(2001-01-01,null);name=in=(axb,'50% off') | 50% off;axb",
            "releaseDate=in=(null,2001-01-01);name=in=(axb,'50% off') | 50% off;axb",
            "releaseDate=in=(2001-01-02,null);name==*off              | ``",
            "releaseDate=in=(null);name=in=(axb,'50% off')            | axb",
            "releaseDate=in=(null,NULL);name=in=(axb,'50% off')       | axb",
            "releaseDate=out=(2001-01-02,null)  | 50% off;500 off",
            "releaseDate=out=(null)             | 50% off;500 off",
            "name=out=(axb,null);name==a*       | a_b;a*b;a\\b",
            "name=ilike='50%*'                  | 50% off",
            "name=ilike=A_B                     | a_b",
            "name=ilike=AXB,name=ilike='O\\'BRIEN' | O'Brien;axb",
    })
    void entityAttributes(String rsql, String expected) {
        assertThat(movies(rsql).stream().sorted().toList(), matches(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "note==null          | P1;P2",
            "note==NULL          | P1;P2",
            "note!=null          | P3;P4;P5;P6",
            "note=='null'        | P4",
            "note!='null'        | P3;P5;P6",
            "note=='50%*'        | P5",
            "note=like='5*\\%*'  | P5",
            "note=in=(x,null)    | P1;P2;P3",
            "note=in=(null,x)    | P1;P2;P3",
            "note=in=(null)      | P1;P2",
            "note=in=(x,'null')  | P3;P4",
            "note=out=(x,null)   | P4;P5;P6",
            "note=out=(null)     | P3;P4;P5;P6",
            "note=ilike=X        | P3",
            "note=ilike='50%*'   | P5",
    })
    void jsonColumn(String rsql, String expected) {
        assertThat(products(rsql).stream().sorted().toList(), matches(expected));
    }

    @Test
    void nullOnlyWorksWithEqualityOperators() {
        assertThrows(RsqlParseException.class, () -> movies("releaseDate=gt=null"));
        assertThrows(RsqlParseException.class, () -> products("note=gt=null"));
    }

    /** Sorted names, so results compare regardless of order; {@code expected} separates by ';'. */
    private static org.hamcrest.Matcher<List<String>> matches(String expected) {
        List<String> names = expected.isEmpty() ? List.of() : Arrays.stream(expected.split(";")).sorted().toList();
        return org.hamcrest.Matchers.is(names);
    }
}
