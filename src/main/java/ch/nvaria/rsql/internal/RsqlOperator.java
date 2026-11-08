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

package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.RsqlParseException;

import java.util.Set;

enum RsqlOperator {

    EQ(Set.of("==")),
    NEQ(Set.of("!=")),
    GT(Set.of("=gt=", ">")),
    GE(Set.of("=ge=", ">=")),
    LT(Set.of("=lt=", "<")),
    LE(Set.of("=le=", "<=")),
    IN(Set.of("=in=")),
    OUT(Set.of("=out=")),
    LIKE(Set.of("=like="));

    private final Set<String> aliases;

    RsqlOperator(Set<String> aliases) {
        this.aliases = aliases;
    }

    static RsqlOperator fromText(String text) {
        for (RsqlOperator op : values()) {
            if (op.aliases.contains(text)) {
                return op;
            }
        }
        throw new RsqlParseException("Unknown operator: " + text);
    }
}
