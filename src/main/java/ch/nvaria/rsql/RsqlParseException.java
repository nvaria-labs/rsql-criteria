/*
 * Copyright 2024-2026 Nvaria GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.nvaria.rsql;

/**
 * Thrown when an RSQL query cannot be turned into a predicate: a syntax error, an unknown
 * selector, an unsupported operator for the selector's type, or an argument that cannot be
 * converted to the selector's type.
 */
public class RsqlParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message what is wrong with the query
     */
    public RsqlParseException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and the error that caused it.
     *
     * @param message what is wrong with the query
     * @param cause   the underlying parser or conversion error
     */
    public RsqlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
