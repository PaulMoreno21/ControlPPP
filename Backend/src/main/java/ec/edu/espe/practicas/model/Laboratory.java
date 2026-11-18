package ec.edu.espe.practicas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "TCWLABORATORIOS")
@SequenceGenerator(name = "laboratory_seq", sequenceName = "SEQ_CWLABORATORIOS", allocationSize = 1)
public class Laboratory {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "laboratory_seq")
    @Column(name = "TCWLABORATORIOS_ID", nullable = false)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "TCWLABORATORIOS_NOMBRE", nullable = false, length = 100, unique = true)
    private String name;

    @Size(max = 150)
    @Column(name = "TCWLABORATORIOS_UBICACION", length = 150)
    private String location;

    protected Laboratory() { }

    public static Laboratory create(String name, String location) {
        Laboratory laboratory = new Laboratory();
        laboratory.name = name;
        laboratory.location = location;
        return laboratory;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
