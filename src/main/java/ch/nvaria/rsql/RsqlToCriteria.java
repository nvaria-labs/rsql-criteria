package ch.nvaria.rsql;

import ch.nvaria.rsql.internal.JpaCriteriaVisitor;
import ch.nvaria.rsql.internal.JsonColumnVisitor;
import ch.nvaria.rsql.internal.RsqlParsing;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;

import java.util.Map;
import java.util.Objects;

/**
 * Translates RSQL query strings into JPA Criteria API predicates.
 *
 * <h2>Syntax</h2>
 * <ul>
 *   <li>Comparisons: {@code selector operator argument}, e.g. {@code year=ge=2000}</li>
 *   <li>Operators: {@code ==}, {@code !=}, {@code =gt=} / {@code >}, {@code =ge=} / {@code >=},
 *       {@code =lt=} / {@code <}, {@code =le=} / {@code <=}, {@code =in=}, {@code =out=},
 *       {@code =like=}, {@code =ilike=} (ignoring case)</li>
 *   <li>Lists for {@code =in=} and {@code =out=}: {@code genre=in=(drama,comedy)}</li>
 *   <li>Logical AND: {@code ;} or {@code and}; logical OR: {@code ,} or {@code or}; the words in
 *       any case and with whitespace on both sides. AND binds tighter than OR; parentheses group</li>
 *   <li>Whitespace between tokens (blanks, tabs, line breaks) is ignored</li>
 *   <li>Arguments with reserved characters or spaces go in single or double quotes</li>
 *   <li>A backslash escapes the next character, quoted or not: {@code 'O\'Brien'},
 *       {@code \*} for a literal star, {@code \\} for a backslash</li>
 *   <li>An unescaped {@code *} in an {@code ==} / {@code !=} argument is a wildcard
 *       ({@code LIKE} / {@code NOT LIKE}); {@code =like=} always treats it as one. {@code %} and
 *       {@code _} are ordinary characters</li>
 *   <li>{@code ==null} / {@code !=null} test for {@code IS NULL} / {@code IS NOT NULL}; in JSON
 *       columns a missing field counts as null. Quoted, {@code 'null'} is the string "null"</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * CriteriaBuilder cb = em.getCriteriaBuilder();
 * CriteriaQuery<Movie> query = cb.createQuery(Movie.class);
 * Root<Movie> movie = query.from(Movie.class);
 * query.where(RsqlToCriteria.parse("director.lastName==Nolan;year=ge=2000", cb, movie));
 * }</pre>
 *
 * <p>All methods throw {@link RsqlParseException} for invalid queries and never return
 * {@code null}. Queries are checked against {@link RsqlLimits#DEFAULTS} unless an overload with
 * other limits is used. The class is stateless and thread-safe.
 */
public final class RsqlToCriteria {

    private RsqlToCriteria() {}

    /**
     * Builds a predicate over entity attributes.
     *
     * <p>Selectors name attributes of {@code from}; dots navigate to-one associations and
     * embeddables ({@code director.lastName}). Selectors through collections are rejected; to
     * filter on them, use {@link #parse(String, CriteriaBuilder, CommonAbstractCriteria, From)}. Every segment is checked against the JPA metamodel,
     * and arguments are converted to the attribute's Java type: strings, numbers (primitive and
     * boxed, {@code BigInteger}, {@code BigDecimal}), {@code Boolean}, {@code Character},
     * {@code UUID}, enums by name, and in ISO-8601 format {@code LocalDate}, {@code LocalDateTime},
     * {@code LocalTime}, {@code OffsetDateTime}, {@code OffsetTime}, {@code Instant},
     * {@code ZonedDateTime}, {@code Year} and {@code YearMonth}.
     *
     * @param rsql the query
     * @param cb   the criteria builder of the query the predicate is used in
     * @param from the root or join the selectors are resolved against
     * @param <T>  the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query is malformed, names an unknown attribute or one
     *                            annotated with {@link RsqlHidden}, or has an argument that does not
     *                            fit the attribute's type
     */
    public static <T> Predicate parse(String rsql, CriteriaBuilder cb, From<?, T> from) {
        return parse(rsql, cb, from, RsqlLimits.DEFAULTS);
    }

