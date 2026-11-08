/**
 * Translates RSQL / FIQL query strings into JPA Criteria API predicates.
 *
 * <p>The entry point is {@link ch.nvaria.rsql.RsqlToCriteria}. Filtering inside JSON columns needs
 * Hibernate ORM on the classpath; filtering on entity attributes works with any JPA provider.
 */
module ch.nvaria.rsql {
    requires transitive jakarta.persistence;
    requires org.antlr.antlr4.runtime;
    // Only needed for JSON columns, which use Hibernate-specific functions and casts
    requires static org.hibernate.orm.core;

    exports ch.nvaria.rsql;

    provides org.hibernate.boot.model.FunctionContributor
            with ch.nvaria.rsql.internal.JsonValueFunctionContributor;
}
