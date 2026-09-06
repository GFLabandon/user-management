package io.github.gflabandon.counselor.entity;

public enum AccountRole {
    ADMIN("管理员"), VIEWER("只读用户");
    private final String label;
    AccountRole(String label) { this.label = label; }
    public String getLabel() { return label; }
}
