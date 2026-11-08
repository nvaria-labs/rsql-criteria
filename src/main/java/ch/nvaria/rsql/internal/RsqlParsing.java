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

import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.internal.parser.RsqlLexer;
import ch.nvaria.rsql.internal.parser.RsqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Parses an RSQL string into a syntax tree, turning every lexer or parser error into an
 * {@link RsqlParseException}.
 */
public final class RsqlParsing {

    private static final BaseErrorListener THROWING = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new RsqlParseException(
                    "Syntax error at line " + line + ":" + charPositionInLine + " — " + msg, e);
        }
    };

    private RsqlParsing() {}

    public static RsqlParser.InputContext parse(String rsql) {
        RsqlLexer lexer = new RsqlLexer(CharStreams.fromString(rsql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(THROWING);

        RsqlParser parser = new RsqlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(THROWING);
        return parser.input();
    }
}
