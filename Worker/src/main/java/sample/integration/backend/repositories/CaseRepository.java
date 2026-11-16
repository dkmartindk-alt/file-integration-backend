package sample.integration.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.integration.backend.models.Case;

public interface CaseRepository extends JpaRepository<Case, String> {
}


