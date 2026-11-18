package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.Laboratory;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.repository.LaboratoryRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Read-only presentation of legacy details; references are resolved once per page. */
@Component
public class AuditDetailsFormatter {
    private final UserRepository users;
    private final LaboratoryRepository laboratories;

    public AuditDetailsFormatter(UserRepository users, LaboratoryRepository laboratories) {
        this.users = users;
        this.laboratories = laboratories;
    }

    public Map<AuditLog, String> formatPage(List<AuditLog> logs) {
        Map<AuditLog, Map<String, String>> fields = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> laboratoryIds = new HashSet<>();
        for (AuditLog log : logs) {
            Map<String, String> values = fields(log.getDetails());
            if (values.isEmpty()) continue;
            fields.put(log, values);
            if (isAssignment(log.getAction())) {
                addId(userIds, values.get("practicante"));
                addId(userIds, values.get("tutor"));
            }
            if ("TCWUSUARIOS".equals(log.getEntityName()) && log.getEntityId() != null) {
                userIds.add(log.getEntityId());
            }
            addId(laboratoryIds, values.get("laboratorio"));
        }
        Map<Long, User> userNames = userIds.isEmpty() ? Map.of() : users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, Laboratory> laboratoryNames = laboratoryIds.isEmpty() ? Map.of()
                : laboratories.findAllById(laboratoryIds).stream()
                        .collect(Collectors.toMap(Laboratory::getId, laboratory -> laboratory));
        Map<AuditLog, String> result = new HashMap<>();
        for (AuditLog log : logs) {
            Map<String, String> values = fields.get(log);
            result.put(log, values == null ? log.getDetails() : format(log, values, userNames, laboratoryNames));
        }
        return result;
    }

    private String format(AuditLog log, Map<String, String> values,
            Map<Long, User> users, Map<Long, Laboratory> laboratories) {
        String action = log.getAction();
        if (isAssignment(action)) {
            User trainee = lookup(users, values.get("practicante"));
            User tutor = lookup(users, values.get("tutor"));
            Laboratory laboratory = lookup(laboratories, values.get("laboratorio"));
            return AuditMessages.assignment(action,
                    trainee == null ? "registrado" : AuditMessages.name(trainee),
                    tutor == null ? "registrado" : AuditMessages.name(tutor),
                    laboratory == null ? null : laboratory.getName());
        }
        try {
            if (action.startsWith("EXPORTAR_BITACORA_")) {
                return AuditMessages.export(AuditMessages.name(log.getUser()),
                        LocalDate.parse(values.get("desde")), LocalDate.parse(values.get("hasta")),
                        action.substring("EXPORTAR_BITACORA_".length()));
            }
            if (Set.of("REGISTRAR_ENTRADA", "REGISTRAR_SALIDA", "CIERRE_AUTOMATICO_ASISTENCIA").contains(action)) {
                LocalDate date = LocalDate.parse(values.get("fecha"));
                LocalDateTime checkIn = values.containsKey("entrada")
                        ? LocalDateTime.parse(values.get("entrada")) : log.getOccurredAt();
                LocalDateTime checkOut = values.containsKey("salida")
                        ? LocalDateTime.parse(values.get("salida")) : log.getOccurredAt();
                return AuditMessages.attendance(action, AuditMessages.name(log.getUser()), date,
                        checkIn, checkOut, values.containsKey("ajuste"));
            }
            if ("TCWFERIADOS".equals(log.getEntityName())) {
                return AuditMessages.holiday(action, LocalDate.parse(values.get("fecha")), values.get("descripcion"));
            }
            if ("TCWLABORATORIOS".equals(log.getEntityName())) {
                return "EDITAR_LABORATORIO".equals(action)
                        ? AuditMessages.laboratoryEdit(values.getOrDefault("nombre", "registrado"),
                                values.get("ubicacion"), values.getOrDefault("nuevo_nombre", "registrado"),
                                values.get("nueva_ubicacion"))
                        : AuditMessages.laboratory(action, values.getOrDefault("nombre", "registrado"),
                                values.get("ubicacion"));
            }
            if ("TCWUSUARIOS".equals(log.getEntityName())) {
                String name = values.getOrDefault("Usuario creado", log.getEntityId() != null && users.containsKey(log.getEntityId())
                        ? AuditMessages.name(users.get(log.getEntityId())) : "la persona registrada");
                return switch (action) {
                    case "CREAR_USUARIO" -> "Se registró al usuario " + name + roles(values.get("Roles"))
                            + laboratorySuffix(values, laboratories) + ".";
                    case "ACTIVAR_USUARIO" -> "Se activó la cuenta de " + name + ".";
                    case "DESACTIVAR_USUARIO" -> "Se desactivó la cuenta de " + name + ".";
                    default -> log.getDetails();
                };
            }
        } catch (IllegalArgumentException | DateTimeException | NullPointerException invalidLegacyDetails) {
            // Incomplete older records must not prevent the whole page from loading.
            return "Se registró este evento. El detalle anterior no contiene información suficiente para describirlo.";
        }
        return log.getDetails();
    }

