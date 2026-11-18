package ec.edu.espe.practicas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "TCWROLES")
@SequenceGenerator(name = "role_seq", sequenceName = "SEQ_CWROLES", allocationSize = 1)
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "role_seq")
    @Column(name = "TCWROLES_ID", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "TCWROLES_NOMBRE", nullable = false, length = 30, unique = true)
    private RoleName name;

    protected Role() { }

    public Long getId() { return id; }
    public RoleName getName() { return name; }
    public void setName(RoleName name) { this.name = name; }
}
