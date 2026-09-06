package io.github.gflabandon.counselor.entity;


public class SystemAccount {
    private int id;
    private String username;
    private String passwordHash;
    private AccountRole role;
    private boolean enabled;
    private Integer counselorId;
    private int version;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public AccountRole getRole() { return role; }
    public void setRole(AccountRole role) { this.role = role; }

    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Integer getCounselorId() { return counselorId; }
    public void setCounselorId(Integer counselorId) { this.counselorId = counselorId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

}