    private String laboratorySuffix(Map<String, String> values, Map<Long, Laboratory> laboratories) {
        Laboratory laboratory = lookup(laboratories, values.get("laboratorio"));
        return laboratory == null ? "" : " en el laboratorio «" + laboratory.getName() + "»";
    }

    private static boolean isAssignment(String action) {
        return Set.of("ASIGNAR_TUTOR", "REASIGNAR_TUTOR", "DESVINCULAR_TUTOR").contains(action);
    }

    private static String roles(String roles) {
        if (roles == null || roles.isBlank()) return "";
        String labels = roles.replace("[", "").replace("]", "")
                .replace("ADMIN_GENERAL", "Administrador general")
                .replace("ADMIN_LABORATORIO", "Administrador de laboratorio")
                .replace("TUTOR_ESPE", "Tutor ESPE").replace("PRACTICANTE", "Practicante");
        return " con los roles: " + labels;
    }

    private static <T> T lookup(Map<Long, T> values, String key) {
        Long parsedId = id(key);
        return parsedId == null ? null : values.get(parsedId);
    }

    private static Map<String, String> fields(String details) {
        Map<String, String> values = new HashMap<>();
        if (details == null) return values;
        int end = fieldNameEnd(details, 0);
        if (end == 0 || end == details.length() || details.charAt(end) != '=') return values;

        String name = details.substring(0, end).trim();
        int valueStart = end + 1;
        int cursor = valueStart;
        while (cursor < details.length()) {
            if (details.charAt(cursor) != ';') {
                cursor++;
                continue;
            }
            int separator = cursor;
            int nameStart = separator + 1;
            while (nameStart < details.length() && isFieldWhitespace(details.charAt(nameStart))) nameStart++;
            end = fieldNameEnd(details, nameStart);
            // Legacy records also allowed names consisting only of ASCII spaces.
            boolean spaceOnlyName = end == nameStart && nameStart > separator + 1
                    && details.charAt(nameStart - 1) == ' ';
            if (end < details.length() && details.charAt(end) == '=' && (end > nameStart || spaceOnlyName)) {
                values.put(name, details.substring(valueStart, separator).trim());
                name = spaceOnlyName ? "" : details.substring(nameStart, end).trim();
                valueStart = end + 1;
                cursor = valueStart;
            } else {
                // The inspected prefix contains no semicolons: do not scan it again.
                cursor = Math.max(separator + 1, end);
            }
        }
        values.put(name, details.substring(valueStart).trim());
        return values;
    }

    private static int fieldNameEnd(String details, int start) {
        int end = start;
        while (end < details.length()) {
            int character = details.codePointAt(end);
            if (!Character.isLetter(character) && character != '_' && character != ' ') break;
            end += Character.charCount(character);
        }
        return end;
    }

    private static boolean isFieldWhitespace(char character) {
        return switch (character) {
            case ' ', '\t', '\n', '\u000B', '\f', '\r' -> true;
            default -> false;
        };
    }

    private static void addId(Set<Long> ids, String value) {
        Long id = id(value);
        if (id != null) ids.add(id);
    }

    private static Long id(String value) {
        try { return value == null ? null : Long.valueOf(value); }
        catch (NumberFormatException invalidId) { return null; }
    }
}
