package ec.edu.espe.practicas.service.holidays;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.controller.ApiExceptionHandler;
import ec.edu.espe.practicas.controller.HolidaySynchronizationController;
import ec.edu.espe.practicas.dto.HolidaySynchronizationStatus;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.service.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HolidaySynchronizationControllerTest {
    @Test void globalAdministratorSeesStatusWithoutKeysOrAdministratorIds() throws Exception {
        var users = mock(AuthenticatedUserService.class);
        var sync = mock(HolidaySynchronizationService.class);
        when(sync.status()).thenReturn(new HolidaySynchronizationStatus("NOT_CONFIGURED", null, null, 0,
                "La actualización automática de feriados todavía no está configurada."));
        var mvc = MockMvcBuilders.standaloneSetup(new HolidaySynchronizationController(users, sync)).build();
        mvc.perform(get("/api/v1/admin/holidays/synchronization"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.apiKey").doesNotExist()).andExpect(jsonPath("$.administratorId").doesNotExist());
        verify(users).requireGlobalRole(any(), eq(RoleName.ADMIN_GENERAL));
    }

    @Test void roleDenialHappensBeforeReadingStatus() throws Exception {
        var users = mock(AuthenticatedUserService.class);
        var sync = mock(HolidaySynchronizationService.class);
        when(users.requireGlobalRole(any(), eq(RoleName.ADMIN_GENERAL))).thenThrow(new AccessDeniedException("Sin permiso"));
        var mvc = MockMvcBuilders.standaloneSetup(new HolidaySynchronizationController(users, sync))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/admin/holidays/synchronization")).andExpect(status().isForbidden());
        verifyNoInteractions(sync);
    }
}
