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
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TCWASISTENCIAS", uniqueConstraints = @UniqueConstraint(
        name = "UK_TCWASISTENCIAS_PRACT_FECHA",
        columnNames = {"TCWUSUARIOS_ID_PRACTICANTE", "TCWASISTENCIAS_FECHA"}))
@SequenceGenerator(name = "attendance_seq", sequenceName = "SEQ_CWASISTENCIAS", allocationSize = 1)
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "attendance_seq")
    @Column(name = "TCWASISTENCIAS_ID", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_PRACTICANTE", nullable = false)
    private User trainee;

    @NotNull
    @Column(name = "TCWASISTENCIAS_FECHA", nullable = false)
    private LocalDate date;

    @NotNull
    @Column(name = "TCWASISTENCIAS_HORA_ENTRADA", nullable = false)
    private LocalDateTime checkIn;

    @Column(name = "TCWASISTENCIAS_HORA_SALIDA")
    private LocalDateTime checkOut;

    protected Attendance() { }

    public static Attendance start(User trainee, LocalDate date, LocalDateTime checkIn) {
        Attendance attendance = new Attendance();
        attendance.trainee = trainee;
        attendance.date = date;
        attendance.checkIn = checkIn;
        return attendance;
    }

    public Long getId() { return id; }
    public User getTrainee() { return trainee; }
    public void setTrainee(User trainee) { this.trainee = trainee; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalDateTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDateTime checkIn) { this.checkIn = checkIn; }
    public LocalDateTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDateTime checkOut) { this.checkOut = checkOut; }
}
