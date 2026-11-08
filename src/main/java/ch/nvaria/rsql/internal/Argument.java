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

package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.internal.parser.RsqlParser;

import java.util.List;

/**
 * One argument of a comparison, as written in the query but without its enclosing quotes.
 *
 * <p>In quoted and unquoted arguments alike, a backslash escapes the next character: {@code \'}
 * and {@code \"} for quotes, {@code \*} for a literal star, {@code \\} for a backslash. An
 * unescaped {@code *} is a wildcard where wildcards apply.
 *
 * @param raw    the argument text without enclosing quotes, escapes still in place
 * @param quoted whether the argument was enclosed in quotes
 */
record Argument(String raw, boolean quoted) {

    /** Escape character of the LIKE patterns built by {@link #likePattern()}. */
    static final char LIKE_ESCAPE = '\\';

    static List<Argument> extract(RsqlParser.ArgumentsContext ctx) {
        List<RsqlParser.ArgumentContext> args = ctx.commaSepArguments() != null
                ? ctx.commaSepArguments().argument()
                : List.of(ctx.argument());
        return args.stream().map(a -> of(a.getText())).toList();
    }

    static Argument of(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            if ((first == '\'' || first == '"') && text.charAt(text.length() - 1) == first) {
                return new Argument(text.substring(1, text.length() - 1), true);
            }
        }
        return new Argument(text, false);
    }

    /** The {@code null} keyword: unquoted, any case. {@code 'null'} is the string "null". */
    boolean isNull() {
        return !quoted && raw.equalsIgnoreCase("null");
    }

    /** Whether the argument contains an unescaped {@code *}. */
    boolean hasWildcard() {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\') i++;
            else if (c == '*') return true;
        }
        return false;
    }

    /** The argument with escapes resolved, for exact comparisons. */
    String value() {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) c = raw.charAt(++i);
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * The argument as a LIKE pattern with {@link #LIKE_ESCAPE} as escape character: unescaped
     * {@code *} become {@code %}; literal {@code %}, {@code _} and backslashes are escaped, so they
     * match only themselves on every database.
     */
    String likePattern() {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '*') {
                sb.append('%');
                continue;
            }
            if (c == '\\' && i + 1 < raw.length()) c = raw.charAt(++i);
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) sb.append(LIKE_ESCAPE);
            sb.append(c);
        }
        return sb.toString();
    }
}
