package visitors;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Filters on the date/time and numeric types beyond the basics, end to end against H2. */
class TypeCoercionTest {

    static EntityManagerFactory emf;
    static CriteriaBuilder cb;

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("test-pu");
        cb = emf.getCriteriaBuilder();
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        TypedValues early = row("early", "2024-01-01T08:00:00Z", "08:00", 2023, "2023-11", 10, 1, "100", 'a');
        early.rating = TypedValues.Rating.PG_13;
        em.persist(early);
        TypedValues late = row("late", "2024-06-15T18:30:00Z", "18:30", 2024, "2024-06", 20, 2, "99999999999999999999", 'z');
        late.rating = TypedValues.Rating.R;
        em.persist(late);
        em.getTransaction().commit();
        em.close();
    }

    private static TypedValues row(String label, String instant, String time, int year, String yearMonth,
                                   int number, int smallNumber, String big, char c) {
        TypedValues v = new TypedValues();
        v.label = label;
        v.instant = Instant.parse(instant);
        v.zoned = ZonedDateTime.ofInstant(v.instant, ZoneOffset.UTC);
        v.offsetTime = OffsetTime.of(LocalTime.parse(time), ZoneOffset.UTC);
        v.timeOfDay = LocalTime.parse(time);
        v.yearValue = Year.of(year);
        v.yearMonth = YearMonth.parse(yearMonth);
        v.shortValue = (short) number;
        v.shortPrimitive = (short) number;
        v.byteValue = (byte) smallNumber;
        v.bytePrimitive = (byte) smallNumber;
        v.bigInteger = new BigInteger(big);
        v.charValue = c;
        v.charPrimitive = c;
        return v;
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    private List<String> labels(String rsql) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<TypedValues> cq = cb.createQuery(TypedValues.class);
            Root<TypedValues> root = cq.from(TypedValues.class);
            cq.where(RsqlToCriteria.parse(rsql, cb, root));
            return em.createQuery(cq).getResultList().stream().map(TypedValues::getLabel).sorted().toList();
        } finally {
            em.close();
        }
    }

    // Expected labels separated by ';', empty for none
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "instant==2024-01-01T08:00:00Z               | early",
            "instant=gt=2024-03-01T00:00:00Z             | late",
            "instant=lt=2024-01-01T10:00:01+02:00        | early",
            "instant=ge=2024-01-01T10:00:00+02:00        | early;late",
            "zoned=gt=2024-03-01T00:00:00Z               | late",
            "zoned=lt=2024-01-01T09:00:01+01:00[Europe/Zurich] | early",
            "timeOfDay=lt=12:00                          | early",
            "timeOfDay=in=(08:00,18:30:00)               | early;late",
            "offsetTime==18:30Z                          | late",
            "yearValue==2024                             | late",
            "yearValue=lt=2024                           | early",
            "yearMonth=ge=2024-01                        | late",
            "shortValue==10                              | early",
            "shortPrimitive=gt=15                        | late",
            "byteValue==2                                | late",
            "bytePrimitive=in=(1,2)                      | early;late",
            "bigInteger=gt=1000                          | late",
            "bigInteger==99999999999999999999            | late",
            "charValue==a                                | early",
            "charPrimitive=gt=m                          | late",
            "rating==PG_13                               | early",
            "rating==pg_13                               | early",
            "rating=in=(r,Pg_13)                         | early;late",
            "rating!=r                                   | early",
    })
    void filters(String rsql, String expected) {
        List<String> names = expected.isEmpty() ? List.of() : Arrays.stream(expected.split(";")).sorted().toList();
        assertThat(labels(rsql), is(names));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "instant==2024-01-01",           // no time, no zone
            "instant==2024-01-01T08:00:00",  // no zone
            "zoned==2024-01-01T08:00",
            "timeOfDay==25:00",
            "yearMonth==2024-13",
            "shortValue==40000",
            "byteValue==200",
            "bigInteger==1.5",
            "charValue==ab",
            "charPrimitive==''",
            "rating==PG13",
    })
    void invalid_values_are_rejected(String rsql) {
        RsqlParseException e = assertThrows(RsqlParseException.class, () -> labels(rsql));
        assertThat(e.getMessage(), startsWith("Cannot coerce"));
    }
}
