package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ec.edu.espe.practicas.model.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByNationalId(String nationalId);
    Optional<User> findByEmailIgnoreCase(String email);

    @Query(value = "select u from User u where exists (select ur.id from UserRole ur "
            + "where ur.user = u and ur.role.name = :role and ur.laboratory is null) "
            + "and (:search is null or lower(u.nationalId) like lower(:search))",
            countQuery = "select count(u) from User u where exists (select ur.id from UserRole ur "
            + "where ur.user = u and ur.role.name = :role and ur.laboratory is null) "
            + "and (:search is null or lower(u.nationalId) like lower(:search))")
    Page<User> findGlobalAdministrators(@Param("role") RoleName role, @Param("search") String search, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
