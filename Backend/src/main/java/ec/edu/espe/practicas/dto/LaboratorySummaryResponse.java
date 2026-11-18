package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Laboratory;

public record LaboratorySummaryResponse(Long id, String name, String location) {
    public static LaboratorySummaryResponse from(Laboratory laboratory) {
        return new LaboratorySummaryResponse(
                laboratory.getId(), laboratory.getName(), laboratory.getLocation());
    }
}
