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
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.ArrayList;
import java.util.List;

public class JPACriteriaRsqlVistor<T> extends RsqlParserBaseVisitor<Predicate> {

    private final CriteriaBuilder cb;
    private final Root<T> root;

    public JPACriteriaRsqlVistor(CriteriaBuilder cb, Root<T> root) {
        this.cb = cb;
        this.root = root;
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
        // 1. Resolve path via metamodel (validates each segment, supports dot notation)
        String selectorText = ctx.selector().UNRESERVED_STR().getText();
        Path path = root;
        ManagedType<?> currentType = root.getModel();
        for (String segment : selectorText.split("\\.")) {
            Attribute<?, ?> attr;
            try {
                attr = currentType.getAttribute(segment);
            } catch (IllegalArgumentException e) {
                throw new RsqlParseException(
                        "Unknown field '" + segment + "' on " + currentType.getJavaType().getSimpleName(), e);
            }
            path = path.get(segment);
            if (attr instanceof SingularAttribute<?, ?> sa && sa.isAssociation()) {
                currentType = (ManagedType<?>) sa.getType();
            }
        }

        // 2. Parse operator
        RsqlOperator operator = RsqlOperator.fromText(ctx.operator().getText());

        // 3. Extract raw argument strings
        List<String> rawArgs = extractArguments(ctx.arguments());

        // 4. Build predicate
        return buildPredicate(operator, path, rawArgs);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildPredicate(RsqlOperator operator, Path path, List<String> rawArgs) {
        String firstRaw = rawArgs.get(0);
        boolean hasWildcard = firstRaw.contains("*");

        return switch (operator) {
            case EQ -> hasWildcard
                    ? cb.like(path.as(String.class), firstRaw.replace("*", "%"))
                    : cb.equal(path, RsqlTypeCoercion.coerce(firstRaw, path.getJavaType()));
            case NEQ -> hasWildcard
                    ? cb.notLike(path.as(String.class), firstRaw.replace("*", "%"))
                    : cb.notEqual(path, RsqlTypeCoercion.coerce(firstRaw, path.getJavaType()));
            case GT -> cb.greaterThan(
                    (Expression<Comparable>) path,
                    (Comparable) RsqlTypeCoercion.coerce(firstRaw, path.getJavaType()));
            case GE -> cb.greaterThanOrEqualTo(
                    (Expression<Comparable>) path,
                    (Comparable) RsqlTypeCoercion.coerce(firstRaw, path.getJavaType()));
            case LT -> cb.lessThan(
                    (Expression<Comparable>) path,
                    (Comparable) RsqlTypeCoercion.coerce(firstRaw, path.getJavaType()));
            case LE -> cb.lessThanOrEqualTo(
                    (Expression<Comparable>) path,
                    (Comparable) RsqlTypeCoercion.coerce(firstRaw, path.getJavaType()));
            case IN -> {
                CriteriaBuilder.In<Object> in = cb.in(path);
                for (String raw : rawArgs) in.value(RsqlTypeCoercion.coerce(raw, path.getJavaType()));
                yield in;
            }
            case OUT -> {
                CriteriaBuilder.In<Object> in = cb.in(path);
                for (String raw : rawArgs) in.value(RsqlTypeCoercion.coerce(raw, path.getJavaType()));
                yield cb.not(in);
            }
            case LIKE -> cb.like(path.as(String.class), firstRaw.replace("*", "%"));
        };
    }

    private List<String> extractArguments(RsqlParser.ArgumentsContext ctx) {
        List<String> result = new ArrayList<>();
        if (ctx.commaSepArguments() != null) {
            for (RsqlParser.ArgumentContext arg : ctx.commaSepArguments().argument()) {
                result.add(stripQuotes(arg.getText()));
            }
        } else {
            result.add(stripQuotes(ctx.argument().getText()));
        }
        return result;
    }

    private static String stripQuotes(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            if ((first == '\'' || first == '"') && text.charAt(text.length() - 1) == first) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }
}
