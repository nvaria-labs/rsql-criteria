package visitors;

import ch.nvaria.rsql.RsqlHidden;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "hidden_film")
public class HiddenFilm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @RsqlHidden
    private String internalNotes;

    @ManyToOne
    private HiddenStudio studio;

    @RsqlHidden
    @ManyToOne
    private HiddenStudio distributor;

    @Embedded
    private HiddenBudget budget;

    public HiddenFilm() {}

    public HiddenFilm(String name, String internalNotes, HiddenStudio studio, HiddenStudio distributor,
                      HiddenBudget budget) {
        this.name = name;
        this.internalNotes = internalNotes;
        this.studio = studio;
        this.distributor = distributor;
        this.budget = budget;
    }

    public String getName() { return name; }
}
