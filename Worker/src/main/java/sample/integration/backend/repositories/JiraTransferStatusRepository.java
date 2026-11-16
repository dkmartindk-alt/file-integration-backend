package sample.integration.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.integration.backend.models.Case;
import sample.integration.backend.models.JiraTransferStatus;

public interface JiraTransferStatusRepository extends JpaRepository<JiraTransferStatus, String> {
    JiraTransferStatus findByCaseRecord(Case caseRecord);

}
