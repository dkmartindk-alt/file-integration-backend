package sample.integration.backend.models;

import jakarta.persistence.*;

@Entity
@Table(name = "case_priority")
public class CasePriority {
    @Id
    @Column(name = "case_id")
    private String caseId;

    @OneToOne
    @MapsId // Automatically copies the primary key from the 'case' relationship
    @JoinColumn(name = "case_id")
    private Case caseRecord;

    private CasePriorityStatus priorityStatus;

    public CasePriority(){}

    public CasePriority(Case caseRecord, CasePriorityStatus priority) {
        setCaseRecord(caseRecord);
        setPriorityStatus(priority);
    }

    public Case getCaseRecord() {
        return caseRecord;
    }

    public void setCaseRecord(Case caseRecord) {
        this.caseRecord = caseRecord;
    }

    public CasePriorityStatus getPriorityStatus() {
        return priorityStatus;
    }

    public void setPriorityStatus(CasePriorityStatus priorityStatus) {
        this.priorityStatus = priorityStatus;
    }
}
