-- 채팅 메시지에 붙인 사진과 보여 주는 순서(docs/db/chat.md의 chat_message_image).
-- chat_message는 V20261124_1544에서, uploaded_image는 V20261123_1545(커뮤니티)에서 만든다.
-- 사진은 먼저 올려 uploaded_image에 기록하고(POST /api/v1/images), 메시지를 보낼 때 이 표로 붙인다.
-- 메시지 하나에 3장까지이고(sort_order 1 ~ 3), 사진 하나는 메시지 하나에만 붙는다(image_id UNIQUE).
-- 메시지는 고치거나 지우지 않으므로 이 표의 줄도 바뀌지 않는다. 그래서 날짜 칼럼을 두지 않는다(붙인 시각은 메시지의 created_at이다).
CREATE TABLE chat_message_image (
    chat_message_id BIGINT  NOT NULL,
    sort_order      TINYINT NOT NULL,
    image_id        BIGINT  NOT NULL,
    PRIMARY KEY (chat_message_id, sort_order),
    CONSTRAINT uk_chat_message_image_image UNIQUE (image_id),
    CONSTRAINT ck_chat_message_image_sort_order CHECK (sort_order BETWEEN 1 AND 3),
    CONSTRAINT fk_chat_message_image_message FOREIGN KEY (chat_message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_chat_message_image_image FOREIGN KEY (image_id) REFERENCES uploaded_image (id)
);
