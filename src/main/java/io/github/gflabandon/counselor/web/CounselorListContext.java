package io.github.gflabandon.counselor.web;

import io.github.gflabandon.counselor.entity.EmploymentStatus;
import io.github.gflabandon.counselor.service.BusinessException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

/** Carries one tab's list position using only known filters, never a caller-supplied return URL. */
public record CounselorListContext(String keyword, Integer departmentId, EmploymentStatus status,
                                   Integer page, int size) {
    public CounselorListContext {
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.length() > 100) throw new BusinessException("检索内容不能超过 100 个字符。");
        if (page != null) page = Math.max(1, page);
        size = Math.max(1, Math.min(size, 100));
    }

    public boolean isPresent() { return page != null; }
    public String getListUrl() { return "/counselors" + query(false); }
    public String getCreateUrl() { return "/counselors/new" + query(true); }
    public String detailUrl(int id) { return "/counselors/" + id + query(true); }
    public String editUrl(int id) { return "/counselors/" + id + "/edit" + query(true); }

    private String query(boolean context) {
        if (!isPresent()) return "";
        var query = new StringJoiner("&", "?", "");
        add(query, context ? "listKeyword" : "keyword", keyword);
        add(query, context ? "listDepartmentId" : "departmentId", departmentId);
        add(query, context ? "listStatus" : "status", status);
        add(query, context ? "listPage" : "page", page);
        add(query, context ? "listSize" : "size", size);
        return query.toString();
    }

    private static void add(StringJoiner query, String name, Object value) {
        if (value != null) query.add(name + "=" + URLEncoder.encode(value.toString(), StandardCharsets.UTF_8).replace("+", "%20"));
    }
}
