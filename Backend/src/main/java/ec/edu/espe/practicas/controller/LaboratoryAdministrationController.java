package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.LaboratorySummaryResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.dto.LaboratoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import ec.edu.espe.practicas.service.LaboratoryAdministrationService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/laboratories")
public class LaboratoryAdministrationController {
    private final LaboratoryAdministrationService laboratoryAdministrationService;

    public LaboratoryAdministrationController(LaboratoryAdministrationService laboratoryAdministrationService) {
        this.laboratoryAdministrationService = laboratoryAdministrationService;
    }

    @GetMapping
    public List<LaboratorySummaryResponse> listLaboratories(Authentication authentication) {
        return laboratoryAdministrationService.listLaboratories(authentication);
    }

    @GetMapping("/page")
    public PagedResponse<LaboratorySummaryResponse> listLaboratoriesPage(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "15") int size) {
        return laboratoryAdministrationService.listLaboratoriesPage(authentication, page, size);
    }

    @GetMapping("/accessible")
    public List<LaboratorySummaryResponse> listAccessibleLaboratories(Authentication authentication) {
        return laboratoryAdministrationService.listAccessibleLaboratories(authentication);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LaboratorySummaryResponse createLaboratory(
            Authentication authentication, @Valid @RequestBody LaboratoryRequest request) {
        return laboratoryAdministrationService.createLaboratory(authentication, request);
    }

    @PutMapping("/{laboratoryId}")
    public LaboratorySummaryResponse updateLaboratory(
            Authentication authentication,
            @PathVariable Long laboratoryId,
            @Valid @RequestBody LaboratoryRequest request) {
        return laboratoryAdministrationService.updateLaboratory(authentication, laboratoryId, request);
    }

    @DeleteMapping("/{laboratoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLaboratory(Authentication authentication, @PathVariable Long laboratoryId) {
        laboratoryAdministrationService.deleteLaboratory(authentication, laboratoryId);
    }
}
