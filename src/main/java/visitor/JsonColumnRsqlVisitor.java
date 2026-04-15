/*
 * Copyright 2026 Nvaria GmbH.
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

package visitor;

import ch.nvaria.generated.RsqlParser;
import ch.nvaria.generated.RsqlParserBaseVisitor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RSQL visitor that maps every selector to a json_value() extraction
 * from a single JSON column on the entity.
 *
 * <p>Type resolution order per selector:
 * <ol>
 *   <li>Explicit entry in {@code fieldTypes} map</li>
 *   <li>For ordering operators (GT/GE/LT/LE): auto-inferred from the
 *       argument value via {@link RsqlTypeCoercion#inferComparableType}</li>
 *   <li>Default: {@code String.class}</li>
 * </ol>
 *
 * <p>Boolean fields support only EQ / NEQ. String fields reject ordering
 * operators (GT/GE/LT/LE) with a clear {@link RsqlParseException}.
 */
public class JsonColumnRsqlVisitor<T> extends RsqlParserBaseVisitor<Predicate> {

    private final CriteriaBuilder cb;
    private final From<?, T> from;
    private final String jsonColumnField;
    private final Map<String, Class<?>> fieldTypes;

    public JsonColumnRsqlVisitor(CriteriaBuilder cb, From<?, T> from,
                                 String jsonColumnField,
                                 Map<String, Class<?>> fieldTypes) {
        this.cb = cb;
        this.from = from;
        this.jsonColumnField = jsonColumnField;
        this.fieldTypes = fieldTypes;
    }

    public JsonColumnRsqlVisitor(CriteriaBuilder cb, From<?, T> from, String jsonColumnField) {
        this(cb, from, jsonColumnField, Map.of());
    }

    // --- tree traversal (identical to JPACriteriaRsqlVistor) ---

    @Override
    public Predicate visitInput(RsqlParser.InputContext ctx) {
        return visit(ctx.or());
    }

    @Override
    public Predicate visitOr(RsqlParser.OrContext ctx) {
        List<RsqlParser.AndContext> children = ctx.and();
        if (children.size() == 1) return visit(children.get(0));
        return cb.or(children.stream().map(this::visit).toArray(Predicate[]::new));
    }

    @Override
    public Predicate visitAnd(RsqlParser.AndContext ctx) {
        List<RsqlParser.ConstraintContext> children = ctx.constraint();
        if (children.size() == 1) return visit(children.get(0));
        return cb.and(children.stream().map(this::visit).toArray(Predicate[]::new));
    }

    @Override
    public Predicate visitConstraint(RsqlParser.ConstraintContext ctx) {
        return ctx.group() != null ? visit(ctx.group()) : visit(ctx.comparison());
    }

    @Override
    public Predicate visitGroup(RsqlParser.GroupContext ctx) {
        return visit(ctx.or());
    }

    // --- core comparison ---

    @Override
    public Predicate visitComparison(RsqlParser.ComparisonContext ctx) {
        String selector = ctx.selector().UNRESERVED_STR().getText();
        String jsonPath  = selector.startsWith("$.") ? selector : "$." + selector;

        RsqlOperator operator = RsqlOperator.fromText(ctx.operator().getText());
        List<String> rawArgs  = extractArguments(ctx.arguments());
        boolean      ordering = isOrdering(operator);

        // resolve semantic type (for validation + numeric cast decisions)
        Class<?> semanticType = fieldTypes.getOrDefault(selector, null);
        if (semanticType == null) {
            semanticType = ordering
                ? RsqlTypeCoercion.inferComparableType(selector, rawArgs.getFirst())
                : String.class;
        }

        // validate operator against type
        if (semanticType == Boolean.class
                && operator != RsqlOperator.EQ && operator != RsqlOperator.NEQ) {
            throw new RsqlParseException(
                "Field '" + selector + "' (Boolean): only == and != are supported");
        }
        if (ordering && semanticType == String.class) {
            throw new RsqlParseException(
                "Field '" + selector + "': ordering operators require a numeric or date type, got String");
        }

        // Hibernate renders json_value as a VARCHAR expression on H2 regardless of
        // the requested Java type. Always extract as String, then CAST to the numeric
        // type when needed so H2 can do proper numeric comparisons.
        // Dates (ISO-8601) sort correctly as strings; booleans are compared as 'true'/'false'.
        Expression<String> strExpr = cb.function(
            "json_value", String.class,
            from.get(jsonColumnField),
            cb.literal(jsonPath)
        );

        if (isNumeric(semanticType)) {
            Expression<?> numExpr = strExpr.as(semanticType);
            return buildPredicate(operator, numExpr, semanticType, rawArgs);
        }

        // non-numeric: compare as plain string, bind raw values
        return buildPredicate(operator, strExpr, String.class, rawArgs);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildPredicate(RsqlOperator op, Expression<?> expr,
                                     Class<?> type, List<String> args) {
        String  first    = args.get(0);
        boolean wildcard = first.contains("*");

        return switch (op) {
            case EQ -> wildcard
                    ? cb.like(expr.as(String.class), first.replace("*", "%"))
                    : cb.equal(expr, bindValue(first, type));
            case NEQ -> wildcard
                    ? cb.notLike(expr.as(String.class), first.replace("*", "%"))
                    : cb.notEqual(expr, bindValue(first, type));
            case GT -> cb.greaterThan(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first, type));
            case GE -> cb.greaterThanOrEqualTo(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first, type));
            case LT -> cb.lessThan(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first, type));
            case LE -> cb.lessThanOrEqualTo(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first, type));
            case IN -> {
                CriteriaBuilder.In<Object> in = cb.in(expr);
                args.stream().map(a -> bindValue(a, type)).forEach(in::value);
                yield in;
            }
            case OUT -> {
                CriteriaBuilder.In<Object> in = cb.in(expr);
                args.stream().map(a -> bindValue(a, type)).forEach(in::value);
                yield cb.not(in);
            }
            case LIKE -> cb.like(expr.as(String.class), first.replace("*", "%"));
        };
    }

    /**
     * Returns the bind value for a parameter.
     * Numeric types are coerced to their Java type (Double, Integer, etc.) so that
     * the CAST expression in SQL receives a properly-typed parameter.
     * Everything else (String, dates, booleans) is bound as a raw String because
     * the JSON extraction always produces VARCHAR on H2.
     */
    private static Object bindValue(String raw, Class<?> type) {
        if (isNumeric(type)) return RsqlTypeCoercion.coerce(raw, type);
        return raw; // dates (ISO sorts correctly), booleans ("true"/"false"), strings
    }

    private static boolean isNumeric(Class<?> type) {
        return type == Double.class   || type == Float.class
            || type == Integer.class  || type == Long.class
            || type == java.math.BigDecimal.class;
    }

    // --- helpers ---

    private static boolean isOrdering(RsqlOperator op) {
        return switch (op) { case GT, GE, LT, LE -> true; default -> false; };
    }

    private List<String> extractArguments(RsqlParser.ArgumentsContext ctx) {
        List<String> result = new ArrayList<>();
        if (ctx.commaSepArguments() != null)
            ctx.commaSepArguments().argument().forEach(a -> result.add(stripQuotes(a.getText())));
        else
            result.add(stripQuotes(ctx.argument().getText()));
        return result;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            char f = s.charAt(0);
            if ((f == '\'' || f == '"') && s.charAt(s.length() - 1) == f)
                return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
