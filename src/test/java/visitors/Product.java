package visitors;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long movieId;

    private String attributes;

    public Product() {}

    public Product(String attributes) {
        this.attributes = attributes;
    }

    public Product(Long movieId, String attributes) {
        this.movieId = movieId;
        this.attributes = attributes;
    }

    public Long getId() { return id; }
    public Long getMovieId() { return movieId; }
    public String getAttributes() { return attributes; }
}
