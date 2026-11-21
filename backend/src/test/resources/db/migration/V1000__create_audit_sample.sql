-- 테스트에서만 적용되는 마이그레이션(src/test/resources). 운영 DB에는 만들어지지 않는다.
-- 공통 날짜 칼럼과 DB 설정을 검증하는 AuditSample 엔티티의 테이블이다.
-- 실제 마이그레이션과 번호가 겹치지 않게 1000번을 쓴다.
CREATE TABLE audit_sample (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
