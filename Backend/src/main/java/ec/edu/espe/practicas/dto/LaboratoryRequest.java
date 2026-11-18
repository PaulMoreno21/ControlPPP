package ec.edu.espe.practicas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LaboratoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 150) String location) {
}
