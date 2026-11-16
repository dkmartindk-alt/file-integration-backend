package sample.integration.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.integration.backend.models.Case;
import sample.integration.backend.models.CasePriority;

public interface CasePriorityRepository extends JpaRepository<CasePriority, String> {
    CasePriority findByCaseRecord(Case caseRecord);
}
