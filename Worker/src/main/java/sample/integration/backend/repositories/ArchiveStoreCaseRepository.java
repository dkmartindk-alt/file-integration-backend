package sample.integration.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.integration.backend.models.ArchiveStoreTransferStatus;
import sample.integration.backend.models.Case;

public interface ArchiveStoreCaseRepository extends JpaRepository<ArchiveStoreTransferStatus, String>{
    ArchiveStoreTransferStatus findByCaseRecord(Case caseRecord);
}

