package io.github.gflabandon.counselor.entity;

public enum EmploymentStatus {
    ACTIVE("在职"), INACTIVE("停用");
    private final String label;
    EmploymentStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
