package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Wording shared by new audit records and the presentation of older records. */
final class AuditMessages {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private AuditMessages() { }

    static String name(User user) {
        return user == null ? "la persona registrada" : user.getFirstName() + " " + user.getLastName();
    }

    static String date(LocalDate date) { return date.format(DATE); }

    static String attendance(String action, String trainee, LocalDate date,
            LocalDateTime checkIn, LocalDateTime checkOut, boolean sameDayAdjustment) {
        String day = "la jornada de " + trainee + " del " + date(date);
        return switch (action) {
            case "REGISTRAR_ENTRADA" -> "Se registró la entrada de " + trainee + " el " + date(date)
                    + " a las " + checkIn.format(TIME) + ".";
            case "REGISTRAR_SALIDA" -> "Se registró la salida de " + trainee + " el " + date(date)
                    + " a las " + checkOut.format(TIME) + ".";
            default -> "El Sistema cerró automáticamente " + day
                    + (sameDayAdjustment ? " para que termine dentro del mismo día."
                            : " al completar las 6 horas diarias.")
                    + " Entrada: " + checkIn.format(TIME) + "; salida: " + checkOut.format(TIME) + ".";
        };
    }

    static String export(String trainee, LocalDate from, LocalDate to, String format) {
        return "Se exportó la bitácora de " + trainee + " en " + format.toUpperCase(Locale.ROOT)
                + ", correspondiente al período del " + date(from) + " al " + date(to) + ".";
    }

    static String assignment(String action, String trainee, String tutor, String laboratory) {
        String place = laboratory == null ? "" : " en el laboratorio «" + laboratory + "»";
        return switch (action) {
            case "DESVINCULAR_TUTOR" -> "Se desvinculó al practicante " + trainee
                    + " del tutor " + tutor + place + ".";
            case "REASIGNAR_TUTOR" -> "Se reasignó al practicante " + trainee
                    + " al tutor " + tutor + place + ".";
            default -> "Se asignó al tutor " + tutor + " al practicante " + trainee + place + ".";
        };
    }

    static String holiday(String action, LocalDate date, String description) {
        String verb = switch (action) {
            case "ACTIVAR_FERIADO" -> "activó";
            case "DESACTIVAR_FERIADO" -> "desactivó";
            case "ELIMINAR_FERIADO" -> "eliminó";
            default -> "registró";
        };
        return "Se " + verb + " el feriado"
                + (description == null || description.isBlank() ? "" : " «" + description + "»")
                + " del " + date(date) + ".";
    }

    static String holidayEdit(LocalDate previousDate, String previousDescription, LocalDate date, String description) {
        return "Se actualizó el feriado «" + previousDescription + "» del " + date(previousDate)
                + ". Ahora corresponde a «" + description + "» del " + date(date) + ".";
    }

    static String laboratory(String action, String name, String location) {
        String verb = "ELIMINAR_LABORATORIO".equals(action) ? "eliminó" : "registró";
        return "Se " + verb + " el laboratorio «" + name + "»"
                + (location == null || location.isBlank() ? "." : ", ubicado en «" + location + "».");
    }

    static String laboratoryEdit(String previousName, String previousLocation, String name, String location) {
        return "Se actualizó el laboratorio «" + previousName + "». Nombre actual: «" + name
                + "». Ubicación anterior: " + location(previousLocation)
                + ". Ubicación actual: " + location(location) + ".";
    }

    private static String location(String value) {
        return value == null || value.isBlank() ? "sin especificar" : "«" + value + "»";
    }
}
