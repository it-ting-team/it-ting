-- 탈퇴 기록(docs/db/account.md의 user_withdrawal). user는 V3에서 만든다.
-- 다시 가입하면 새 계정 id를 쓰므로 계정 하나에 탈퇴 기록은 하나다. 한 번 쓰고 바꾸지 않아 updated_at을 두지 않는다.
-- reason은 고르지 않았으면 비어 있다. 값의 글은 프론트엔드가 갖는다.
CREATE TABLE user_withdrawal (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    reason     ENUM ('NOT_USING', 'COURSE_ENDED', 'NO_MATCH', 'INCONVENIENT', 'UNPLEASANT', 'ETC') NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_withdrawal_user UNIQUE (user_id),
    CONSTRAINT fk_user_withdrawal_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);
