-- 방명록 글(docs/db/burrow.md의 guestbook_entry). 방문자가 다른 사람의 땅굴에 남긴 글이다.
-- burrow는 V20261123_1354, user는 V3에서 만든다. 글을 쓰는 API는 05 탐색 · 방문에서 만든다.

-- 고칠 수 없고, 지우면 행을 지운다. 서로 다른 3명이 신고하면 status를 HIDDEN으로 바꿔 가린다.
-- 목록은 한 땅굴의 PUBLIC 글을 최신순(created_at, id 내림차순)으로 읽으므로 그 순서의 인덱스를 둔다.
CREATE TABLE guestbook_entry (
    id         BIGINT                   NOT NULL AUTO_INCREMENT,
    burrow_id  BIGINT                   NOT NULL,
    author_id  BIGINT                   NOT NULL,
    content    VARCHAR(50)              NOT NULL,
    status     ENUM ('PUBLIC', 'HIDDEN') NOT NULL,
    created_at DATETIME(6)              NOT NULL,
    updated_at DATETIME(6)              NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_guestbook_entry_burrow_list (burrow_id, status, created_at, id),
    INDEX idx_guestbook_entry_author (author_id),
    CONSTRAINT fk_guestbook_entry_burrow FOREIGN KEY (burrow_id) REFERENCES burrow (id),
    CONSTRAINT fk_guestbook_entry_author FOREIGN KEY (author_id) REFERENCES `user` (id)
);
