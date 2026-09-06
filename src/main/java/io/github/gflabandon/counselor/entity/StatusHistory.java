package io.github.gflabandon.counselor.entity;

import java.time.LocalDateTime;
public class StatusHistory {

    private long id;


    private int counselorId;


    private EmploymentStatus fromStatus;


    private EmploymentStatus toStatus;


    private String actor;


    private LocalDateTime changedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getCounselorId() { return counselorId; }
    public void setCounselorId(int counselorId) { this.counselorId = counselorId; }

    public EmploymentStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(EmploymentStatus fromStatus) { this.fromStatus = fromStatus; }

    public EmploymentStatus getToStatus() { return toStatus; }
    public void setToStatus(EmploymentStatus toStatus) { this.toStatus = toStatus; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

}
