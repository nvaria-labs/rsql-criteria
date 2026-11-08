package ch.nvaria.rsql;

/**
 * How the documents in a JSON column are laid out.
 *
 * @see RsqlToCriteria#parseJsonColumn(String, jakarta.persistence.criteria.CriteriaBuilder,
 *      jakarta.persistence.criteria.From, String, JsonLayout)
 */
public enum JsonLayout {

    /** Plain JSON: a selector {@code price} reads {@code $.price}. */
    PLAIN,

    /**
     * JSON as written by Avro's JSON encoder, where values of union-typed fields are wrapped in an
     * object keyed by their type, e.g. {@code {"price": {"double": 9.5}}}. A selector {@code price}
     * reads the first present of {@code $.price.int}, {@code $.price.long}, {@code $.price.float},
     * {@code $.price.double}, {@code $.price.string}, {@code $.price.boolean} and {@code $.price}.
     */
    AVRO
}
