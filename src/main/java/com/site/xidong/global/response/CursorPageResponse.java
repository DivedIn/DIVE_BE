package com.site.xidong.global.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 기반 페이지네이션 응답 래퍼.
 *
 * 리포지토리에서 요청한 size보다 1개 더(size+1) 가져온 뒤 {@link #of}에 그대로 넘기면,
 * 마지막 한 건으로 hasNext 여부를 판단하고 실제 응답에서는 잘라낸다 — count 쿼리 없이
 * "다음 페이지가 있는지"를 알 수 있는 흔한 커서 페이지네이션 트릭이다.
 */
@Getter
@Builder
public class CursorPageResponse<T> {
    private final List<T> items;
    private final Long nextCursor;
    private final boolean hasNext;

    public static <T> CursorPageResponse<T> of(List<T> overFetched, int size, Function<T, Long> cursorExtractor) {
        boolean hasNext = overFetched.size() > size;
        List<T> items = hasNext ? overFetched.subList(0, size) : overFetched;
        Long nextCursor = hasNext && !items.isEmpty()
                ? cursorExtractor.apply(items.get(items.size() - 1))
                : null;
        return CursorPageResponse.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}
