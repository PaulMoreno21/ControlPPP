package ec.edu.espe.practicas.repository;

import static org.junit.jupiter.api.Assertions.*;

import ec.edu.espe.practicas.model.RoleName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in: only SELECTs against the configured schema; never edits users or DDL. */
@EnabledIfEnvironmentVariable(named = "RUN_ORACLE_READONLY_TESTS", matches = "true")
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(readOnly = true)
class GeneralAdministratorOracleReadOnlyTest {
    @Autowired UserRepository users;
    @Autowired UserRoleRepository roles;
    @Autowired JdbcTemplate jdbc;

    @Test void globalListingAndCountAgreeWithOracleWithoutLaboratoryScope() {
        var page = users.findGlobalAdministrators(RoleName.ADMIN_GENERAL, null,
                PageRequest.of(0, 15, Sort.by("lastName", "firstName", "id")));
        Long expected = jdbc.queryForObject("select count(*) from TCWUSUARIOS u where exists "
                + "(select 1 from TCWUSUARIOSROLES ur join TCWROLES r on r.TCWROLES_ID=ur.TCWROLES_ID "
                + "where ur.TCWUSUARIOS_ID=u.TCWUSUARIOS_ID and r.TCWROLES_NOMBRE='ADMIN_GENERAL' "
                + "and ur.TCWLABORATORIOS_ID is null)", Long.class);
        assertEquals(expected.longValue(), page.getTotalElements());
        assertEquals(page.getNumberOfElements(), page.getContent().stream().map(user -> user.getId()).distinct().count());
        var ids = page.getContent().stream().map(user -> user.getId()).toList();
        if (!ids.isEmpty()) {
            var assignments = roles.findByUserIdIn(ids);
            for (var user : page) assertTrue(assignments.stream().anyMatch(role ->
                    role.getUser().getId().equals(user.getId()) && role.getRole().getName() == RoleName.ADMIN_GENERAL
                            && role.getLaboratory() == null));
            var first = page.getContent().getFirst();
            var filtered = users.findGlobalAdministrators(RoleName.ADMIN_GENERAL, "%" + first.getNationalId() + "%",
                    PageRequest.of(0, 15, Sort.by("lastName", "firstName", "id")));
            assertTrue(filtered.stream().anyMatch(user -> user.getId().equals(first.getId())));
        }
        Long active = jdbc.queryForObject("select count(*) from TCWUSUARIOS u where u.TCWUSUARIOS_ACTIVO=1 "
                + "and exists (select 1 from TCWUSUARIOSROLES ur join TCWROLES r on r.TCWROLES_ID=ur.TCWROLES_ID "
                + "where ur.TCWUSUARIOS_ID=u.TCWUSUARIOS_ID and r.TCWROLES_NOMBRE='ADMIN_GENERAL' "
                + "and ur.TCWLABORATORIOS_ID is null)", Long.class);
        assertEquals(active.longValue(), roles.countOtherActiveGlobalAdministrators(RoleName.ADMIN_GENERAL, -1L));
    }
}
