package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.JsonLayout;
import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.internal.parser.RsqlParser;
import ch.nvaria.rsql.internal.parser.RsqlParserBaseVisitor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.sqm.NodeBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RSQL visitor that maps every selector to a json_value() extraction
 * from a single JSON column on the entity.
 *
 * <p>Type resolution order per selector:
 * <ol>
 *   <li>Explicit entry in {@code fieldTypes} map</li>
 *   <li>For ordering operators (GT/GE/LT/LE): auto-inferred from the
 *       argument value via {@link TypeCoercion#inferComparableType}</li>
 *   <li>Default: {@code String.class}</li>
 * </ol>
 *
 * <p>Boolean fields support only EQ / NEQ. String fields reject ordering
 * operators (GT/GE/LT/LE) with a clear {@link RsqlParseException}.
 */
public final class JsonColumnVisitor<T> extends RsqlParserBaseVisitor<Predicate> {

    private static final String[] AVRO_TYPE_KEYS = { "int", "long", "float", "double", "string", "boolean" };
    private static final String JSON_VALUE = "json_value";

    private final CriteriaBuilder cb;
    private final From<?, T> from;
    private final String jsonColumnField;
    private final Map<String, Class<?>> fieldTypes;
    private final JsonLayout layout;
    /**
     * The scalar JSON-extraction function to render. On PostgreSQL the built-in {@code json_value}
     * renders as {@code json_value(col, path RETURNING jsonb) #>> '{}'}, which misfires in filter
     * predicates over heterogeneous jsonb on PG 18.x; there we use {@code json_value_text}
     * (native {@code json_value(col, path)}) registered by {@link JsonValueFunctionContributor}.
     * Every other dialect keeps the built-in, which renders its own correct native form.
     */
    private final String jsonValueFn;

    public JsonColumnVisitor(CriteriaBuilder cb, From<?, T> from, String jsonColumnField,
                             Map<String, Class<?>> fieldTypes, JsonLayout layout) {
        this.cb = cb;
        this.from = from;
        this.jsonColumnField = jsonColumnField;
        this.fieldTypes = fieldTypes;
        this.layout = layout;
        this.jsonValueFn = isPostgres(cb) ? JsonValueFunctionContributor.JSON_VALUE_TEXT : JSON_VALUE;
    }

    private static boolean isPostgres(CriteriaBuilder cb) {
        return cb instanceof NodeBuilder nb
                && nb.getQueryEngine().getDialect() instanceof PostgreSQLDialect;
    }

    // --- tree traversal (identical to JpaCriteriaVisitor) ---

    @Override
    public Predicate visitInput(RsqlParser.InputContext ctx) {
        return visit(ctx.or());
    }

    @Override
    public Predicate visitOr(RsqlParser.OrContext ctx) {
        List<RsqlParser.AndContext> children = ctx.and();
        if (children.size() == 1) return visit(children.getFirst());
        return cb.or(children.stream().map(this::visit).toArray(Predicate[]::new));
    }

    @Override
    public Predicate visitAnd(RsqlParser.AndContext ctx) {
        List<RsqlParser.ConstraintContext> children = ctx.constraint();
        if (children.size() == 1) return visit(children.getFirst());
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
        try {
            return comparison(ctx);
        } catch (RsqlParseException e) {
            // name the comparison the error occurred in, for clients that point at it
            throw e.inComparison(ctx.selector().getText(), RsqlParsing.position(ctx.getStart()));
        }
    }

    private Predicate comparison(RsqlParser.ComparisonContext ctx) {
        String selector = ctx.selector().UNRESERVED_STR().getText();
        String jsonPath  = selector.startsWith("$.") ? selector : "$." + selector;

        RsqlOperator operator = RsqlOperator.fromText(ctx.operator().getText());
        List<Argument> args   = Argument.extract(ctx.arguments());
        boolean      ordering = isOrdering(operator);

        boolean      list     = operator == RsqlOperator.IN || operator == RsqlOperator.OUT;

        // A missing key and JSON null both extract as SQL NULL, so ==null matches either.
        // Lists handle the null keyword per argument, in buildPredicate.
        if (!list && args.getFirst().isNull()) {
            return switch (operator) {
                case EQ  -> cb.isNull(buildJsonValueExpr(jsonPath));
                case NEQ -> cb.isNotNull(buildJsonValueExpr(jsonPath));
                default  -> throw new RsqlParseException("Operator " + operator + " cannot be used with null");
            };
        }

        // resolve semantic type (for validation + numeric cast decisions)
        Class<?> semanticType = fieldTypes.getOrDefault(selector, null);
        if (semanticType == null) {
            semanticType = ordering
                ? TypeCoercion.inferComparableType(selector, args.getFirst().value())
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

        // json_value extraction always yields a text expression. Always extract as
        // String, then emit a real SQL CAST to the numeric type when needed so the
        // database compares numbers, not text vs. number.
        // Dates (ISO-8601) sort correctly as strings; booleans are compared as 'true'/'false'.
        Expression<String> strExpr = buildJsonValueExpr(jsonPath);

        if (isNumeric(semanticType)) {
            Expression<?> numExpr = castTo(strExpr, semanticType);
            return buildPredicate(operator, numExpr, semanticType, args);
        }

        // non-numeric: compare as plain string, bind raw values
        return buildPredicate(operator, strExpr, String.class, args);
    }

    private Expression<String> buildJsonValueExpr(String jsonPath) {
        if (layout == JsonLayout.PLAIN) {
            return cb.function(jsonValueFn, String.class,
                from.get(jsonColumnField), cb.literal(jsonPath));
        }
        // In the Avro layout, union-typed fields are wrapped: {"int": 500}. Try each Avro
        // primitive type key first, then fall back to the plain path for non-wrapped fields.
        CriteriaBuilder.Coalesce<String> coalesce = cb.coalesce();
        for (String key : AVRO_TYPE_KEYS) {
            coalesce.value(cb.function(jsonValueFn, String.class,
                from.get(jsonColumnField), cb.literal(jsonPath + "." + key)));
        }
        coalesce.value(cb.function(jsonValueFn, String.class,
            from.get(jsonColumnField), cb.literal(jsonPath)));
        return coalesce;
    }

    /**
     * Emits a real SQL {@code CAST(... AS <numeric>)}.
     *
     * <p>{@link Expression#as(Class)} is deliberately NOT used: per the JPA spec it only
     * changes the reported Java type and "does not cause type conversion", so no CAST is
     * rendered. The left side then stays text while the bound argument is a number,
     * producing {@code operator does not exist: text >= double precision} on PostgreSQL
     * (SQLState 42883). {@link HibernateCriteriaBuilder#cast} renders the actual CAST.
     */
    @SuppressWarnings("unchecked")
    private <N> Expression<N> castTo(Expression<String> strExpr, Class<N> numericType) {
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        return hcb.cast((JpaExpression<String>) strExpr, numericType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildPredicate(RsqlOperator op, Expression<?> expr,
                                     Class<?> type, List<Argument> args) {
        Argument first    = args.getFirst();
        boolean  wildcard = first.hasWildcard();

        return switch (op) {
            case EQ -> wildcard
                    ? cb.like(expr.as(String.class), first.likePattern(), Argument.LIKE_ESCAPE)
                    : cb.equal(expr, bindValue(first.value(), type));
            case NEQ -> wildcard
                    ? cb.notLike(expr.as(String.class), first.likePattern(), Argument.LIKE_ESCAPE)
                    : cb.notEqual(expr, bindValue(first.value(), type));
            case GT -> cb.greaterThan(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first.value(), type));
            case GE -> cb.greaterThanOrEqualTo(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first.value(), type));
            case LT -> cb.lessThan(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first.value(), type));
            case LE -> cb.lessThanOrEqualTo(
                    (Expression<Comparable>) expr,
                    (Comparable) bindValue(first.value(), type));
            case IN, OUT -> {
                // the null keyword adds IS NULL / IS NOT NULL: SQL's IN (..., NULL) never matches null
                boolean in = op == RsqlOperator.IN;
                List<Argument> values = Argument.values(args);
                Predicate nullCheck = in ? cb.isNull(expr) : cb.isNotNull(expr);
                if (values.isEmpty()) yield nullCheck;
                CriteriaBuilder.In<Object> list = cb.in(expr);
                values.stream().map(a -> bindValue(a.value(), type)).forEach(list::value);
                Predicate listCheck = in ? list : cb.not(list);
                if (values.size() == args.size()) yield listCheck;
                yield in ? cb.or(listCheck, nullCheck) : cb.and(listCheck, nullCheck);
            }
            case LIKE -> cb.like(expr.as(String.class), first.likePattern(), Argument.LIKE_ESCAPE);
            // the pattern is lowercased here rather than with lower() in SQL, so it stays a bound
            // parameter instead of a literal in the SQL text
            case ILIKE -> cb.like(cb.lower(expr.as(String.class)), first.likePattern().toLowerCase(Locale.ROOT),
                    Argument.LIKE_ESCAPE);
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
        if (isNumeric(type)) return TypeCoercion.coerce(raw, type);
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
}
