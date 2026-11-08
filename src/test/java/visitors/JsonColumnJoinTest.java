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

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ch.nvaria.rsql.JsonLayout;
import ch.nvaria.rsql.RsqlToCriteria;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class JsonColumnJoinTest {

    static EntityManagerFactory emf;
    static CriteriaBuilder cb;

    // Movies and their products (via product.movieId):
    //   The Dark Knight  (action,  Nolan)    → Widget (9.99, available=true)
    //                                        → Doohickey (4.99, available=false)
    //   Inception        (sci-fi,  Nolan)    → Gadget (24.99, available=true)
    //   Interstellar     (sci-fi,  Nolan)    → Thingamajig (49.99, available=true)
    //   Pulp Fiction     (crime,   Tarantino)→ (no products)
    //   Kill Bill        (action,  Tarantino)→ Whatchamacallit (1.99, available=false)
    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("test-pu");
        cb  = emf.getCriteriaBuilder();

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        Director nolan     = new Director("Christopher", "Nolan");
        Director tarantino = new Director("Quentin",     "Tarantino");
        em.persist(nolan);
        em.persist(tarantino);

        Movie darkKnight  = new Movie("The Dark Knight", 2008, "action", "Christian Bale",      nolan);
        Movie inception   = new Movie("Inception",       2010, "sci-fi", "Leonardo DiCaprio",   nolan);
        Movie interstellar = new Movie("Interstellar",   2014, "sci-fi", "Matthew McConaughey", nolan);
        Movie pulpFiction = new Movie("Pulp Fiction",    1994, "crime",  "John Travolta",       tarantino);
        Movie killBill    = new Movie("Kill Bill",       2003, "action", "Uma Thurman",         tarantino);
        em.persist(darkKnight);
        em.persist(inception);
        em.persist(interstellar);
        em.persist(pulpFiction);
        em.persist(killBill);

        // flush to get generated IDs before using them in products
        em.flush();

        em.persist(new Product(darkKnight.getId(),   "{\"name\":\"Widget\",          \"price\":9.99,  \"available\":true}"));
        em.persist(new Product(darkKnight.getId(),   "{\"name\":\"Doohickey\",       \"price\":4.99,  \"available\":false}"));
        em.persist(new Product(inception.getId(),    "{\"name\":\"Gadget\",          \"price\":24.99, \"available\":true}"));
        em.persist(new Product(interstellar.getId(), "{\"name\":\"Thingamajig\",     \"price\":49.99, \"available\":true}"));
        em.persist(new Product(killBill.getId(),     "{\"name\":\"Whatchamacallit\", \"price\":1.99,  \"available\":false}"));

        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    // --- helper ---

    private List<Movie> query(String movieRsql, String productRsql) {
        EntityManager em = emf.createEntityManager();

        CriteriaQuery<Movie> cq        = cb.createQuery(Movie.class);
        Root<Movie>          movieRoot = cq.from(Movie.class);

        Subquery<Long>   sub         = cq.subquery(Long.class);
        Root<Product>    productRoot = sub.from(Product.class);
        Predicate        prodFilter  = RsqlToCriteria.parseJsonColumn(productRsql, cb, productRoot, "attributes", JsonLayout.PLAIN);
        sub.select(productRoot.get("id"))
           .where(cb.and(
               cb.equal(productRoot.get("movieId"), movieRoot.get("id")),
               prodFilter
           ));

        Predicate movieFilter = RsqlToCriteria.parse(movieRsql, cb, movieRoot);
        cq.where(cb.and(movieFilter, cb.exists(sub)));

        List<Movie> results = em.createQuery(cq).getResultList();
        em.close();
        return results;
    }

    // --- tests ---

    @Test
    void actionMovies_havingCheapProduct() {
        // action movies with at least one product where price < 8
        // Dark Knight has Widget(9.99) and Doohickey(4.99) → Doohickey qualifies
        // Kill Bill has Whatchamacallit(1.99) → qualifies
        assertThat(query("genre==action", "price=lt=8"), hasSize(2));
    }

    @Test
    void actionMovies_havingAvailableProduct() {
        // action movies with at least one available product
        // Dark Knight has Widget(available=true) → qualifies
        // Kill Bill has only Whatchamacallit(available=false) → excluded
        assertThat(query("genre==action", "available==true"), hasSize(1));
    }

    @Test
    void scifiMovies_havingExpensiveProduct() {
        // sci-fi movies with at least one product where price > 20
        // Inception → Gadget(24.99), Interstellar → Thingamajig(49.99)
        assertThat(query("genre==sci-fi", "price=gt=20"), hasSize(2));
    }

    @Test
    void nolanMovies_havingAvailableProduct() {
        // Nolan movies with at least one available product
        // Dark Knight (Widget available=true), Inception, Interstellar
        assertThat(query("director.lastName==Nolan", "available==true"), hasSize(3));
    }

    @Test
    void movies_havingUnavailableProduct() {
        // movies with at least one unavailable product
        // Dark Knight (Doohickey), Kill Bill (Whatchamacallit)
        assertThat(query("genre==action,genre==sci-fi,genre==crime", "available==false"), hasSize(2));
    }

    @Test
    void crimeMovies_havingAnyProduct() {
        // Pulp Fiction has no products → excluded by EXISTS
        assertThat(query("genre==crime", "available==true,available==false"), hasSize(0));
    }
}
