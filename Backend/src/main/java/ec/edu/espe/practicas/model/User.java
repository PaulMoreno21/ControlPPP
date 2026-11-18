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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "TCWUSUARIOS", uniqueConstraints = {
        @UniqueConstraint(name = "UK_TCWUSUARIOS_CEDULA", columnNames = "TCWUSUARIOS_CEDULA"),
        @UniqueConstraint(name = "UK_TCWUSUARIOS_CORREO", columnNames = "TCWUSUARIOS_CORREO")
})
@SequenceGenerator(name = "user_seq", sequenceName = "SEQ_CWUSUARIOS", allocationSize = 1)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @Column(name = "TCWUSUARIOS_ID", nullable = false)
    private Long id;

    @NotBlank @Size(max = 10)
    @Column(name = "TCWUSUARIOS_CEDULA", nullable = false, length = 10)
    private String nationalId;

    @NotBlank @Size(max = 100)
    @Column(name = "TCWUSUARIOS_NOMBRE", nullable = false, length = 100)
    private String firstName;

    @NotBlank @Size(max = 100)
    @Column(name = "TCWUSUARIOS_APELLIDO", nullable = false, length = 100)
    private String lastName;

    @NotBlank @Email @Size(max = 150)
    @Column(name = "TCWUSUARIOS_CORREO", nullable = false, length = 150)
    private String email;

    @NotBlank @Size(max = 255)
    @Column(name = "TCWUSUARIOS_CLAVE_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "TCWUSUARIOS_ACTIVO", nullable = false)
    private Integer active = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "TCWUSUARIOS_TIPO_CUENTA", nullable = false, length = 15)
    private AccountType accountType = AccountType.LOCAL;

    @Column(name = "TCWUSUARIOS_CAMBIAR_CLAVE", nullable = false)
    private Integer passwordChangeRequired = 0;

    protected User() { }

    public static User create(
            String nationalId, String firstName, String lastName, String email,
            String passwordHash, AccountType accountType) {
        User user = new User();
        user.nationalId = nationalId;
        user.firstName = firstName;
        user.lastName = lastName;
        user.email = email;
        user.passwordHash = passwordHash;
        user.accountType = accountType;
        user.active = 1;
        return user;
    }

    public Long getId() { return id; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Integer getActive() { return active; }
    public void setActive(Integer active) { this.active = active; }
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    public Integer getPasswordChangeRequired() { return passwordChangeRequired; }
    public boolean isPasswordChangeRequired() { return Integer.valueOf(1).equals(passwordChangeRequired); }
    public void setPasswordChangeRequired(boolean required) { this.passwordChangeRequired = required ? 1 : 0; }
}