    /**
     * Builds a predicate over entity attributes, like
     * {@link #parse(String, CriteriaBuilder, From)}, with other limits on the query size.
     *
     * @param rsql   the query
     * @param cb     the criteria builder of the query the predicate is used in
     * @param from   the root or join the selectors are resolved against
     * @param limits the limits the query must stay within
     * @param <T>    the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query exceeds a limit, is malformed, names an unknown
     *                            attribute or one annotated with {@link RsqlHidden}, or has an
     *                            argument that does not fit the attribute's type
     */
    public static <T> Predicate parse(String rsql, CriteriaBuilder cb, From<?, T> from, RsqlLimits limits) {
        Objects.requireNonNull(rsql, "rsql");
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(limits, "limits");
        return new JpaCriteriaVisitor<>(cb, null, from).visitInput(RsqlParsing.parse(rsql, limits));
    }

    /**
     * Builds a predicate over entity attributes, including attributes reached through
     * collections. Like {@link #parse(String, CriteriaBuilder, From)}, but with the query the
     * predicate is used in, which the subqueries for collections are created from.
     *
     * <p>A selector through a collection matches if <em>some</em> element matches:
     * {@code actors.name==Bale} finds movies with an actor named Bale, as an {@code EXISTS}
     * subquery, so every row appears once and paging stays correct. {@code !=} and {@code =out=}
     * mean that <em>no</em> element matches: {@code actors.name!=Bale} finds movies without an
     * actor named Bale, including movies without actors. Each comparison is a subquery of its
     * own, so {@code actors.name==Bale;actors.age=gt=50} may be satisfied by two different
     * actors. On a collection itself, {@code actors==null} / {@code actors!=null} test whether it
     * is empty, and element collections compare their elements: {@code tags==classic}.
     *
     * <pre>{@code
     * Specification<Movie> spec = (root, query, cb) -> RsqlToCriteria.parse(filter, cb, query, root);
     * }</pre>
     *
     * @param rsql  the query
     * @param cb    the criteria builder of the query the predicate is used in
     * @param query the query the predicate is used in
     * @param from  the root or join the selectors are resolved against
     * @param <T>   the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query is malformed, names an unknown attribute or one
     *                            annotated with {@link RsqlHidden}, or has an argument that does not
     *                            fit the attribute's type
     */
    public static <T> Predicate parse(String rsql, CriteriaBuilder cb, CommonAbstractCriteria query,
                                      From<?, T> from) {
        return parse(rsql, cb, query, from, RsqlLimits.DEFAULTS);
    }

    /**
     * Builds a predicate over entity attributes, including attributes reached through
     * collections, like {@link #parse(String, CriteriaBuilder, CommonAbstractCriteria, From)},
     * with other limits on the query size.
     *
     * @param rsql   the query
     * @param cb     the criteria builder of the query the predicate is used in
     * @param query  the query the predicate is used in
     * @param from   the root or join the selectors are resolved against
     * @param limits the limits the query must stay within
     * @param <T>    the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query exceeds a limit, is malformed, names an unknown
     *                            attribute or one annotated with {@link RsqlHidden}, or has an
     *                            argument that does not fit the attribute's type
     */
    public static <T> Predicate parse(String rsql, CriteriaBuilder cb, CommonAbstractCriteria query,
                                      From<?, T> from, RsqlLimits limits) {
        Objects.requireNonNull(rsql, "rsql");
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(limits, "limits");
        return new JpaCriteriaVisitor<>(cb, query, from).visitInput(RsqlParsing.parse(rsql, limits));
    }

