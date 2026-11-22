-- 학기(docs/db/burrow.md의 term). 운영자가 DB에서 직접 관리한다(관리 화면 없음).

CREATE TABLE term (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    year       SMALLINT    NOT NULL,
    half       TINYINT     NOT NULL,
    opened_at  DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_term_year_half UNIQUE (year, half),
    CONSTRAINT ck_term_half CHECK (half IN (1, 2))
);

-- 첫 학기는 2026 하반기, 2026-07-01 00:00(한국 시간)에 열린 것으로 둔다. 다음 학기를 여는 방법은
-- 명세의 보류한 결정이라, 정해지기 전까지는 새 마이그레이션으로 더한다.
INSERT INTO term (year, half, opened_at, created_at, updated_at)
VALUES (2026, 2, '2026-06-30 15:00:00', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
