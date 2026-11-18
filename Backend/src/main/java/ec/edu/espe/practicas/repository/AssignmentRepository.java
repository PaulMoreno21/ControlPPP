package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.Assignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findByTraineeIdAndCurrent(Long traineeId, Integer current);
    @EntityGraph(attributePaths = {"trainee", "laboratory"})
    List<Assignment> findByTutorIdAndCurrent(Long tutorId, Integer current);
    @EntityGraph(attributePaths = {"trainee", "tutor", "assignedBy", "laboratory"})
    List<Assignment> findByLaboratoryIdOrderByAssignedAtDesc(Long laboratoryId);
    @EntityGraph(attributePaths = {"trainee", "tutor", "assignedBy", "laboratory"})
    Page<Assignment> findByLaboratoryId(Long laboratoryId, Pageable pageable);
    @EntityGraph(attributePaths = {"trainee", "tutor", "assignedBy", "laboratory"})
    @Query(value = "select a from Assignment a where a.laboratory.id = :laboratoryId "
            + "and a.current = 1 and (:search is null "
            + "or lower(a.tutor.firstName) like lower(:search) "
            + "or lower(concat(concat(a.tutor.firstName, ' '), a.tutor.lastName)) like lower(:search) "
            + "or lower(a.tutor.lastName) like lower(:search) "
            + "or lower(a.tutor.email) like lower(:search))",
            countQuery = "select count(a.id) from Assignment a where a.laboratory.id = :laboratoryId "
                    + "and a.current = 1 and (:search is null "
                    + "or lower(a.tutor.firstName) like lower(:search) "
                    + "or lower(concat(concat(a.tutor.firstName, ' '), a.tutor.lastName)) like lower(:search) "
                    + "or lower(a.tutor.lastName) like lower(:search) "
                    + "or lower(a.tutor.email) like lower(:search))")
    Page<Assignment> findActiveByLaboratoryIdAndTutorSearch(
            @Param("laboratoryId") Long laboratoryId, @Param("search") String search, Pageable pageable);
    List<Assignment> findByLaboratoryIdAndCurrent(Long laboratoryId, Integer current);
    boolean existsByLaboratoryId(Long laboratoryId);
    @EntityGraph(attributePaths = {"trainee", "laboratory"})
    Page<Assignment> findByTutorIdAndCurrent(Long tutorId, Integer current, Pageable pageable);
    @EntityGraph(attributePaths = {"trainee", "laboratory"})
    @Query(value = "select a from Assignment a where a.tutor.id = :tutorId and a.current = 1 "
            + "and (:search is null or lower(a.trainee.firstName) like lower(:search) "
            + "or lower(concat(concat(a.trainee.firstName, ' '), a.trainee.lastName)) like lower(:search) "
            + "or lower(a.trainee.lastName) like lower(:search) "
            + "or lower(a.trainee.email) like lower(:search) "
            + "or lower(a.laboratory.name) like lower(:search)) order by a.trainee.lastName, a.trainee.firstName, a.trainee.id",
            countQuery = "select count(a.id) from Assignment a where a.tutor.id = :tutorId and a.current = 1 "
                    + "and (:search is null or lower(a.trainee.firstName) like lower(:search) "
                    + "or lower(concat(concat(a.trainee.firstName, ' '), a.trainee.lastName)) like lower(:search) "
                    + "or lower(a.trainee.lastName) like lower(:search) "
                    + "or lower(a.trainee.email) like lower(:search) "
                    + "or lower(a.laboratory.name) like lower(:search))")
    Page<Assignment> findActiveByTutorIdAndTraineeSearch(
            @Param("tutorId") Long tutorId, @Param("search") String search, Pageable pageable);
}
