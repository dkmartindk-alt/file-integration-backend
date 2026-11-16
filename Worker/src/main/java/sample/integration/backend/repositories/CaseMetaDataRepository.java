package sample.integration.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.integration.backend.models.Case;
import sample.integration.backend.models.CaseMetaData;


public interface CaseMetaDataRepository extends JpaRepository<CaseMetaData, String> {
    CaseMetaData findByCaseRecord(Case caseRecord);
}
