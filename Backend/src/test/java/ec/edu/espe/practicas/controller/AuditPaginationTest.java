package ec.edu.espe.practicas.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.service.AuditQueryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditPaginationTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "/page"})
    void bothRoutesUseBoundedDefaultPage(String suffix) throws Exception {
        var service = mock(AuditQueryService.class);
        when(service.listPage(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(List.of(), 0, 15, 0, 0, true, true));
        var mvc = MockMvcBuilders.standaloneSetup(new AuditController(service)).build();

        mvc.perform(get("/api/v1/admin/audit-logs" + suffix)
                        .param("from", "2026-10-01").param("to", "2026-10-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(15))
                .andExpect(jsonPath("$.content").isArray());
        verify(service).listPage(isNull(), eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 30)), eq(0), eq(15));
    }
}
