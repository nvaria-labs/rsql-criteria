package visitors;

import ch.nvaria.rsql.RsqlHidden;
import jakarta.persistence.Embeddable;

/** A filming location: an embeddable used as the element of a collection. */
@Embeddable
public class Location {

    private String city;
    private String country;

    @RsqlHidden
    private String permitNumber;

    public Location() {}

    public Location(String city, String country, String permitNumber) {
        this.city = city;
        this.country = country;
        this.permitNumber = permitNumber;
    }
}