    /**
     * Checks an attribute path from a client by the same rules as filter selectors, without
     * building a predicate. Use it for other client input that names attributes, such as the
     * properties of a sort order, so hidden attributes stay hidden there too:
     *
     * <pre>{@code
     * Specification<Movie> spec = (root, query, cb) -> {
     *     pageable.getSort().forEach(order -> RsqlToCriteria.checkPath(order.getProperty(), root));
     *     return RsqlToCriteria.parse(filter, cb, root);
     * };
     * }</pre>
     *
     * @param path a dotted attribute path, e.g. {@code director.lastName}
     * @param from the root or join the path is resolved against
     * @throws RsqlParseException if the path names an unknown attribute or one annotated with
     *                            {@link RsqlHidden}, goes through a collection, or continues after
     *                            a basic attribute
     */
    public static void checkPath(String path, From<?, ?> from) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(from, "from");
        JpaCriteriaVisitor.resolvePath(path, from);
    }

    /**
     * Builds a predicate over the fields of a JSON document stored in one attribute, untyped.
     * Same as {@link #parseJsonColumn(String, CriteriaBuilder, From, String, Map, JsonLayout)} with
     * an empty {@code fieldTypes} map.
     *
     * @param rsql            the query
     * @param cb              the criteria builder of the query the predicate is used in
     * @param from            the root or join that holds the JSON attribute
     * @param jsonColumnField name of the attribute that holds the JSON document
     * @param layout          how the JSON documents are laid out
     * @param <T>             the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query is malformed or an operator does not fit a field
     */
    public static <T> Predicate parseJsonColumn(String rsql, CriteriaBuilder cb, From<?, T> from,
                                                String jsonColumnField, JsonLayout layout) {
        return parseJsonColumn(rsql, cb, from, jsonColumnField, Map.of(), layout);
    }

    /**
     * Builds a predicate over the fields of a JSON document stored in one attribute.
     *
     * <p>Each selector is a JSON path below the document root ({@code price} or {@code $.price},
     * {@code dimensions.width}), extracted with {@code json_value}. Values are compared as text,
     * except fields with a numeric type, which are compared numerically. A field's type comes from
     * {@code fieldTypes}; without an entry, ordering operators ({@code =gt=} etc.) infer it from
     * the argument (ISO-8601 date-time, date or number) and all other operators compare text.
     * {@code Boolean} fields allow only {@code ==} and {@code !=}.
     *
     * <p><b>Requires Hibernate ORM 7</b> as JPA provider: JSON extraction and the numeric casts
     * use Hibernate-specific criteria functions.
     *
     * @param rsql            the query
     * @param cb              the criteria builder of the query the predicate is used in
     * @param from            the root or join that holds the JSON attribute
     * @param jsonColumnField name of the attribute that holds the JSON document
     * @param fieldTypes      Java types of JSON fields by selector, e.g. {@code Map.of("price",
     *                        Double.class)}; fields not listed are treated as described above
     * @param layout          how the JSON documents are laid out
     * @param <T>             the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query is malformed or an operator does not fit a field
     */
    public static <T> Predicate parseJsonColumn(String rsql, CriteriaBuilder cb, From<?, T> from,
                                                String jsonColumnField,
                                                Map<String, Class<?>> fieldTypes, JsonLayout layout) {
        return parseJsonColumn(rsql, cb, from, jsonColumnField, fieldTypes, layout, RsqlLimits.DEFAULTS);
    }

    /**
     * Builds a predicate over the fields of a JSON document stored in one attribute, like
     * {@link #parseJsonColumn(String, CriteriaBuilder, From, String, Map, JsonLayout)}, with other
     * limits on the query size.
     *
     * @param rsql            the query
     * @param cb              the criteria builder of the query the predicate is used in
     * @param from            the root or join that holds the JSON attribute
     * @param jsonColumnField name of the attribute that holds the JSON document
     * @param fieldTypes      Java types of JSON fields by selector
     * @param layout          how the JSON documents are laid out
     * @param limits          the limits the query must stay within
     * @param <T>             the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query exceeds a limit, is malformed or an operator does
     *                            not fit a field
     */
    public static <T> Predicate parseJsonColumn(String rsql, CriteriaBuilder cb, From<?, T> from,
                                                String jsonColumnField, Map<String, Class<?>> fieldTypes,
                                                JsonLayout layout, RsqlLimits limits) {
        Objects.requireNonNull(rsql, "rsql");
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(jsonColumnField, "jsonColumnField");
        Objects.requireNonNull(fieldTypes, "fieldTypes");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(limits, "limits");
        return new JsonColumnVisitor<>(cb, from, jsonColumnField, Map.copyOf(fieldTypes), layout)
                .visitInput(RsqlParsing.parse(rsql, limits));
    }
}
