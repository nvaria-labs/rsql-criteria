package visitors;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(name = "release_year")
    private int year;
    private String genre;
    private String actor;
    private LocalDate releaseDate;
    private boolean featured;

    @ElementCollection
    private Set<String> tags = new HashSet<>();

    @ElementCollection
    private List<Location> locations = new ArrayList<>();

    @ManyToOne
    private Director director;

    public Movie() {}

    public Movie(String name, int year, String genre, String actor, Director director) {
        this.name = name;
        this.year = year;
        this.genre = genre;
        this.actor = actor;
        this.director = director;
    }

    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public void setTags(String... tags) { this.tags = new HashSet<>(Set.of(tags)); }
    public void setLocations(Location... locations) { this.locations = new ArrayList<>(List.of(locations)); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getYear() { return year; }
    public String getGenre() { return genre; }
    public String getActor() { return actor; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public Director getDirector() { return director; }
}
