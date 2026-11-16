package sample.integration.backend.models;

import jakarta.persistence.*;

@Entity
@Table(name = "case_metadata")
public class CaseMetaData {
    @Id
    @Column(name = "id", unique = true, nullable = false)
    private int id;

    @OneToOne
    @JoinColumn(name = "case_id", unique = true, nullable = false)
    private Case caseRecord;

    //Other data could be here..


    public CaseMetaData(){

    }

    public CaseMetaData(Case caseRecord, int id){
        this.setCaseRecord(caseRecord);
        this.setId(id);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Case getCaseRecord() {
        return caseRecord;
    }

    public void setCaseRecord(Case caseRecord) {
        this.caseRecord = caseRecord;
    }
}
