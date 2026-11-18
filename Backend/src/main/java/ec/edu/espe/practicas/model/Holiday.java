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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TCWFERIADOS", uniqueConstraints =
        @UniqueConstraint(name = "UK_TCWFERIADOS_FECHA", columnNames = "TCWFERIADOS_FECHA"))
@SequenceGenerator(name = "holiday_seq", sequenceName = "SEQ_CWFERIADOS", allocationSize = 1)
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "holiday_seq")
    @Column(name = "TCWFERIADOS_ID", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "TCWFERIADOS_FECHA", nullable = false)
    private LocalDate date;

    @NotBlank @Size(max = 150)
    @Column(name = "TCWFERIADOS_DESCRIPCION", nullable = false, length = 150)
    private String description;

    @NotNull
    @Column(name = "TCWFERIADOS_ACTIVO", nullable = false)
    private Integer active = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_ADMIN", nullable = false)
    private User registeredBy;

    @NotNull
    @Column(name = "TCWFERIADOS_FECHA_REGISTRO", nullable = false)
    private LocalDateTime registeredAt;

    protected Holiday() { }

    public static Holiday create(
            LocalDate date, String description, User registeredBy, LocalDateTime registeredAt) {
        Holiday holiday = new Holiday();
        holiday.date = date;
        holiday.description = description;
        holiday.active = 1;
        holiday.registeredBy = registeredBy;
        holiday.registeredAt = registeredAt;
        return holiday;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getActive() { return active; }
    public void setActive(Integer active) { this.active = active; }
    public User getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(User registeredBy) { this.registeredBy = registeredBy; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}
