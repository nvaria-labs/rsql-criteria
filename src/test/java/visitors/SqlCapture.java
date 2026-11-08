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
