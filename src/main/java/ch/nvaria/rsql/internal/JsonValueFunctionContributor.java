package ch.nvaria.rsql.internal;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers {@code json_value_text} on PostgreSQL only. Auto-discovered by Hibernate via the
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor} service file.
 *
 * <p><b>Why only PostgreSQL.</b> Hibernate renders its built-in {@code json_value} as
 * {@code json_value(col, path RETURNING jsonb) #>> '{}'} <em>on the PostgreSQL dialect</em>. That
 * form returns wrong results inside a filter predicate over a heterogeneous {@code jsonb} table on
 * PostgreSQL&nbsp;18.x: a row that matches when scanned alone is dropped within the full-table scan
 * (the extracted value projects correctly, but the equality predicate misfires). The native
 * {@code json_value(col, path)} form (default {@code RETURNING varchar}) is unaffected, so on
 * PostgreSQL {@link JsonColumnVisitor} calls this {@code json_value_text} instead.
 *
 * <p>The buggy {@code RETURNING jsonb} rendering is unique to Hibernate's PostgreSQL dialect, so no
 * other database needs this. On every other dialect (H2, MySQL, MSSQL, Oracle, DB2, …) the visitor
 * keeps calling the built-in {@code json_value}, which each dialect renders with its own native
 * {@code JSON_VALUE} (or emulation, e.g. H2's {@code (col format json)."path"}) — all of which return
 * text directly and are correct. Hence this contributor deliberately registers nothing for them.
 */
public class JsonValueFunctionContributor implements FunctionContributor {

    /** Name the visitor uses on PostgreSQL; kept in sync with {@link JsonColumnVisitor}. */
    static final String JSON_VALUE_TEXT = "json_value_text";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        if (!(functionContributions.getDialect() instanceof PostgreSQLDialect)) {
            return;
        }
        // Native JSON_VALUE with the standard default RETURNING varchar — no jsonb round-trip.
        functionContributions.getFunctionRegistry().registerPattern(
                JSON_VALUE_TEXT,
                "json_value(?1,?2)",
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.STRING));
    }
}
