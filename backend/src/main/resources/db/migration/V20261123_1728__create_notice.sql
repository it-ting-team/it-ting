-- 공지사항(docs/db/notification.md의 notice). 관리자가 올리고 사용자는 읽기만 한다.
-- 올리고 고치는 API는 11 운영 · 관리자에서 만든다. 제목 길이 100자는 KAN-58에서 정했다.

-- 지우면 deleted_at을 채운다. 목록은 지우지 않은 공지를 최신순(created_at, id 내림차순)으로 읽으므로 그 순서의 인덱스를 둔다.
CREATE TABLE notice (
    id         BIGINT                              NOT NULL AUTO_INCREMENT,
    category   ENUM ('NOTICE', 'UPDATE', 'EVENT') NOT NULL,
    title      VARCHAR(100)                        NOT NULL,
    content    TEXT                                NOT NULL,
    created_at DATETIME(6)                         NOT NULL,
    updated_at DATETIME(6)                         NOT NULL,
    deleted_at DATETIME(6)                         NULL,
    PRIMARY KEY (id),
    INDEX idx_notice_list (deleted_at, created_at, id)
);
