package com.printplatform.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable pagination envelope (avoids serializing Spring's PageImpl directly). */
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public PageResponse(Page<T> p) {
        this.content = p.getContent();
        this.page = p.getNumber();
        this.size = p.getSize();
        this.totalElements = p.getTotalElements();
        this.totalPages = p.getTotalPages();
        this.last = p.isLast();
    }

    public List<T> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean isLast() { return last; }
}
