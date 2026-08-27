# DB 마이그레이션 스크립트

Flyway/Liquibase가 없고, `application-prod.yml`은 `spring.jpa.hibernate.ddl-auto: validate`라
**운영 DB 스키마는 코드(JPA 엔티티)를 배포한다고 자동으로 안 바뀐다.** 로컬 개발(`ddl-auto: update`)에서는
엔티티에 붙은 `@Index`/`@Column`이 그대로 반영되지만, 운영에는 여기 있는 스크립트를 수동으로 실행해야 한다.

## 적용 방법

```bash
mysql -h <운영 DB 호스트> -u <계정> -p <DB명> < db/migrations/0001_add_missing_indexes.sql
```

실행 전에 반드시 `SHOW INDEX FROM <table>;`으로 이미 있는 인덱스인지 먼저 확인할 것 — 이 스크립트는
`information_schema`를 조회해 없을 때만 생성하도록 짰지만(멱등), 운영 DB에 직접 DDL을 실행하는 작업이라
한 번 더 눈으로 확인하고 돌리는 걸 권장한다.

## 파일 목록

| 파일 | 내용 | 관련 커밋/PR |
|---|---|---|
| `0001_add_missing_indexes.sql` | 큐 클레임(SKIP LOCKED) + 영상 목록 조회용 인덱스 3종 | #34, 인덱스 점검 |
