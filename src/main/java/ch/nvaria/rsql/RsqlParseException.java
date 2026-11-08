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
