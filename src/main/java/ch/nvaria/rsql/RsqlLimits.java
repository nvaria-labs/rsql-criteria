package ch.nvaria.rsql;

/**
 * Upper bounds on the size of an RSQL query, checked before the query is parsed. A query over a
 * limit is rejected with an {@link RsqlParseException}.
 *
 * <p>They protect endpoints that accept queries from clients: deeply nested parentheses would
 * otherwise exhaust the parser's stack, and long {@code =in=} lists can exceed the database's
 * limit on bind parameters. The methods of {@link RsqlToCriteria} without a limits parameter use
 * {@link #DEFAULTS}.
 *
 * <pre>{@code
 * RsqlLimits limits = RsqlLimits.DEFAULTS.withMaxInArguments(1000);
 * }</pre>
 *
 * @param maxLength      the most characters a query may have
 * @param maxDepth       the deepest nesting of parentheses, counting the parentheses of
 *                       {@code =in=} / {@code =out=} lists
 * @param maxInArguments the most arguments of one {@code =in=} or {@code =out=} list
 */
public record RsqlLimits(int maxLength, int maxDepth, int maxInArguments) {

    /** 4096 characters, 16 levels of parentheses, 500 arguments per list. */
    public static final RsqlLimits DEFAULTS = new RsqlLimits(4096, 16, 500);

    /**
     * Validates the limits.
     *
     * @throws IllegalArgumentException if a limit is less than 1
     */
    public RsqlLimits {
        if (maxLength < 1 || maxDepth < 1 || maxInArguments < 1) {
            throw new IllegalArgumentException("Limits must be at least 1: " + this);
        }
    }

    /**
     * Returns these limits with another maximum length.
     *
     * @param maxLength the most characters a query may have
     * @return the new limits
     */
    public RsqlLimits withMaxLength(int maxLength) {
        return new RsqlLimits(maxLength, maxDepth, maxInArguments);
    }

    /**
     * Returns these limits with another maximum nesting depth.
     *
     * @param maxDepth the deepest nesting of parentheses
     * @return the new limits
     */
    public RsqlLimits withMaxDepth(int maxDepth) {
        return new RsqlLimits(maxLength, maxDepth, maxInArguments);
    }

    /**
     * Returns these limits with another maximum list size.
     *
     * @param maxInArguments the most arguments of one {@code =in=} or {@code =out=} list
     * @return the new limits
     */
    public RsqlLimits withMaxInArguments(int maxInArguments) {
        return new RsqlLimits(maxLength, maxDepth, maxInArguments);
    }
}
