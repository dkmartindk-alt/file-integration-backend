package sample.integration.backend.models;

import jakarta.persistence.*;

@Entity
@Table(name = "case_privilege")
public class CasePrivilege {
    @Id
    @Column(name = "case_id")
    private String caseId;

    @OneToOne
    @MapsId // Automatically copies the primary key from the 'case' relationship
    @JoinColumn(name = "case_id")
    private Case caseRecord;

    private int privilegeId;

    public CasePrivilege(){}

    public CasePrivilege(Case caseRecord, int privilegeId){
        setCaseRecord(caseRecord);
        setPrivilegeId(privilegeId);
    }

    //Other data could be here..


    public Case getCaseRecord() {
        return caseRecord;
    }

    public void setCaseRecord(Case caseRecord) {
        this.caseRecord = caseRecord;
    }

    public int getPrivilegeId() {
        return privilegeId;
    }

    public void setPrivilegeId(int privilegeId) {
        this.privilegeId = privilegeId;
    }
}
