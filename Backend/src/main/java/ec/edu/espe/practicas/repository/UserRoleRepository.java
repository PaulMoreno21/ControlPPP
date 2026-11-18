package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.UserRole;
import ec.edu.espe.practicas.model.RoleName;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    @EntityGraph(attributePaths = {"user", "role", "laboratory"})
    List<UserRole> findByUserId(Long userId);
    @EntityGraph(attributePaths = {"user", "role", "laboratory"})
    List<UserRole> findByUserIdIn(List<Long> userIds);

    @Query("select count(distinct ur.user.id) from UserRole ur where ur.role.name = :role "
            + "and ur.laboratory is null and ur.user.active = 1 and ur.user.id <> :excludedId")
    long countOtherActiveGlobalAdministrators(@Param("role") RoleName role, @Param("excludedId") Long excludedId);
    List<UserRole> findByUserIdAndLaboratoryId(Long userId, Long laboratoryId);
    boolean existsByLaboratoryId(Long laboratoryId);
    @EntityGraph(attributePaths = {"user", "role", "laboratory"})
    List<UserRole> findByLaboratoryId(Long laboratoryId);
    @EntityGraph(attributePaths = {"user", "role", "laboratory"})
    List<UserRole> findByLaboratoryIdAndUserIdIn(Long laboratoryId, List<Long> userIds);
    @Query(value = "select distinct ur.user from UserRole ur where ur.laboratory.id = :laboratoryId "
            + "and (:search is null or lower(ur.user.nationalId) like lower(:search))",
            countQuery = "select count(distinct ur.user.id) from UserRole ur where ur.laboratory.id = :laboratoryId "
                    + "and (:search is null or lower(ur.user.nationalId) like lower(:search))")
    Page<ec.edu.espe.practicas.model.User> findUsersByLaboratoryId(
            @Param("laboratoryId") Long laboratoryId, @Param("search") String search, Pageable pageable);
    @EntityGraph(attributePaths = {"user", "role", "laboratory"})
    List<UserRole> findByRoleName(RoleName roleName);
    @EntityGraph(attributePaths = {"user", "role", "laboratory"})
    List<UserRole> findByRoleNameAndLaboratoryId(RoleName roleName, Long laboratoryId);
}
