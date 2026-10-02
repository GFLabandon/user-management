package io.github.gflabandon.counselor.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import jakarta.validation.constraints.*;
import io.github.gflabandon.counselor.service.BusinessException;

/** Date inputs remain strings so invalid dates can be corrected on the query form. */
public class AuditQuery {
    @Size(max = 255, message = "操作者不能超过 255 个字符。")
    private String actor = "";
    @Pattern(regexp = "|ACCOUNT|COUNSELOR|DEPARTMENT|REQUEST", message = "请选择有效的对象类型。")
    private String targetType = "";
    @Size(max = 64, message = "对象编号不能超过 64 个字符。")
    private String targetId = "";
    @Pattern(regexp = "|SUCCESS|FAILURE|DENIED", message = "请选择有效的结果。")
    private String outcome = "";
    @Size(max = 10, message = "开始日期请使用 yyyy-MM-dd 格式。")
    private String startDate = "";
    @Size(max = 10, message = "结束日期请使用 yyyy-MM-dd 格式。")
    private String endDate = "";
    @Min(value = 1, message = "页码必须大于或等于 1。")
    private int page = 1;
    @Min(value = 1, message = "每页条数必须在 1 至 100 之间。")
    @Max(value = 100, message = "每页条数不能超过 100。")
    private int size = 20;

    public record Filter(String actor, String targetType, String targetId, String outcome,
                         LocalDateTime from, LocalDateTime until) {}

    public Filter filter() {
        if (!Set.of(10, 20, 50, 100).contains(size)) throw new BusinessException("每页条数请选择 10、20、50 或 100。");
        LocalDate start = date(startDate), end = date(endDate);
        if (start != null && end != null && start.isAfter(end)) throw new BusinessException("开始日期不能晚于结束日期，请调整后查询。");
        return new Filter(actor, targetType, targetId, outcome,
                start == null ? null : start.atStartOfDay(), end == null ? null : end.plusDays(1).atStartOfDay());
    }

    private static LocalDate date(String value) {
        if (value.isEmpty()) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("format", value, 0);
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1 || date.getYear() > 9998) throw new DateTimeParseException("range", value, 0);
            return date;
        } catch (DateTimeParseException invalid) {
            throw new BusinessException("日期无效，请输入 0001 至 9998 年内的有效日期（yyyy-MM-dd）。");
        }
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = clean(value); }
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { targetType = clean(value); }
    public String getTargetId() { return targetId; }
    public void setTargetId(String value) { targetId = clean(value); }
    public String getOutcome() { return outcome; }
    public void setOutcome(String value) { outcome = clean(value); }
    public String getStartDate() { return startDate; }
    public void setStartDate(String value) { startDate = clean(value); }
    public String getEndDate() { return endDate; }
    public void setEndDate(String value) { endDate = clean(value); }
    public int getPage() { return page; }
    public void setPage(int value) { page = value; }
    public int getSize() { return size; }
    public void setSize(int value) { size = value; }
}
