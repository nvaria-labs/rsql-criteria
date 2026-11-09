package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.RsqlHidden;
import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.internal.parser.RsqlParser;
import ch.nvaria.rsql.internal.parser.RsqlParserBaseVisitor;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps each RSQL selector to an entity attribute path, validated against the JPA metamodel.
 */
public final class JpaCriteriaVisitor<T> extends RsqlParserBaseVisitor<Predicate> {

    private final CriteriaBuilder cb;
    private final CommonAbstractCriteria query;
    private final From<?, T> root;

    /**
     * @param query the query the predicate is for, needed for subqueries on collections; without
     *              it ({@code null}), selectors through collections are rejected
     */
    public JpaCriteriaVisitor(CriteriaBuilder cb, CommonAbstractCriteria query, From<?, T> root) {
        this.cb = cb;
        this.query = query;
        this.root = root;
    }

    /** The managed type behind a root (an entity) or a join (the joined attribute's type). */
    private static ManagedType<?> managedTypeOf(From<?, ?> from) {
        Bindable<?> model = from.getModel();
        if (model instanceof ManagedType<?> managed) return managed;
        if (model instanceof SingularAttribute<?, ?> sa && sa.getType() instanceof ManagedType<?> managed) {
            return managed;
        }
        if (model instanceof PluralAttribute<?, ?, ?> pa && pa.getElementType() instanceof ManagedType<?> managed) {
            return managed;
        }
        throw new IllegalArgumentException("Not an entity or embeddable path: " + from.getJavaType().getName());
    }

    @Override
    public Predicate visitInput(RsqlParser.InputContext ctx) {
        return visit(ctx.or());
    }

    @Override
    public Predicate visitOr(RsqlParser.OrContext ctx) {
        List<RsqlParser.AndContext> children = ctx.and();
        if (children.size() == 1) return visit(children.get(0));
        Predicate[] predicates = children.stream().map(this::visit).toArray(Predicate[]::new);
        return cb.or(predicates);
    }

    @Override
    public Predicate visitAnd(RsqlParser.AndContext ctx) {
        List<RsqlParser.ConstraintContext> children = ctx.constraint();
        if (children.size() == 1) return visit(children.get(0));
        Predicate[] predicates = children.stream().map(this::visit).toArray(Predicate[]::new);
        return cb.and(predicates);
    }

    @Override
    public Predicate visitConstraint(RsqlParser.ConstraintContext ctx) {
        if (ctx.group() != null) return visit(ctx.group());
        return visit(ctx.comparison());
    }

