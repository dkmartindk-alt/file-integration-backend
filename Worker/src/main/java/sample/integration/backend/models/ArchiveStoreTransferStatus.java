package sample.integration.backend.models;

import jakarta.persistence.*;

@Entity
@Table(name = "archivestore_transfer_status")
public class ArchiveStoreTransferStatus {
    @Id
    @Column(name = "case_id")
    private String caseId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "case_id", unique = true, nullable = false)
    private Case caseRecord;

    //Other data could be here..
    public ArchiveStoreTransferStatus(){

    }
    public ArchiveStoreTransferStatus(Case caseRecord) {
        this.caseRecord = caseRecord;
    }
}
