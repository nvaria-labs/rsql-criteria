package ch.nvaria.rsql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes an entity attribute from RSQL filters built by
 * {@link RsqlToCriteria#parse(String, jakarta.persistence.criteria.CriteriaBuilder,
 * jakarta.persistence.criteria.From)}.
 *
 * <p>A selector that names a hidden attribute, or navigates through one, is rejected with the same
 * {@link RsqlParseException} as an unknown attribute, so clients cannot tell that it exists. Hiding
 * an association or embeddable hides every path below it.
 *
 * <p>Put the annotation on the field or on its getter, whichever the entity uses for access.
 * Filters on fields inside JSON columns are not affected.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface RsqlHidden {
}
