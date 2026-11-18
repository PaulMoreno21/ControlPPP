package ec.edu.espe.practicas.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ActivityDraftRequest(
        @NotBlank @Size(max = 1000) String description,
        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("6.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal durationHours) {
}
