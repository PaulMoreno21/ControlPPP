package ec.edu.espe.practicas.dto;

import java.util.List;

public record ReportScopeResponse(
        String role,
        List<LaboratorySummaryResponse> laboratories,
        List<ReportTraineeOption> trainees,
        boolean canChooseLaboratory,
        boolean canChooseTrainee) { }
