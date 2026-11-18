package ec.edu.espe.practicas.repository;

import ec.edu.espe.practicas.model.Laboratory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {
    Optional<Laboratory> findByName(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    Page<Laboratory> findAll(Pageable pageable);
}
