package sample.integration.backend.models;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "cases")
@EntityListeners(AuditingEntityListener.class)
public class Case {
    @Id
    @Column(name = "case_id", unique = true, nullable = false)
    private String caseId;

    private String fileName;

    @OneToOne(mappedBy = "caseRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private CasePriority casePriority;
    @OneToOne(mappedBy = "caseRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private CasePrivilege casePrivilege;
    @OneToOne(mappedBy = "caseRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private CaseMetaData caseMetaData;
    @OneToOne(mappedBy = "caseRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private JiraTransferStatus jiraTransferStatus;
    @OneToOne(mappedBy = "caseRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private ArchiveStoreTransferStatus archiveStoreTransferStatus;

    private int retries;
    private CaseTransferStatus caseTransferStatus = CaseTransferStatus.PROCESSING;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(name = "updated_at")
    //This value is updated on retries
    private LocalDateTime updated;

    public void incrementRetries() {
        this.retries++;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public CasePriority getCasePriority() {
        return casePriority;
    }

    public void setCasePriority(CasePriority casePriority) {
        this.casePriority = casePriority;
    }

    public CasePrivilege getCasePrivilege() {
        return casePrivilege;
    }

    public void setCasePrivilege(CasePrivilege casePrivilege) {
        this.casePrivilege = casePrivilege;
    }

    public CaseMetaData getCaseMetaData() {
        return caseMetaData;
    }

    public void setCaseMetaData(CaseMetaData caseMetaData) {
        this.caseMetaData = caseMetaData;
    }

    public JiraTransferStatus getJiraTransferStatus() {
        return jiraTransferStatus;
    }

    public void setJiraTransferStatus(JiraTransferStatus jiraTransferStatus) {
        this.jiraTransferStatus = jiraTransferStatus;
    }

    public ArchiveStoreTransferStatus getArchiveStoreTransferStatus() {
        return archiveStoreTransferStatus;
    }

    public void setArchiveStoreTransferStatus(ArchiveStoreTransferStatus archiveStoreTransferStatus) {
        this.archiveStoreTransferStatus = archiveStoreTransferStatus;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public CaseTransferStatus getCaseTransferStatus() {
        return caseTransferStatus;
    }

    public void setCaseTransferStatus(CaseTransferStatus caseTransferStatus) {
        this.caseTransferStatus = caseTransferStatus;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }
}
