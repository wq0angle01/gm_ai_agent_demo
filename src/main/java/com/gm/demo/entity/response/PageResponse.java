package com.gm.demo.entity.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 自定义分页壳。不直接序列化 Spring Data 的 Page，是为了不把第三方内部字段
 * （pageable / paged / empty）变成对外契约。
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.hasNext());
    }
}
