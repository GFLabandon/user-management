package io.github.gflabandon.counselor.web;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {
    public boolean hasPrevious() { return page > 1; }
    public boolean hasNext() { return page < totalPages; }
}
