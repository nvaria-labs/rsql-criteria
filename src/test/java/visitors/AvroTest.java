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
import ch.nvaria.rsql.RsqlToCriteria;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class AvroTest {


    static EntityManagerFactory emf;
    static CriteriaBuilder cb;

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("test-pu");
        cb  = emf.getCriteriaBuilder();

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Product p1 = new Product();
        Product p2 = new Product();

        p1.setCar("{\"name\": \"ix3\", \"horsepower\": { \"int\": 400 },\"favorite_color\": {\"string\": \"blue\"}}");
        p2.setCar("{\"name\": \"glc\", \"horsepower\": { \"int\": 500 },\"favorite_color\": {\"string\": \"silver\"}}");


        em.persist(p1);
        em.persist(p2);


        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }


    private List<Product> query(String rsql) {
        return query(rsql, Map.of());
    }

    private List<Product> query(String rsql, Map<String, Class<?>> fieldTypes) {
        EntityManager em = emf.createEntityManager();
        CriteriaQuery<Product> cq   = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);
        Predicate predicate = RsqlToCriteria.parseJsonColumn(rsql, cb, root, "car", fieldTypes, JsonLayout.AVRO);
        cq.where(predicate);
        TypedQuery<Product> q = em.createQuery(cq);
        List<Product> results = q.getResultList();
        em.close();
        return results;
    }

    // --- Avro union field comparisons ---

    @Test
    void eq_string() {
        assertThat(query("horsepower==500"), hasSize(1));
    }

    @Test
    void eq_string_field() {
        assertThat(query("favorite_color==blue"), hasSize(1));
    }

    @Test
    void eq_plain_string() {
        assertThat(query("name==ix3"), hasSize(1));
    }
}

