package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.repository.RoleRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;

@Service
public class GlobalAdministratorPolicy {
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;

    public GlobalAdministratorPolicy(RoleRepository roles, UserRoleRepository userRoles) {
        this.roles = roles;
        this.userRoles = userRoles;
    }

    // All mutations that remove a global administrator must take this same database lock.
    public void requireAnotherActiveAdministrator(Long excludedId) {
        roles.findByNameForUpdate(RoleName.ADMIN_GENERAL).orElseThrow(() ->
                new BusinessRuleViolationException("No se pudo comprobar el acceso de administración general."));
        if (userRoles.countOtherActiveGlobalAdministrators(RoleName.ADMIN_GENERAL, excludedId) < 1) {
            throw new BusinessRuleViolationException("Debe permanecer al menos un administrador general activo.");
        }
    }
}
