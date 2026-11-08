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

/**
 * Translates RSQL / FIQL query strings into JPA Criteria API predicates.
 *
 * <p>The entry point is {@link ch.nvaria.rsql.RsqlToCriteria}. Filtering inside JSON columns needs
 * Hibernate ORM on the classpath; filtering on entity attributes works with any JPA provider.
 */
module ch.nvaria.rsql {
    requires transitive jakarta.persistence;
    requires org.antlr.antlr4.runtime;
    // Only needed for JSON columns, which use Hibernate-specific functions and casts
    requires static org.hibernate.orm.core;

    exports ch.nvaria.rsql;

    provides org.hibernate.boot.model.FunctionContributor
            with ch.nvaria.rsql.internal.JsonValueFunctionContributor;
}
