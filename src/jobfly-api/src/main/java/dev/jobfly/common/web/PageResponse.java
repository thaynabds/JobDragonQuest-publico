package dev.jobfly.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/** Envelope de paginação estável na API, independente da serialização interna do Spring Data. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
