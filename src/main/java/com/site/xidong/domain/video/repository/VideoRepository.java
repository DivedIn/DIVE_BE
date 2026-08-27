package com.site.xidong.domain.video.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import com.site.xidong.domain.video.entity.Video;

public interface VideoRepository extends JpaRepository<Video, Long> {
    Optional<Video> findById(Long id);

    // [N+1] siteUser/question/question.questionSet 전부 *ToOne이라 fetch join 3개를 묶어도
    // MultipleBagFetchException 걱정 없이 한 쿼리로 끝난다. cursor(직전 페이지 마지막 videoId)보다
    // 작은 id를 최신순(id DESC)으로 size+1개 가져와 다음 페이지 존재 여부를 count 쿼리 없이 판단한다.
    @Query("SELECT v FROM Video v " +
            "JOIN FETCH v.siteUser " +
            "JOIN FETCH v.question q " +
            "JOIN FETCH q.questionSet " +
            "WHERE v.isOpen = true AND (:cursor IS NULL OR v.id < :cursor) " +
            "ORDER BY v.id DESC")
    List<Video> findOpenVideosAfterCursor(@Param("cursor") Long cursor, Pageable pageable);

    @Query("SELECT v FROM Video v " +
            "JOIN FETCH v.siteUser " +
            "JOIN FETCH v.question q " +
            "JOIN FETCH q.questionSet " +
            "WHERE v.siteUser.username = :username AND (:cursor IS NULL OR v.id < :cursor) " +
            "ORDER BY v.id DESC")
    List<Video> findMyVideosAfterCursor(@Param("username") String username, @Param("cursor") Long cursor, Pageable pageable);

    @Query("SELECT v FROM Video v JOIN FETCH v.question WHERE v.id = :id")
    Optional<Video> findByIdWithQuestion(Long id);
}
