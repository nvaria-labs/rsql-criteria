package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.RsqlLimits;
import ch.nvaria.rsql.RsqlParseException;
import ch.nvaria.rsql.internal.parser.RsqlLexer;
import ch.nvaria.rsql.internal.parser.RsqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses an RSQL string into a syntax tree, turning every lexer or parser error into an
 * {@link RsqlParseException}. The query is checked against {@link RsqlLimits} on the token
 * level first, so oversized input never reaches the recursive parser.
 */
public final class RsqlParsing {

    private RsqlParsing() {}

    public static RsqlParser.InputContext parse(String rsql) {
        return parse(rsql, RsqlLimits.DEFAULTS);
    }

    public static RsqlParser.InputContext parse(String rsql, RsqlLimits limits) {
        if (rsql.length() > limits.maxLength()) {
            throw new RsqlParseException("Query too long: " + rsql.length()
                    + " characters, at most " + limits.maxLength() + " allowed");
        }

        RsqlLexer lexer = new RsqlLexer(CharStreams.fromString(rsql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexerErrors(rsql));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        checkStructure(tokens.getTokens(), limits);

        RsqlParser parser = new RsqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(PARSER_ERRORS);
        return parser.input();
    }

    /** Checks nesting depth and list sizes; the lexer is not recursive, so this is safe on any input. */
    private static void checkStructure(List<Token> tokens, RsqlLimits limits) {
        int depth = 0;
        int listArguments = -1;   // -1: not inside an argument list
        Token selector = null;
        Token previous = null;
        for (Token token : tokens) {
            switch (token.getType()) {
                case RsqlLexer.LPAREN -> {
                    if (++depth > limits.maxDepth()) {
                        throw new RsqlParseException(
                                "Query nested too deeply: at most " + limits.maxDepth() + " levels of parentheses allowed");
                    }
                    if (previous != null && isOperator(previous)) listArguments = 1;
                }
                case RsqlLexer.RPAREN -> {
                    depth--;
                    listArguments = -1;
                }
                case RsqlLexer.COMMA -> {
                    if (listArguments >= 0 && ++listArguments > limits.maxInArguments()) {
                        String name = selector == null ? "?" : selector.getText();
                        throw new RsqlParseException("Too many arguments for '" + name + "': at most "
                                + limits.maxInArguments() + " allowed", null,
                                selector == null ? null : name, selector == null ? null : position(selector));
                    }
                }
                case RsqlLexer.UNRESERVED_STR -> {
                    if (listArguments < 0 && (previous == null || !isOperator(previous))) selector = token;
                }
                default -> { }
            }
            previous = token;
        }
    }

    /** 1-based position of a token in the query. */
    static int position(Token token) {
        return token.getStartIndex() + 1;
    }

    /** Lexer errors: a character no token can start with, or a quote that is never closed. */
    private static final class LexerErrors extends BaseErrorListener {
        private final String rsql;

        LexerErrors(String rsql) {
            this.rsql = rsql;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            int index = ((Lexer) recognizer)._tokenStartCharIndex;
            char c = index < rsql.length() ? rsql.charAt(index) : ' ';
            String message = c == '\'' || c == '"'
                    ? "Unclosed quote at position " + (index + 1)
                    : "Unexpected character '" + c + "' at position " + (index + 1);
            throw new RsqlParseException(message, e, null, index + 1);
        }
    }

    /** Parser errors, worded as "Unexpected X at position N, expected Y". */
    private static final BaseErrorListener PARSER_ERRORS = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            Token token = (Token) offendingSymbol;
            // 'and' / 'or' tokens include the whitespace around them; point at the word itself
            String text = token.getText();
            String word = text.strip();
            String found = token.getType() == Token.EOF ? "end of query" : "'" + word + "'";
            String expected = describe(((Parser) recognizer).getExpectedTokens());
            int position = token.getType() == Token.EOF
                    ? position(token)
                    : position(token) + text.length() - text.stripLeading().length();
            throw new RsqlParseException("Unexpected " + found + " at position " + position
                    + (expected.isEmpty() ? "" : ", expected " + expected), e, null, position);
        }
    };

    /** The expected tokens in words, e.g. "a value" or "';', ',' or end of query". */
    private static String describe(IntervalSet expected) {
        Set<String> words = new LinkedHashSet<>();
        if (expected.contains(RsqlLexer.SINGLE_QUOTED_STR) || expected.contains(RsqlLexer.DOUBLE_QUOTED_STR)) {
            words.add("a value");
        } else if (expected.contains(RsqlLexer.UNRESERVED_STR)) {
            words.add("a field name");
        }
        if (expected.contains(RsqlLexer.COMP_FIQL) || expected.contains(RsqlLexer.COMP_ALT)) {
            words.add("an operator such as == or =gt=");
        }
        if (expected.contains(RsqlLexer.LPAREN)) words.add("'('");
        if (expected.contains(RsqlLexer.SEMICOLON) || expected.contains(RsqlLexer.AND)) words.add("';'");
        if (expected.contains(RsqlLexer.COMMA) || expected.contains(RsqlLexer.OR)) words.add("','");
        if (expected.contains(RsqlLexer.RPAREN)) words.add("')'");
        if (expected.contains(Token.EOF)) words.add("end of query");
        List<String> list = List.copyOf(words);
        if (list.size() <= 1) return list.isEmpty() ? "" : list.getFirst();
        return String.join(", ", list.subList(0, list.size() - 1)) + " or " + list.getLast();
    }

    private static boolean isOperator(Token token) {
        return token.getType() == RsqlLexer.COMP_FIQL || token.getType() == RsqlLexer.COMP_ALT;
    }
}
