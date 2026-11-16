package sample.integration.backend.services;

import sample.integration.backend.models.Case;

public interface CasePublisher {
    boolean publish(Case caseRecord);
}
