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

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RsqlHiddenTest {

    static EntityManagerFactory emf;
    static CriteriaBuilder cb;

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("hidden-pu");
        cb = emf.getCriteriaBuilder();

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        HiddenStudio warner = new HiddenStudio("Warner", 1000, "alice", "import");
        HiddenStudio miramax = new HiddenStudio("Miramax", 500, "bob", "manual");
        em.persist(warner);
        em.persist(miramax);
        em.persist(new HiddenFilm("Inception", "reshoots", warner, miramax, new HiddenBudget("USD", 160)));
        em.persist(new HiddenFilm("Pulp Fiction", "cheap", miramax, warner, new HiddenBudget("EUR", 8)));
        em.persist(new HiddenCritic("Ebert", "roger@example.com", "555"));
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    private List<String> films(String rsql) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<HiddenFilm> cq = cb.createQuery(HiddenFilm.class);
            Root<HiddenFilm> root = cq.from(HiddenFilm.class);
            cq.where(RsqlToCriteria.parse(rsql, cb, root));
            return em.createQuery(cq).getResultList().stream().map(HiddenFilm::getName).toList();
        } finally {
            em.close();
        }
    }

    private List<HiddenCritic> critics(String rsql) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<HiddenCritic> cq = cb.createQuery(HiddenCritic.class);
            Root<HiddenCritic> root = cq.from(HiddenCritic.class);
            cq.where(RsqlToCriteria.parse(rsql, cb, root));
            return em.createQuery(cq).getResultList();
        } finally {
            em.close();
        }
    }

    private static String rejection(Runnable query) {
        return assertThrows(RsqlParseException.class, query::run).getMessage();
    }

    @Test
    void visible_attributes_still_work() {
        assertThat(films("name==Inception"), contains("Inception"));
        assertThat(films("studio.name==Miramax"), contains("Pulp Fiction"));
        assertThat(films("budget.currency==EUR"), contains("Pulp Fiction"));
        assertThat(films("studio.source==import"), contains("Inception"));
    }

    @Test
    void hidden_basic_attribute_is_rejected() {
        assertEquals("Unknown field 'internalNotes' on HiddenFilm", rejection(() -> films("internalNotes==cheap")));
    }

    @Test
    void hidden_attribute_looks_like_unknown_attribute() {
        String unknown = rejection(() -> films("nonExistent==x"));
        String hidden = rejection(() -> films("internalNotes==x"));
        assertEquals(unknown.replace("nonExistent", "internalNotes"), hidden);
    }

    @Test
    void hidden_attribute_is_rejected_in_any_position_of_the_query() {
        rejection(() -> films("name==Inception,internalNotes==cheap"));
        rejection(() -> films("name==Inception;(name==x,internalNotes=in=(a,b))"));
        rejection(() -> films("internalNotes==null"));
    }

    @Test
    void hidden_attribute_behind_visible_association_is_rejected() {
        assertEquals("Unknown field 'revenue' on HiddenStudio", rejection(() -> films("studio.revenue=gt=0")));
    }

    @Test
    void hidden_association_hides_everything_below_it() {
        assertEquals("Unknown field 'distributor' on HiddenFilm", rejection(() -> films("distributor.name==Warner")));
        rejection(() -> films("distributor==null"));
    }

    @Test
    void hidden_attribute_in_embeddable_is_rejected() {
        assertEquals("Unknown field 'amount' on HiddenBudget", rejection(() -> films("budget.amount=lt=10")));
    }

    @Test
    void hidden_attribute_from_mapped_superclass_is_rejected() {
        assertEquals("Unknown field 'createdBy' on HiddenStudio", rejection(() -> films("studio.createdBy==alice")));
    }

    @Test
    void hidden_attribute_is_rejected_on_join() {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<HiddenFilm> cq = cb.createQuery(HiddenFilm.class);
            Join<HiddenFilm, HiddenStudio> studio = cq.from(HiddenFilm.class).join("studio");
            assertThrows(RsqlParseException.class, () -> RsqlToCriteria.parse("revenue=gt=0", cb, studio));
            cq.where(RsqlToCriteria.parse("name==Warner", cb, studio));
            assertThat(em.createQuery(cq).getResultList(), hasSize(1));
        } finally {
            em.close();
        }
    }

    @Test
    void property_access_hidden_on_getter_is_rejected() {
        assertThat(critics("name==Ebert"), hasSize(1));
        assertEquals("Unknown field 'email' on HiddenCritic", rejection(() -> critics("email==x")));
    }

    @Test
    void property_access_hidden_on_field_is_rejected() {
        assertEquals("Unknown field 'phone' on HiddenCritic", rejection(() -> critics("phone==555")));
    }

    // --- checkPath, e.g. for sort properties ---

    private static void checkFilmPath(String path) {
        CriteriaQuery<HiddenFilm> cq = cb.createQuery(HiddenFilm.class);
        RsqlToCriteria.checkPath(path, cq.from(HiddenFilm.class));
    }

    @Test
    void checkPath_accepts_visible_paths() {
        checkFilmPath("name");
        checkFilmPath("studio.name");
        checkFilmPath("studio");
        checkFilmPath("budget.currency");
    }

    @Test
    void checkPath_rejects_like_filters() {
        assertEquals("Unknown field 'internalNotes' on HiddenFilm", rejection(() -> checkFilmPath("internalNotes")));
        assertEquals("Unknown field 'revenue' on HiddenStudio", rejection(() -> checkFilmPath("studio.revenue")));
        assertEquals("Unknown field 'distributor' on HiddenFilm", rejection(() -> checkFilmPath("distributor.name")));
        assertEquals("Unknown field 'amount' on HiddenBudget", rejection(() -> checkFilmPath("budget.amount")));
        assertEquals("Unknown field 'createdBy' on HiddenStudio", rejection(() -> checkFilmPath("studio.createdBy")));
        assertEquals("Unknown field 'nope' on HiddenFilm", rejection(() -> checkFilmPath("nope")));
        assertEquals("Field 'name' on HiddenFilm has no fields", rejection(() -> checkFilmPath("name.length")));
    }

    @Test
    void checkPath_rejects_empty_segments() {
        rejection(() -> checkFilmPath(""));
        rejection(() -> checkFilmPath("studio."));
        rejection(() -> checkFilmPath("studio..name"));
    }

    @Test
    void checkPath_works_on_property_access() {
        CriteriaQuery<HiddenCritic> cq = cb.createQuery(HiddenCritic.class);
        Root<HiddenCritic> root = cq.from(HiddenCritic.class);
        RsqlToCriteria.checkPath("name", root);
        assertThrows(RsqlParseException.class, () -> RsqlToCriteria.checkPath("email", root));
        assertThrows(RsqlParseException.class, () -> RsqlToCriteria.checkPath("phone", root));
    }

    // --- collections ---

    private List<HiddenStudio> studios(String rsql) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaQuery<HiddenStudio> cq = cb.createQuery(HiddenStudio.class);
            Root<HiddenStudio> root = cq.from(HiddenStudio.class);
            cq.where(RsqlToCriteria.parse(rsql, cb, cq, root));
            return em.createQuery(cq).getResultList();
        } finally {
            em.close();
        }
    }

    @Test
    void hidden_collection_is_rejected() {
        assertEquals("Unknown field 'films' on HiddenStudio", rejection(() -> studios("films.name==Inception")));
        assertEquals("Unknown field 'films' on HiddenStudio", rejection(() -> studios("films==null")));
    }

    @Test
    void hidden_attribute_behind_visible_collection_is_rejected() {
        assertThat(studios("distributed.name==Inception"), hasSize(1));
        assertEquals("Unknown field 'internalNotes' on HiddenFilm",
                rejection(() -> studios("distributed.internalNotes==cheap")));
    }
}
