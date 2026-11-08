package visitors;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every SQL statement Hibernate sends to the JDBC driver so tests can
 * assert on the rendered SQL (e.g. that a real {@code cast(...)} is emitted).
 * Registered via {@code hibernate.session_factory.statement_inspector}.
 */
public class SqlCapture implements StatementInspector {

    private static final List<String> STATEMENTS = new ArrayList<>();

    @Override
    public String inspect(String sql) {
        synchronized (STATEMENTS) {
            STATEMENTS.add(sql);
        }
        return sql;
    }

    static void clear() {
        synchronized (STATEMENTS) {
            STATEMENTS.clear();
        }
    }

    static List<String> statements() {
        synchronized (STATEMENTS) {
            return new ArrayList<>(STATEMENTS);
        }
    }
}
