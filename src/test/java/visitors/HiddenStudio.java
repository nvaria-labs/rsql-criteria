package visitors;

import ch.nvaria.rsql.RsqlHidden;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hidden_studio")
public class HiddenStudio extends HiddenAudited {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @RsqlHidden
    private long revenue;

    @RsqlHidden
    @OneToMany(mappedBy = "studio")
    private List<HiddenFilm> films = new ArrayList<>();

    @OneToMany(mappedBy = "distributor")
    private List<HiddenFilm> distributed = new ArrayList<>();

    public HiddenStudio() {}

    public HiddenStudio(String name, long revenue, String createdBy, String source) {
        this.name = name;
        this.revenue = revenue;
        this.createdBy = createdBy;
        this.source = source;
    }
}
