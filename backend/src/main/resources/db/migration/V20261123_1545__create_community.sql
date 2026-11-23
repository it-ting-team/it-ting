-- 커뮤니티 글과, 글 API가 읽는 댓글 · 좋아요 · 저장(docs/db/community.md).
-- 댓글 좋아요(comment_like)는 08 커뮤니티 ②, 신고(report, report_action)는 08 커뮤니티 ③에서 만든다.
-- 글에 연결하는 모임(post.meetup_id)은 meetup 테이블이 생긴 뒤 08 커뮤니티 ③에서 더한다.
-- user는 V3, interest_category는 V20261123_0036에서 만든다.

-- 제목과 본문은 post_version에 있다. 삭제해도 줄을 남긴다(삭제된 글 안내, 답글 · 신고의 참조).
CREATE TABLE post (
    id         BIGINT                                 NOT NULL AUTO_INCREMENT,
    author_id  BIGINT                                 NOT NULL,
    category   ENUM ('PROMOTION', 'REVIEW', 'FREE')   NOT NULL,
    status     ENUM ('PUBLIC', 'HIDDEN', 'DELETED')   NOT NULL,
    view_count INT                                    NOT NULL,
    created_at DATETIME(6)                            NOT NULL,
    updated_at DATETIME(6)                            NOT NULL,
    deleted_at DATETIME(6)                            NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_post_view_count CHECK (view_count >= 0),
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES `user` (id)
);

-- 글 목록(공개 글을 최신순)
CREATE INDEX idx_post_status_created_at ON post (status, created_at);

-- 고칠 때마다 한 줄씩 쌓고, 번호가 가장 큰 줄을 보여 준다. 한 번 쓴 줄은 고치지 않는다.
CREATE TABLE post_version (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    post_id    BIGINT        NOT NULL,
    version    INT           NOT NULL,
    title      VARCHAR(50)   NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_version_post_version UNIQUE (post_id, version),
    CONSTRAINT ck_post_version_version CHECK (version >= 1),
    CONSTRAINT fk_post_version_post FOREIGN KEY (post_id) REFERENCES post (id)
);

-- 글에 붙인 관심사 대분류 태그. 글마다 0 ~ 5개. 글과 함께 생기므로 시각 칼럼이 없다.
CREATE TABLE post_interest (
    post_id              BIGINT NOT NULL,
    interest_category_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, interest_category_id),
    CONSTRAINT fk_post_interest_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_interest_interest_category FOREIGN KEY (interest_category_id) REFERENCES interest_category (id)
);

-- 올린 사진. 글이나 채팅 메시지에 붙이기 전에 먼저 올린다. 올리는 API는 08 커뮤니티 ③에서 만든다.
CREATE TABLE uploaded_image (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    uploader_id BIGINT       NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_uploaded_image_uploader FOREIGN KEY (uploader_id) REFERENCES `user` (id)
);

-- 글에 붙인 사진과 순서. sort_order 1이 대표 사진이다. 사진 하나는 글 하나에만 붙는다.
CREATE TABLE post_image (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL,
    sort_order TINYINT     NOT NULL,
    image_id   BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_image_image UNIQUE (image_id),
    CONSTRAINT uk_post_image_post_sort_order UNIQUE (post_id, sort_order),
    CONSTRAINT ck_post_image_sort_order CHECK (sort_order BETWEEN 1 AND 3),
    CONSTRAINT fk_post_image_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_image_image FOREIGN KEY (image_id) REFERENCES uploaded_image (id)
);

-- 댓글과 한 단계 답글. 본문은 comment_version에 있다. parent_id는 답글이 속한 맨 위 댓글이다.
CREATE TABLE comment (
    id              BIGINT                               NOT NULL AUTO_INCREMENT,
    post_id         BIGINT                               NOT NULL,
    author_id       BIGINT                               NOT NULL,
    parent_id       BIGINT                               NULL,
    mention_user_id BIGINT                               NULL,
    status          ENUM ('PUBLIC', 'HIDDEN', 'DELETED') NOT NULL,
    created_at      DATETIME(6)                          NOT NULL,
    updated_at      DATETIME(6)                          NOT NULL,
    deleted_at      DATETIME(6)                          NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES `user` (id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment (id),
    CONSTRAINT fk_comment_mention_user FOREIGN KEY (mention_user_id) REFERENCES `user` (id)
);

-- 글의 댓글 수(삭제되지 않은 것)
CREATE INDEX idx_comment_post_status ON comment (post_id, status);

CREATE TABLE comment_version (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    comment_id BIGINT       NOT NULL,
    version    INT          NOT NULL,
    content    VARCHAR(300) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_comment_version_comment_version UNIQUE (comment_id, version),
    CONSTRAINT ck_comment_version_version CHECK (version >= 1),
    CONSTRAINT fk_comment_version_comment FOREIGN KEY (comment_id) REFERENCES comment (id)
);

-- 켜고 끄는 관계. 끄면 줄을 지운다.
CREATE TABLE post_like (
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

-- 인기 점수(최근 7일에 받은 좋아요 수)
CREATE INDEX idx_post_like_created_at ON post_like (created_at, post_id);

CREATE TABLE post_bookmark (
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_post_bookmark_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_bookmark_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);
