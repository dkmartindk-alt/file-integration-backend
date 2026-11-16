package sample.integration.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sample.integration.backend.models.Case;
import sample.integration.backend.models.CaseMetaData;
import sample.integration.backend.models.CasePrivilege;
import sample.integration.backend.repositories.CaseMetaDataRepository;
import sample.integration.backend.repositories.CasePrivilegeRepository;
import sample.integration.backend.repositories.CaseRepository;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retrives data from the Ret system and saves the data in relation to the file id.
 * */

@Service
public class CasePrivilegeDataFetcher implements CaseDataFetcher {
    private static final Logger log = LoggerFactory.getLogger(CasePrivilegeDataFetcher.class);

    @Value("${worker.case.service.errorchance:5}")
    private int errorChance;

    private final CasePrivilegeRepository casePrivilegeRepository;
    private final CaseMetaDataRepository caseMetaDataRepository;
    public CasePrivilegeDataFetcher(CasePrivilegeRepository casePrivilegeRepository, CaseMetaDataRepository caseMetaDataRepository){
        this.casePrivilegeRepository = casePrivilegeRepository;
        this.caseMetaDataRepository = caseMetaDataRepository;
    }

    @Override
    public boolean syncCaseData(Case caseRecord) {
        if(caseRecord == null){
            log.error("Case should always exist, before any child element is created.");
            return false;
        }

        Optional<CasePrivilege> casePrivilegeOptional = casePrivilegeRepository.findById(caseRecord.getCaseId());

        if(casePrivilegeOptional.isPresent()){
            return true;
        }
        else{
            CaseMetaData caseMetaDataOptional = caseMetaDataRepository.findByCaseRecord(caseRecord);
            if(caseMetaDataOptional == null){
                log.error("Case metadata could not be found.");
                return false;
            }
            //We load the meta data for the case using caseMetaDataOptional (in a real scenario)
            if(ThreadLocalRandom.current().nextInt(100) < errorChance){
                log.error("Failed to get privileges for case id {}", caseRecord.getCaseId());
                return false;
            }
        }
        //We generate some bogus data here
        CasePrivilege casePrivilege = new CasePrivilege(caseRecord, ThreadLocalRandom.current().nextInt(100));
        casePrivilegeRepository.saveAndFlush(casePrivilege);
        return true;
    }
}
