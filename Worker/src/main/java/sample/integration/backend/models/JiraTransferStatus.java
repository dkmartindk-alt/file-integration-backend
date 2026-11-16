package sample.integration.backend.models;

import jakarta.persistence.*;

@Entity
@Table(name = "jira_transfer_status")
public class JiraTransferStatus {
    @Id
    @Column(name = "case_id")
    private String caseId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "case_id", unique = true, nullable = false)
    private Case caseRecord;

    //Other data could be here..

    public JiraTransferStatus(){

    }

    public JiraTransferStatus(Case caseRecord) {
        this.caseRecord = caseRecord;
    }
}
