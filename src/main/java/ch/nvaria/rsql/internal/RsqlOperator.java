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
    LIKE(Set.of("=like=")),
    ILIKE(Set.of("=ilike="));

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
