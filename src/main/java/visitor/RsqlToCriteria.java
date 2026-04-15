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

package visitor;

import ch.nvaria.generated.RsqlLexer;
import ch.nvaria.generated.RsqlParser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Map;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public final class RsqlToCriteria {

    private RsqlToCriteria() {}

    public static <T> Predicate parse(String rsql, CriteriaBuilder cb, Root<T> root) {
        RsqlLexer lexer = new RsqlLexer(CharStreams.fromString(rsql));
        lexer.removeErrorListeners();

        RsqlParser parser = new RsqlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                throw new RsqlParseException(
                        "Syntax error at line " + line + ":" + charPositionInLine + " — " + msg, e);
            }
        });

        RsqlParser.InputContext tree = parser.input();
        return new JPACriteriaRsqlVistor<>(cb, root).visitInput(tree);
    }

    public static <T> Predicate parseJsonColumn(String rsql, CriteriaBuilder cb, From<?, T> from,
                                                String jsonColumnField) {
        return parseJsonColumn(rsql, cb, from, jsonColumnField, Map.of());
    }

    public static <T> Predicate parseJsonColumn(String rsql, CriteriaBuilder cb, From<?, T> from,
                                                String jsonColumnField,
                                                Map<String, Class<?>> fieldTypes) {
        RsqlLexer lexer = new RsqlLexer(CharStreams.fromString(rsql));
        lexer.removeErrorListeners();

        RsqlParser parser = new RsqlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                throw new RsqlParseException(
                        "Syntax error at line " + line + ":" + charPositionInLine + " — " + msg, e);
            }
        });

        RsqlParser.InputContext tree = parser.input();
        return new JsonColumnRsqlVisitor<>(cb, from, jsonColumnField, fieldTypes).visitInput(tree);
    }
}
