package sample.integration.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sample.integration.backend.models.Case;
import sample.integration.backend.models.CasePriority;
import sample.integration.backend.models.CasePriorityStatus;
import sample.integration.backend.repositories.CasePriorityRepository;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class CasePriorityDataFetcher implements CaseDataFetcher {
    private static final Logger log = LoggerFactory.getLogger(CasePriorityDataFetcher.class);

    @Value("${worker.case.service.errorchance:5}")
    private int errorChance;

    private final CasePriorityRepository casePriorityRepository;

    public CasePriorityDataFetcher(CasePriorityRepository casePriorityRepository){
        this.casePriorityRepository = casePriorityRepository;
    }

    @Override
    public boolean syncCaseData(Case caseRecord) {
        CasePriority casePriority = this.casePriorityRepository.findByCaseRecord(caseRecord);
        if(casePriority != null){
            return true;
        }
        if(ThreadLocalRandom.current().nextInt(100) < errorChance){
            //An error occured, we failed to cache data.
            log.error("Failed to get priority for case id {}", caseRecord.getCaseId());
            return false;
        }
        //We generate some bogus data here
        casePriority = new CasePriority(caseRecord, ThreadLocalRandom.current().nextInt(2) == 0 ? CasePriorityStatus.LOW : CasePriorityStatus.HIGH );
        casePriorityRepository.saveAndFlush(casePriority);
        return true;
    }
}
