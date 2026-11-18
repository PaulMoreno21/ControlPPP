package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.ReviewDecision;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotNull ReviewDecision decision,
        String observation) {
}
