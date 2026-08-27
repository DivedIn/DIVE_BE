-- 0001_add_missing_indexes.sql
--
-- 인덱스 점검 (P0) 결과 반영. ddl-auto: validate라 운영 DB엔 수동 적용해야 한다.
--
-- 실행 전 실제 컬럼명 확인 방법:
--   SHOW CREATE TABLE video;
--   SHOW CREATE TABLE video_processing_queue;
--
-- ⚠️ 티켓 원문은 video(site_user_id)를 요청했지만, Video 엔티티가
--   @JoinColumn(name = "ID")로 siteUser FK 컬럼명을 "id"로 못박아 놔서
--   실제 컬럼은 site_user_id가 아니라 id다. 아래 스크립트는 실제 컬럼명 기준.
--   (이 세션에서 동일 엔티티 매핑으로 로컬 컨테이너를 띄워 DESCRIBE video로 직접 확인함.)
--
-- 이 스크립트는 멱등하다 — information_schema.statistics로 이미 있는 인덱스인지
-- 먼저 확인하고, 없을 때만 생성한다. 여러 번 실행해도 안전하다.

DELIMITER $$

DROP PROCEDURE IF EXISTS _create_index_if_missing $$
CREATE PROCEDURE _create_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('CREATED: ', p_table, '.', p_index) AS result;
    ELSE
        SELECT CONCAT('SKIPPED (already exists): ', p_table, '.', p_index) AS result;
    END IF;
END $$

DELIMITER ;

-- ---------------------------------------------------------------------------
-- 1) video_processing_queue(status, created_at)
--    큐 클레임 쿼리(SKIP LOCKED)가 이 인덱스 없이 실행되면 풀 테이블 스캔 +
--    filesort가 붙어, InnoDB가 LIMIT을 적용하기 전 스캔 중 만난 행을 전부 잠근다.
--    SKIP LOCKED를 붙여도 사실상 무용지물이 된다 — 실측 확인(#34 PR 참고).
--    JPA @Index(idx_queue_status_created_at)로 이미 코드에 반영돼 로컬(ddl-auto=update)엔
--    있지만, 운영(ddl-auto=validate)엔 아직 수동 반영이 안 됐을 수 있다.
CALL _create_index_if_missing(
    'video_processing_queue',
    'idx_queue_status_created_at',
    'ALTER TABLE video_processing_queue ADD INDEX idx_queue_status_created_at (status, created_at)'
);

-- ---------------------------------------------------------------------------
-- 2) video(is_open, video_id)
--    findOpenVideosAfterCursor()가 "isOpen으로 거르고 video_id로 정렬"하는데,
--    단일 컬럼 인덱스만으론 정렬에 filesort가 붙는다 — (필터, 정렬) 복합 인덱스로
--    LIMIT 도달 즉시 스캔을 멈출 수 있게 한다(SKIP LOCKED 작업에서 확인한 것과 동일 패턴).
CALL _create_index_if_missing(
    'video',
    'idx_video_is_open_id',
    'ALTER TABLE video ADD INDEX idx_video_is_open_id (is_open, video_id)'
);

-- ---------------------------------------------------------------------------
-- 3) video(id, video_id) — site_user FK
--    findMyVideosAfterCursor()가 "siteUser로 거르고 video_id로 정렬"한다.
--    티켓 원문의 video(site_user_id)는 실제 컬럼명과 다르다 — 위 주석 참고.
CALL _create_index_if_missing(
    'video',
    'idx_video_site_user_id',
    'ALTER TABLE video ADD INDEX idx_video_site_user_id (id, video_id)'
);

DROP PROCEDURE IF EXISTS _create_index_if_missing;
