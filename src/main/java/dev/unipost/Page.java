package dev.unipost;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Page<T> {
    private final List<T> data;
    private final Map<String, Object> meta;
    private final String nextCursor;

    public Page(List<T> data, Map<String, Object> meta, String nextCursor) {
        this.data = data == null ? Collections.emptyList() : data;
        this.meta = meta == null ? Collections.emptyMap() : meta;
        this.nextCursor = nextCursor;
    }

    public List<T> getData() {
        return data;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
