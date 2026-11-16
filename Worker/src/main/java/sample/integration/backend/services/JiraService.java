package sample.integration.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sample.integration.backend.models.*;
import sample.integration.backend.repositories.JiraTransferStatusRepository;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class JiraService implements CasePublisher{
    private static final Logger log = LoggerFactory.getLogger(JiraService.class);

    private final JiraTransferStatusRepository jiraTransferStatusRepository;

    @Value("${worker.case.service.errorchance:5}")
    private int errorChance;

    public JiraService(JiraTransferStatusRepository jiraTransferStatusRepository){
        this.jiraTransferStatusRepository = jiraTransferStatusRepository;
    }

    @Override
    public boolean publish(Case caseRecord) {
        JiraTransferStatus jiraTransferStatus = this.jiraTransferStatusRepository.findByCaseRecord(caseRecord);
        if(jiraTransferStatus != null){
            return true;
        }
        if(ThreadLocalRandom.current().nextInt(100) < errorChance){
            //An error occured, we failed to cache data.
            log.error("Failed to transfer {} to Jira", caseRecord.getCaseId());
            return false;
        }
        //We generate some bogus data here
        jiraTransferStatus = new JiraTransferStatus(caseRecord);
        jiraTransferStatusRepository.saveAndFlush(jiraTransferStatus);
        log.info("Data for case {} transferred to Jira", caseRecord.getCaseId());

        return true;
    }
}