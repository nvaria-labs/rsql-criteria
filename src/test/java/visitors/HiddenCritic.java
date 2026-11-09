package visitors;

import ch.nvaria.rsql.RsqlHidden;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Property access: one attribute hidden on its getter, one on its backing field. */
@Entity
@Table(name = "hidden_critic")
@Access(AccessType.PROPERTY)
public class HiddenCritic {

    private Long id;
    private String name;
    private String email;
    @RsqlHidden
    private String phone;

    public HiddenCritic() {}

    public HiddenCritic(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @RsqlHidden
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
