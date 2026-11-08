/*
 * Copyright 2024 Nvaria GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.nvaria.rsql;

import ch.nvaria.rsql.internal.JpaCriteriaVisitor;
import ch.nvaria.rsql.internal.JsonColumnVisitor;
import ch.nvaria.rsql.internal.RsqlParsing;
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
 *       {@code =like=}</li>
 *   <li>Lists for {@code =in=} and {@code =out=}: {@code genre=in=(drama,comedy)}</li>
 *   <li>Logical AND: {@code ;} or {@code  and }; logical OR: {@code ,} or {@code  or };
 *       AND binds tighter than OR; parentheses group</li>
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
 * {@code null}. The class is stateless and thread-safe.
 */
public final class RsqlToCriteria {

    private RsqlToCriteria() {}

    /**
     * Builds a predicate over entity attributes.
     *
     * <p>Selectors name attributes of {@code from}; dots navigate to-one associations and
     * embeddables ({@code director.lastName}). Every segment is checked against the JPA metamodel,
     * and arguments are converted to the attribute's Java type (numbers, {@code BigDecimal},
     * {@code Boolean}, {@code UUID}, enums, {@code LocalDate}, {@code LocalDateTime},
     * {@code OffsetDateTime} in ISO-8601 format, strings).
     *
     * @param rsql the query
     * @param cb   the criteria builder of the query the predicate is used in
     * @param from the root or join the selectors are resolved against
     * @param <T>  the entity type of {@code from}
     * @return the predicate
     * @throws RsqlParseException if the query is malformed, names an unknown attribute or has an
     *                            argument that does not fit the attribute's type
     */
    public static <T> Predicate parse(String rsql, CriteriaBuilder cb, From<?, T> from) {
        Objects.requireNonNull(rsql, "rsql");
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(from, "from");
        return new JpaCriteriaVisitor<>(cb, from).visitInput(RsqlParsing.parse(rsql));
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
        Objects.requireNonNull(rsql, "rsql");
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(jsonColumnField, "jsonColumnField");
        Objects.requireNonNull(fieldTypes, "fieldTypes");
        Objects.requireNonNull(layout, "layout");
        return new JsonColumnVisitor<>(cb, from, jsonColumnField, Map.copyOf(fieldTypes), layout)
                .visitInput(RsqlParsing.parse(rsql));
    }
}
