package visitors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ch.nvaria.rsql.JsonLayout;
import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.RsqlToCriteria;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonColumnRsqlVisitorTest {

    static EntityManagerFactory emf;
    static CriteriaBuilder cb;

    // {"name":"...", "price":..., "stock":..., "available":..., "releaseDate":"..."}
    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("test-pu");
        cb  = emf.getCriteriaBuilder();

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        em.persist(new Product("{\"name\":\"Widget\",          \"price\":9.99,  \"stock\":100, \"available\":true,  \"releaseDate\":\"2023-06-15\"}"));
        em.persist(new Product("{\"name\":\"Gadget\",          \"price\":24.99, \"stock\":5,   \"available\":true,  \"releaseDate\":\"2024-01-10\"}"));
        em.persist(new Product("{\"name\":\"Doohickey\",       \"price\":4.99,  \"stock\":0,   \"available\":false, \"releaseDate\":\"2022-03-01\"}"));
        em.persist(new Product("{\"name\":\"Thingamajig\",     \"price\":49.99, \"stock\":20,  \"available\":true,  \"releaseDate\":\"2024-06-01\"}"));
        em.persist(new Product("{\"name\":\"Whatchamacallit\", \"price\":1.99,  \"stock\":999, \"available\":false, \"releaseDate\":\"2021-11-30\"}"));

        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    // --- helper ---

    private List<Product> query(String rsql) {
        return query(rsql, Map.of());
    }

    private List<Product> query(String rsql, Map<String, Class<?>> fieldTypes) {
        EntityManager em = emf.createEntityManager();
        CriteriaQuery<Product> cq   = cb.createQuery(Product.class);
        Root<Product>          root = cq.from(Product.class);
        Predicate predicate = RsqlToCriteria.parseJsonColumn(rsql, cb, root, "attributes", fieldTypes, JsonLayout.AVRO);
        cq.where(predicate);
        TypedQuery<Product> q = em.createQuery(cq);
        List<Product> results = q.getResultList();
        em.close();
        return results;
    }

    // --- string equality ---

    @Test
    void eq_string() {
        assertThat(query("name==Widget"), hasSize(1));
    }

    @Test
    void neq_string() {
        assertThat(query("name!=Widget"), hasSize(4));
    }

    @Test
    void eq_wildcardPrefix() {
        // Widget, Whatchamacallit
        assertThat(query("name==W*"), hasSize(2));
    }

    @Test
    void eq_wildcardInfix() {
        assertThat(query("name==*gadget*"), hasSize(0));
    }

    @Test
    void like_operator() {
        assertThat(query("name=like=*get"), hasSize(2)); // Widget, Gadget
    }

    // --- numeric (inferred as Double) ---

    @Test
    void gt_number_inferred() {
        // Gadget 24.99, Thingamajig 49.99
        assertThat(query("price=gt=10"), hasSize(2));
    }

    @Test
    void ge_number_inferred() {
        // Gadget 24.99, Thingamajig 49.99
        assertThat(query("price=ge=24.99"), hasSize(2));
    }

    @Test
    void lt_number_inferred() {
        // Widget 9.99, Doohickey 4.99, Whatchamacallit 1.99
        assertThat(query("price=lt=10"), hasSize(3));
    }

    @Test
    void le_number_inferred() {
        // Doohickey 0, Gadget 5
        assertThat(query("stock=le=5"), hasSize(2));
    }

    @Test
    void gt_number_explicit() {
        assertThat(query("price=gt=10", Map.of("price", Double.class)), hasSize(2));
    }

    /**
     * Regression: ordering on a numeric JSON value must render a real SQL {@code cast(...)}.
     * With the old {@code Expression.as()} the left side stayed text and PostgreSQL rejected
     * it with {@code operator does not exist: text >= double precision} (SQLState 42883).
     * H2 is lenient about text/number comparison, so this asserts on the emitted SQL, not
     * only the result count, to catch the defect on any dialect.
     */
    @Test
    void ge_number_rendersRealCast() {
        SqlCapture.clear();
        query("price=ge=24.99", Map.of("price", Double.class));
        // The old Expression.as() rendered no cast at all (the extraction stayed text);
        // the fix wraps the JSON extraction in cast(... as <float>). H2 compiles
        // json_value into its native (col format json)."path" syntax, so assert on the
        // cast rather than the function name.
        boolean hasNumericCast = SqlCapture.statements().stream()
            .map(String::toLowerCase)
            .anyMatch(sql -> sql.contains("cast(") && sql.contains("as float"));
        assertTrue(hasNumericCast,
            "expected a real SQL numeric cast around the JSON extraction, got: " + SqlCapture.statements());
    }

    // --- boolean ---

    @Test
    void eq_boolean_true() {
        // Widget, Gadget, Thingamajig
        assertThat(query("available==true"), hasSize(3));
    }

    @Test
    void eq_boolean_false() {
        // Doohickey, Whatchamacallit
        assertThat(query("available==false"), hasSize(2));
    }

    // --- date (inferred as LocalDate) ---

    @Test
    void gt_date_inferred() {
        // Widget 2023-06-15, Gadget 2024-01-10, Thingamajig 2024-06-01
        assertThat(query("releaseDate=gt=2023-01-01"), hasSize(3));
    }

    @Test
    void lt_date_inferred() {
        // Doohickey 2022-03-01, Whatchamacallit 2021-11-30
        assertThat(query("releaseDate=lt=2023-01-01"), hasSize(2));
    }

    // --- IN / OUT ---

    @Test
    void in_string() {
        assertThat(query("name=in=(Widget,Gadget)"), hasSize(2));
    }

    @Test
    void out_string() {
        assertThat(query("name=out=(Widget,Gadget)"), hasSize(3));
    }

    // --- AND / OR ---

    @Test
    void and_availableAndPrice() {
        // available=true AND price > 20 → Gadget, Thingamajig
        assertThat(query("available==true;price=gt=20"), hasSize(2));
    }

    @Test
    void or_names() {
        assertThat(query("name==Widget,name==Gadget"), hasSize(2));
    }

    @Test
    void group_orInsideAnd() {
        // available=true AND (price > 40 OR stock > 50) → Widget(stock 100), Thingamajig(price 49.99)
        assertThat(query("available==true;(price=gt=40,stock=gt=50)"), hasSize(2));
    }

    // --- error cases ---

    @Test
    void ordering_onString_throws() {
        assertThrows(RsqlParseException.class, () -> query("name=gt=Widget"));
    }

    @Test
    void ordering_onBoolean_explicit_throws() {
        assertThrows(RsqlParseException.class,
            () -> query("available=gt=true", Map.of("available", Boolean.class)));
    }

    @Test
    void ordering_unrecognisedValue_throws() {
        // "active" is not a date, datetime, or number → inferComparableType should throw
        assertThrows(RsqlParseException.class, () -> query("name=gt=active"));
    }
}
