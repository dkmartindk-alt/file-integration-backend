package sample.integration.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.integration.backend.models.Case;
import sample.integration.backend.models.CasePrivilege;

public interface CasePrivilegeRepository extends JpaRepository<CasePrivilege, String>{
    CasePrivilege findByCaseRecord(Case caseRecord);

}