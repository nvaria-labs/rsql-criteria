package ch.nvaria.rsql;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Thrown when an RSQL query cannot be turned into a predicate: a syntax error, an unknown
 * selector, an unsupported operator for the selector's type, or an argument that cannot be
 * converted to the selector's type.
 *
 * <p>The message is meant for API clients and never names internal classes. Where they are known,
 * {@link #getSelector()} and {@link #getPosition()} say which comparison and which character of
 * the query the error is about, e.g. for a {@code ProblemDetail}:
 *
 * <pre>{@code
 * ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
 * e.getSelector().ifPresent(s -> problem.setProperty("selector", s));
 * e.getPosition().ifPresent(p -> problem.setProperty("position", p));
 * }</pre>
 */
public class RsqlParseException extends RuntimeException {

    private static final long serialVersionUID = 2L;

    private final String selector;
    private final Integer position;

    /**
     * Creates the exception with a message.
     *
     * @param message what is wrong with the query
     */
    public RsqlParseException(String message) {
        this(message, null, null, null);
    }

    /**
     * Creates the exception with a message and the error that caused it.
     *
     * @param message what is wrong with the query
     * @param cause   the underlying parser or conversion error
     */
    public RsqlParseException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    /**
     * Creates the exception with a message, its cause and where in the query the error is.
     *
     * @param message  what is wrong with the query
     * @param cause    the underlying parser or conversion error, or {@code null}
     * @param selector the selector of the comparison the error is about, or {@code null}
     * @param position the position of the error in the query, 1 for the first character, or
     *                 {@code null}
     */
    public RsqlParseException(String message, Throwable cause, String selector, Integer position) {
        super(message, cause);
        this.selector = selector;
        this.position = position;
    }

    /**
     * The selector of the comparison the error is about, e.g. {@code director.lastName}.
     *
     * @return the selector, or empty for errors that concern no single comparison
     */
    public Optional<String> getSelector() {
        return Optional.ofNullable(selector);
    }

    /**
     * Where in the query the error is: 1 for the first character. For errors about a comparison,
     * the position of its selector.
     *
     * @return the position, or empty if the error concerns the query as a whole
     */
    public OptionalInt getPosition() {
        return position == null ? OptionalInt.empty() : OptionalInt.of(position);
    }

    /**
     * Returns this error with the comparison it occurred in, unless it already names one.
     *
     * @param selector the selector of the comparison
     * @param position the position of the comparison in the query, 1 for the first character
     * @return this exception, or a copy with the selector and position set
     */
    public RsqlParseException inComparison(String selector, int position) {
        if (this.selector != null) return this;
        RsqlParseException located = new RsqlParseException(getMessage(), getCause(), selector, position);
        located.setStackTrace(getStackTrace());
        return located;
    }
}
