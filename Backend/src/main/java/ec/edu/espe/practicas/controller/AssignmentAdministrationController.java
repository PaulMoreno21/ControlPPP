package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.AssignmentRequest;
import ec.edu.espe.practicas.dto.AssignmentResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.service.AssignmentAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/laboratories/{laboratoryId}/assignments")
public class AssignmentAdministrationController {
    private final AssignmentAdministrationService assignmentAdministrationService;

    public AssignmentAdministrationController(AssignmentAdministrationService assignmentAdministrationService) {
        this.assignmentAdministrationService = assignmentAdministrationService;
    }

    @GetMapping
    public List<AssignmentResponse> listAssignments(
            Authentication authentication, @PathVariable Long laboratoryId) {
        return assignmentAdministrationService.listAssignments(authentication, laboratoryId);
    }

    @GetMapping("/page")
    public PagedResponse<AssignmentResponse> listAssignmentsPage(
            Authentication authentication,
            @PathVariable Long laboratoryId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "15") int size) {
        return assignmentAdministrationService.listAssignmentsPage(authentication, laboratoryId, search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse assignTutor(
            Authentication authentication,
            @PathVariable Long laboratoryId,
            @Valid @RequestBody AssignmentRequest request) {
        return assignmentAdministrationService.assignTutor(authentication, laboratoryId, request);
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkTutor(
            Authentication authentication,
            @PathVariable Long laboratoryId,
            @PathVariable Long assignmentId) {
        assignmentAdministrationService.unlinkTutor(authentication, laboratoryId, assignmentId);
    }
}
