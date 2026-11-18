package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.User;

public record ReportTraineeOption(Long id, String name, String email, Long laboratoryId, String laboratoryName) {
    public static ReportTraineeOption of(User user, Long laboratoryId, String laboratoryName) {
        return new ReportTraineeOption(
                user.getId(), user.getFirstName() + " " + user.getLastName(), user.getEmail(),
                laboratoryId, laboratoryName);
    }
}
