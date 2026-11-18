package ec.edu.espe.practicas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignmentRequest(
        @NotNull @Positive Long traineeId,
        @NotNull @Positive Long tutorId) {
}
