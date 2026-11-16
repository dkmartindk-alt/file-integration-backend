package sample.integration.backend.services;

import sample.integration.backend.models.Case;

public interface CaseDataFetcher {
    /**
     * Retrieves data based on a caseId.
     * The implementation should ideally be idempotent to handle repeated calls gracefully.
     *
     * @param caseRecord caseRecord of the specific case
     * @return true on success, false on failure.
     */
    boolean syncCaseData(Case caseRecord);
}
