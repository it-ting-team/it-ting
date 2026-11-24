-- 댓글과 답글에 누른 좋아요(docs/db/community.md의 comment_like). 켜고 끄는 관계이고, 끄면 줄을 지운다.
-- comment는 V20261123_1545에서 만들었다. 글의 좋아요(post_like)와 같은 모양이다.
CREATE TABLE comment_like (
    comment_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (comment_id, user_id),
    CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES comment (id),
    CONSTRAINT fk_comment_like_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);
