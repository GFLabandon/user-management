package io.github.gflabandon.counselor.web;

/** Minimal projection used by the administrator's account association picker. */
public record CounselorOption(int id, String employeeNo, String name, String departmentName) {}
