package sample.integration.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sample.integration.backend.models.ArchiveStoreTransferStatus;
import sample.integration.backend.models.Case;
import sample.integration.backend.repositories.ArchiveStoreCaseRepository;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class ArchiveStoreCaseService implements CasePublisher{
    private static final Logger log = LoggerFactory.getLogger(ArchiveStoreCaseService.class);

    private final ArchiveStoreCaseRepository archiveStoreCaseRepository;

    @Value("${worker.case.service.errorchance:5}")
    private int errorChance;

    public ArchiveStoreCaseService(ArchiveStoreCaseRepository archiveStoreCaseRepository){
        this.archiveStoreCaseRepository = archiveStoreCaseRepository;
    }

    @Override
    public boolean publish(Case caseRecord) {
        ArchiveStoreTransferStatus archiveStoreTransferStatus = this.archiveStoreCaseRepository.findByCaseRecord(caseRecord);
        if(archiveStoreTransferStatus != null){
            return true;
        }
        if(ThreadLocalRandom.current().nextInt(100) < errorChance){
            //An error occured, we failed to cache data.
            log.error("Failed to transfer {} to Archive Store", caseRecord.getCaseId());
            return false;
        }
        //We generate some bogus data here
        archiveStoreTransferStatus = new ArchiveStoreTransferStatus(caseRecord);
        archiveStoreCaseRepository.saveAndFlush(archiveStoreTransferStatus);
        log.info("Data for case {} transferred to Archive Store", caseRecord.getCaseId());

        return true;
    }
}
