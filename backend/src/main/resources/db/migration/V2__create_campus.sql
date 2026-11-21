-- 캠퍼스와 캠퍼스마다의 층(docs/db/burrow.md의 campus, campus_floor).
-- 운영자가 DB에서 직접 관리한다(관리 화면 없음). 지울 때는 줄을 지우지 않고 deleted_at을 채운다.

CREATE TABLE campus (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(20) NOT NULL,
    sort_order INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_name UNIQUE (name)
);

CREATE TABLE campus_floor (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    campus_id  BIGINT      NOT NULL,
    floor      INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_floor_campus_id_floor UNIQUE (campus_id, floor),
    CONSTRAINT fk_campus_floor_campus FOREIGN KEY (campus_id) REFERENCES campus (id)
);

-- 처음 값은 명세의 임시 값이다(docs/spec/open-issues.md의 "임시 값"). 실제 값이 정해지면 새 마이그레이션으로 고친다.
INSERT INTO campus (name, sort_order, created_at, updated_at)
VALUES ('서울', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('대전', 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('부울경', 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('구미', 4, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('광주', 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- 캠퍼스마다 1 ~ 20층
INSERT INTO campus_floor (campus_id, floor, created_at, updated_at)
WITH RECURSIVE floors (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM floors WHERE n < 20
)
SELECT campus.id, floors.n, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM campus
CROSS JOIN floors;
