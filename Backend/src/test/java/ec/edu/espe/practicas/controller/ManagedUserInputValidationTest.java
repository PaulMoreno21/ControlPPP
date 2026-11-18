package ec.edu.espe.practicas.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.dto.CreateManagedUserRequest;
import ec.edu.espe.practicas.dto.UpdateManagedUserRequest;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.service.PasswordManagementService;
import ec.edu.espe.practicas.service.UserAdministrationService;
import jakarta.validation.Validation;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ManagedUserInputValidationTest {
    @Test
    void acceptsTrimmedPassportAccentsAndCompoundNames() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new CreateManagedUserRequest(" AB1234567 ", " María José ", " D’Ávila-Pérez ",
                    " persona@example.com ", null, false, null, List.of(RoleName.TUTOR_ESPE));
            assertTrue(factory.getValidator().validate(request).isEmpty());
            assertEquals("AB1234567", request.nationalId());
            assertEquals("María José", request.firstName());
            assertEquals("persona@example.com", request.email());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "12345678901", "AB 123", "<script>", "123-45"})
    void bothCreateAndEditRejectInvalidIdentification(String identification) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new CreateManagedUserRequest(identification, "Ana", "Pérez",
                    "ana@example.com", null, false, null, List.of(RoleName.PRACTICANTE))).isEmpty());
            assertFalse(validator.validate(new UpdateManagedUserRequest(identification, "Ana", "Pérez",
                    "ana@example.com", List.of(RoleName.PRACTICANTE))).isEmpty());
        }
    }

    @Test
    void rejectsInvalidNamesEmailAccountTypeAndNullRoles() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var invalid = new CreateManagedUserRequest("1234567890", "123", "<b>Pérez</b>",
                    "ana@localhost", null, null, null, Arrays.asList(RoleName.PRACTICANTE, null));
            var fields = factory.getValidator().validate(invalid).stream()
                    .map(v -> v.getPropertyPath().toString()).toList();
            for (String field : List.of("firstName", "lastName", "email", "localAccount")) {
                assertTrue(fields.contains(field), field);
            }
            assertTrue(fields.stream().anyMatch(field -> field.startsWith("roles")));
        }
    }

    @Test
    void rejectsOversizedFieldsAndMissingEditRoles() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var invalid = new UpdateManagedUserRequest("1234567890", "A".repeat(101), "B".repeat(101),
                    "a".repeat(140) + "@example.com", List.of());
            var fields = factory.getValidator().validate(invalid).stream()
                    .map(v -> v.getPropertyPath().toString()).toList();
            assertTrue(fields.containsAll(List.of("firstName", "lastName", "email", "roles")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void invalidBodyNeverReachesUserService(String method) throws Exception {
        var users = mock(UserAdministrationService.class);
        var passwords = mock(PasswordManagementService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserAdministrationController(users, passwords))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        String base = "/api/v1/admin/laboratories/1/users";
        var request = method.equals("POST") ? post(base) : put(base + "/2");
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("""
                {"nationalId":"AB 123","firstName":"   ","lastName":"Pérez",
                 "email":"invalid","localAccount":false,"roles":["PRACTICANTE"]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("nationalId"))));
        verifyNoInteractions(users, passwords);
    }

    @Test
    void rejectsInvalidRoleWithFriendlyMessage() throws Exception {
        var users = mock(UserAdministrationService.class);
        var passwords = mock(PasswordManagementService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserAdministrationController(users, passwords))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/v1/admin/laboratories/1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nationalId":"1234567890","firstName":"Ana","lastName":"Pérez",
                                 "email":"ana@example.com","localAccount":false,"roles":["UNKNOWN"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Revisa los datos ingresados y selecciona las opciones disponibles."));
        verifyNoInteractions(users, passwords);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/admin/laboratories/0/users/page",
            "/api/v1/admin/laboratories/1/users/page?search=AB%20CD",
            "/api/v1/admin/laboratories/1/users/page?search=12345678901"})
    void rejectsInvalidLaboratoryAndSearchBeforeQuery(String uri) throws Exception {
        var users = mock(UserAdministrationService.class);
        var passwords = mock(PasswordManagementService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserAdministrationController(users, passwords))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get(uri)).andExpect(status().isBadRequest());
        verifyNoInteractions(users, passwords);
    }
}
