package ec.edu.espe.practicas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "TCWUSUARIOSROLES")
@SequenceGenerator(name = "user_role_seq", sequenceName = "SEQ_CWUSUARIOSROLES", allocationSize = 1)
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_role_seq")
    @Column(name = "TCWUSUARIOSROLES_ID", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWROLES_ID", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TCWLABORATORIOS_ID")
    private Laboratory laboratory;

    protected UserRole() { }

    public static UserRole assign(User user, Role role, Laboratory laboratory) {
        UserRole assignment = new UserRole();
        assignment.user = user;
        assignment.role = role;
        assignment.laboratory = laboratory;
        return assignment;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Laboratory getLaboratory() { return laboratory; }
    public void setLaboratory(Laboratory laboratory) { this.laboratory = laboratory; }
}
