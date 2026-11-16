package sample.integration.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import sample.integration.backend.models.Case;
import sample.integration.backend.models.CaseMetaData;
import sample.integration.backend.repositories.CaseMetaDataRepository;

import java.util.concurrent.ThreadLocalRandom;

/**
 * CaseMetaDataService must run before CasePrivilegeService,
 * because it establishes the base record and PrivilegeId
 * required by CasePrivilegeService.
 */
@Service
@Order(1)
public class CaseMetaDataDataFetcher implements CaseDataFetcher {
    private static final Logger log = LoggerFactory.getLogger(CaseMetaDataDataFetcher.class);

    @Value("${worker.case.service.errorchance:5}")
    private int errorChance;

    private final CaseMetaDataRepository caseMetaDataRepository;

    public CaseMetaDataDataFetcher(CaseMetaDataRepository caseMetaDataRepository){
        this.caseMetaDataRepository = caseMetaDataRepository;
    }

    @Override
    public boolean syncCaseData(Case caseRecord) {
        CaseMetaData caseMetaData = caseMetaDataRepository.findByCaseRecord(caseRecord);
        if(caseMetaData != null){
            return true;
        }
        if(ThreadLocalRandom.current().nextInt(100) < errorChance){
            //An error occured, we failed to cache data.
            log.error("Failed to get meta data for case id {}", caseRecord.getCaseId());
            return false;
        }
        //We generate some bogus data here - Since the ID is formed from a low number, this can cause collitions.
        caseMetaData = new CaseMetaData(caseRecord, ThreadLocalRandom.current().nextInt(1000));
        caseMetaDataRepository.saveAndFlush(caseMetaData);

        return true;
    }
}
