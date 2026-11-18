package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Assignment;

public record TutorTraineeResponse(
        Long id,
        String fullName,
        String email,
        Long laboratoryId,
        String laboratoryName) {

    public static TutorTraineeResponse from(Assignment assignment) {
        var trainee = assignment.getTrainee();
        var laboratory = assignment.getLaboratory();
        return new TutorTraineeResponse(
                trainee.getId(),
                trainee.getFirstName() + " " + trainee.getLastName(),
                trainee.getEmail(),
                laboratory.getId(),
                laboratory.getName());
    }
}
