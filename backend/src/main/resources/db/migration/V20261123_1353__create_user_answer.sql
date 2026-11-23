-- 자기소개 질문의 답변(docs/db/account.md의 user_answer). 온보딩을 확정할 때 질문 3개와 답변을 저장한다.
-- user는 V3, intro_question은 V20261123_0037에서 만든다.

-- slot은 자기소개에서 보이는 자리(1 ~ 3)다. 한 사람이 같은 질문을 두 자리에 고르지 못하게 한다.
CREATE TABLE user_answer (
    user_id           BIGINT      NOT NULL,
    slot              TINYINT     NOT NULL,
    intro_question_id BIGINT      NOT NULL,
    answer            VARCHAR(50) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, slot),
    CONSTRAINT uk_user_answer_user_question UNIQUE (user_id, intro_question_id),
    CONSTRAINT ck_user_answer_slot CHECK (slot BETWEEN 1 AND 3),
    CONSTRAINT ck_user_answer_answer_length CHECK (CHAR_LENGTH(answer) BETWEEN 10 AND 50),
    CONSTRAINT fk_user_answer_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_user_answer_intro_question FOREIGN KEY (intro_question_id) REFERENCES intro_question (id)
);