    @Override
    public Predicate visitGroup(RsqlParser.GroupContext ctx) {
        return visit(ctx.or());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate visitComparison(RsqlParser.ComparisonContext ctx) {
        try {
            return comparison(ctx);
        } catch (RsqlParseException e) {
            // name the comparison the error occurred in, for clients that point at it
            throw e.inComparison(ctx.selector().getText(), RsqlParsing.position(ctx.getStart()));
        }
    }

    @SuppressWarnings({"rawtypes"})
    private Predicate comparison(RsqlParser.ComparisonContext ctx) {
        String selector = ctx.selector().UNRESERVED_STR().getText();
        List<Attribute<?, ?>> attributes = walk(selector, root, query != null);
        RsqlOperator operator = RsqlOperator.fromText(ctx.operator().getText());
        List<Argument> args = Argument.extract(ctx.arguments());

        int lastCollection = -1;
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.get(i).isCollection()) lastCollection = i;
        }
        if (lastCollection < 0) {
            Path path = root;
            for (Attribute<?, ?> attribute : attributes) path = path.get(attribute.getName());
            return buildPredicate(operator, path, args);
        }
        return collectionPredicate(selector, attributes, lastCollection, operator, args);
    }

    /**
     * A comparison through a collection, as a subquery correlated with {@link #root}: "some
     * element matches" ({@code EXISTS}). The negative operators {@code !=} and {@code =out=} mean
     * "no element matches the positive form" ({@code NOT EXISTS}). On the collection itself,
     * {@code ==null} / {@code !=null} test whether it is empty.
     */
    @SuppressWarnings({"rawtypes"})
    private Predicate collectionPredicate(String selector, List<Attribute<?, ?>> attributes, int lastCollection,
                                          RsqlOperator operator, List<Argument> args) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        subquery.select(cb.literal(1));
        From<?, ?> from = correlate(subquery, root);
        for (int i = 0; i <= lastCollection; i++) from = from.join(attributes.get(i).getName());
        Path path = from;
        for (int i = lastCollection + 1; i < attributes.size(); i++) path = path.get(attributes.get(i).getName());

        boolean atCollection = lastCollection == attributes.size() - 1;
        if (atCollection && args.stream().anyMatch(Argument::isNull)) {
            return switch (operator) {
                case EQ  -> cb.not(cb.exists(subquery));
                case NEQ -> cb.exists(subquery);
                default  -> throw new RsqlParseException("Use " + selector + "==null or " + selector
                        + "!=null to test whether the collection is empty");
            };
        }

        RsqlOperator positive = switch (operator) {
            case NEQ -> RsqlOperator.EQ;
            case OUT -> RsqlOperator.IN;
            default  -> operator;
        };
        subquery.where(buildPredicate(positive, path, args));
        Predicate exists = cb.exists(subquery);
        return positive == operator ? exists : cb.not(exists);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static From<?, ?> correlate(Subquery<?> subquery, From<?, ?> from) {
        if (from instanceof Root r) return subquery.correlate(r);
        if (from instanceof Join j) return subquery.correlate(j);
        throw new IllegalArgumentException("Cannot correlate " + from.getClass().getName());
    }

    /**
     * Resolves a dotted attribute path against {@code from}, checking every segment against the
     * metamodel: unknown and {@link RsqlHidden} attributes are rejected alike, as are collections
     * and segments after a basic attribute. Used for paths that are not filters, such as sort
     * properties, so collections are never allowed.
     *
     * @throws RsqlParseException if the path cannot be resolved
     */
    @SuppressWarnings({"rawtypes"})
    public static Path<?> resolvePath(String selectorText, From<?, ?> from) {
        Path path = from;
        for (Attribute<?, ?> attribute : walk(selectorText, from, false)) path = path.get(attribute.getName());
        return path;
    }

    /**
     * Checks a dotted path segment by segment against the metamodel and returns its attributes.
     *
     * @param collections whether segments may be collections; map attributes never may
     * @throws RsqlParseException if a segment is unknown, hidden, a disallowed collection, or
     *                            follows a basic attribute
     */
    private static List<Attribute<?, ?>> walk(String selectorText, From<?, ?> from, boolean collections) {
        List<Attribute<?, ?>> attributes = new ArrayList<>();
        ManagedType<?> currentType = managedTypeOf(from);
        Attribute<?, ?> previous = null;
        for (String segment : selectorText.split("\\.", -1)) {
            if (currentType == null) {
                throw new RsqlParseException("Field '" + previous.getName() + "' on "
                        + previous.getDeclaringType().getJavaType().getSimpleName() + " has no fields");
            }
            Attribute<?, ?> attr;
            try {
                attr = currentType.getAttribute(segment);
            } catch (IllegalArgumentException e) {
                throw unknownField(segment, currentType, e);
            }
            // hidden attributes look exactly like unknown ones, so clients cannot probe for them
            if (isHidden(attr)) throw unknownField(segment, currentType, null);
            Type<?> next;
            if (attr instanceof PluralAttribute<?, ?, ?> plural) {
                if (!collections || plural.getCollectionType() == PluralAttribute.CollectionType.MAP) {
                    throw new RsqlParseException("Field '" + segment + "' on "
                            + currentType.getJavaType().getSimpleName() + " is a collection and cannot be used");
                }
                next = plural.getElementType();
            } else {
                next = ((SingularAttribute<?, ?>) attr).getType();
            }
            // entities and embeddables can be navigated further, basic values cannot
            currentType = next instanceof ManagedType<?> managed ? managed : null;
            attributes.add(attr);
            previous = attr;
        }
        return attributes;
    }

    private static RsqlParseException unknownField(String segment, ManagedType<?> type, Throwable cause) {
        return new RsqlParseException(
                "Unknown field '" + segment + "' on " + type.getJavaType().getSimpleName(), cause);
    }

    /**
     * Whether the attribute is annotated with {@link RsqlHidden}: on the member the provider maps
     * (field or getter) or, for property access, on the field of the same name.
     */
    static boolean isHidden(Attribute<?, ?> attr) {
        Member member = attr.getJavaMember();
        if (member instanceof AnnotatedElement element && element.isAnnotationPresent(RsqlHidden.class)) {
            return true;
        }
        if (member instanceof Method getter) {
            try {
                return getter.getDeclaringClass().getDeclaredField(attr.getName())
                        .isAnnotationPresent(RsqlHidden.class);
            } catch (NoSuchFieldException e) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildPredicate(RsqlOperator operator, Path path, List<Argument> args) {
        Argument first = args.getFirst();
        Class<?> type = path.getJavaType();

        if (operator == RsqlOperator.IN || operator == RsqlOperator.OUT) {
            return buildListPredicate(operator, path, type, args);
        }
        if (first.isNull()) {
            return switch (operator) {
                case EQ  -> cb.isNull(path);
                case NEQ -> cb.isNotNull(path);
                default  -> throw new RsqlParseException("Operator " + operator + " cannot be used with null");
            };
        }

        return switch (operator) {
            case EQ -> first.hasWildcard()
                    ? cb.like(path.as(String.class), first.likePattern(), Argument.LIKE_ESCAPE)
                    : cb.equal(path, TypeCoercion.coerce(first.value(), type));
            case NEQ -> first.hasWildcard()
                    ? cb.notLike(path.as(String.class), first.likePattern(), Argument.LIKE_ESCAPE)
                    : cb.notEqual(path, TypeCoercion.coerce(first.value(), type));
            case GT -> cb.greaterThan(
                    (Expression<Comparable>) path,
                    (Comparable) TypeCoercion.coerce(first.value(), type));
            case GE -> cb.greaterThanOrEqualTo(
                    (Expression<Comparable>) path,
                    (Comparable) TypeCoercion.coerce(first.value(), type));
            case LT -> cb.lessThan(
                    (Expression<Comparable>) path,
                    (Comparable) TypeCoercion.coerce(first.value(), type));
            case LE -> cb.lessThanOrEqualTo(
                    (Expression<Comparable>) path,
                    (Comparable) TypeCoercion.coerce(first.value(), type));
            case IN, OUT -> throw new IllegalStateException("handled by buildListPredicate");
            case LIKE -> cb.like(path.as(String.class), first.likePattern(), Argument.LIKE_ESCAPE);
            // the pattern is lowercased here rather than with lower() in SQL, so it stays a bound
            // parameter instead of a literal in the SQL text
            case ILIKE -> cb.like(cb.lower(path.as(String.class)), first.likePattern().toLowerCase(Locale.ROOT),
                    Argument.LIKE_ESCAPE);
        };
    }

    /**
     * {@code =in=} / {@code =out=}. The {@code null} keyword in the list adds {@code IS NULL} /
     * {@code IS NOT NULL}, since SQL's {@code IN (..., NULL)} never matches null.
     */
    private Predicate buildListPredicate(RsqlOperator operator, Expression<?> path, Class<?> type,
                                         List<Argument> args) {
        boolean in = operator == RsqlOperator.IN;
        List<Argument> values = Argument.values(args);
        Predicate nullCheck = in ? cb.isNull(path) : cb.isNotNull(path);
        if (values.isEmpty()) return nullCheck;

        CriteriaBuilder.In<Object> list = cb.in(path);
        for (Argument arg : values) list.value(TypeCoercion.coerce(arg.value(), type));
        Predicate listCheck = in ? list : cb.not(list);
        if (values.size() == args.size()) return listCheck;
        return in ? cb.or(listCheck, nullCheck) : cb.and(listCheck, nullCheck);
    }
}
