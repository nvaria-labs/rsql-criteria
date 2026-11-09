package visitors;

import ch.nvaria.rsql.RsqlHidden;
import jakarta.persistence.Embeddable;

@Embeddable
public class HiddenBudget {

    private String currency;

    @RsqlHidden
    private long amount;

    public HiddenBudget() {}

    public HiddenBudget(String currency, long amount) {
        this.currency = currency;
        this.amount = amount;
    }
}
