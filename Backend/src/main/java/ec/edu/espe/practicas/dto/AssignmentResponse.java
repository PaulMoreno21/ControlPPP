package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Assignment;
import java.time.LocalDateTime;

public record AssignmentResponse(
        Long id,
        Long traineeId,
        String traineeName,
        Long tutorId,
        String tutorName,
        Long laboratoryId,
        String laboratoryName,
        Long assignedById,
        LocalDateTime assignedAt,
        boolean current) {

    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getTrainee().getId(),
                fullName(assignment.getTrainee()),
                assignment.getTutor().getId(),
                fullName(assignment.getTutor()),
                assignment.getLaboratory().getId(),
                assignment.getLaboratory().getName(),
                assignment.getAssignedBy().getId(),
                assignment.getAssignedAt(),
                Integer.valueOf(1).equals(assignment.getCurrent()));
    }

    private static String fullName(ec.edu.espe.practicas.model.User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
