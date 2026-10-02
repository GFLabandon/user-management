package io.github.gflabandon.counselor.entity;

import java.util.Map;

/** Presentation only: persisted event codes and history stay unchanged. */
public final class AuditLabels {
    private AuditLabels() {}
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("LOGIN", "登录"), Map.entry("ACCESS_DENIED", "访问受限"),
            Map.entry("ACCOUNT_CREATE", "创建账号"), Map.entry("ACCOUNT_UPDATE", "更新账号"), Map.entry("ACCOUNT_WRITE", "账号变更"),
            Map.entry("COUNSELOR_CREATE", "建立档案"), Map.entry("COUNSELOR_UPDATE", "更新档案"),
            Map.entry("COUNSELOR_DEACTIVATE", "停用档案"), Map.entry("COUNSELOR_WRITE", "档案变更"),
            Map.entry("DEPARTMENT_CREATE", "创建院系"), Map.entry("DEPARTMENT_UPDATE", "更新院系"),
            Map.entry("DEPARTMENT_DELETE", "删除院系"), Map.entry("DEPARTMENT_WRITE", "院系变更"));
    private static final Map<String, String> TARGETS = Map.of("ACCOUNT", "账号", "COUNSELOR", "辅导员档案", "DEPARTMENT", "院系", "REQUEST", "访问请求");
    private static final Map<String, String> OUTCOMES = Map.of("SUCCESS", "成功", "FAILURE", "失败", "DENIED", "已拒绝");
    private static final Map<String, String> REASONS = Map.of("OK", "操作完成", "INVALID_CREDENTIALS", "账号或密码不正确",
            "CSRF", "表单安全校验失效", "ROLE", "权限不足", "VALIDATION", "输入校验未通过",
            "WRITE_FAILED", "变更未完成", "SAVE_FAILED", "保存未完成", "DEACTIVATE_FAILED", "停用未完成");
    public static String action(String code) { return label(ACTIONS, code, "未知操作"); }
    public static String target(String code) { return label(TARGETS, code, "未知对象"); }
    public static String outcome(String code) { return label(OUTCOMES, code, "未知结果"); }
    public static String reason(String code) { return label(REASONS, code, "原因未分类"); }
    private static String label(Map<String, String> labels, String code, String fallback) {
        return code == null ? fallback : labels.getOrDefault(code, fallback);
    }
}
