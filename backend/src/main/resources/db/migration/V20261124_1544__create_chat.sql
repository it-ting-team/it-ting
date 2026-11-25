-- 채팅(docs/db/chat.md의 chat_request, chat_room, chat_room_member, chat_message).
-- user는 V3에서 만든다. 대화 요청을 수락하면 대화방 · 참여자 · 안내 메시지가 함께 생겨서 네 테이블을 한 번에 만든다.
-- chat_message_image는 사진 올리기(uploaded_image)가 있어야 해서 채팅 ②(KAN-56)에서 만든다.

-- 1:1 대화방과 모임 대화방이 함께 쓴다. 이 작업에서는 1:1(DIRECT)만 만들고, 모임 대화방은 모임을 만드는 작업이 붙인다.
-- meetup_id는 meetup 테이블이 아직 없어 FK를 걸지 않는다. 모임 대화방을 붙이는 작업이 새 마이그레이션으로 FK를 더한다.
-- 모임 하나에 대화방은 하나이고(UNIQUE), 1:1 방은 비운다.
CREATE TABLE chat_room (
    id         BIGINT                  NOT NULL AUTO_INCREMENT,
    type       ENUM ('DIRECT', 'MEETUP') NOT NULL,
    meetup_id  BIGINT                  NULL,
    notice     TEXT                    NULL,
    created_at DATETIME(6)             NOT NULL,
    updated_at DATETIME(6)             NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_meetup UNIQUE (meetup_id),
    CONSTRAINT ck_chat_room_meetup CHECK ((type = 'MEETUP' AND meetup_id IS NOT NULL)
        OR (type = 'DIRECT' AND meetup_id IS NULL))
);

-- 1:1 대화 요청. 만료는 저장하지 않고 created_at에서 5일이 지났는지로 계산한다(살아 있는 요청: created_at이 지금 - 5일보다
-- 뒤이고 PENDING이거나 REJECTED). responded_at은 수락 · 거절 · 취소 가운데 마지막 상태 변경 시각이다.
-- 받은 요청 목록 · 수는 (receiver_id, status, created_at)으로, 보낸 요청 목록 · 수와 두 사람 사이의 살아 있는 요청 찾기는
-- (sender_id, status, created_at)으로 찾는다.
CREATE TABLE chat_request (
    id           BIGINT                                                NOT NULL AUTO_INCREMENT,
    sender_id    BIGINT                                                NOT NULL,
    receiver_id  BIGINT                                                NOT NULL,
    message      VARCHAR(50)                                           NULL,
    status       ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED') NOT NULL,
    chat_room_id BIGINT                                                NULL,
    created_at   DATETIME(6)                                           NOT NULL,
    responded_at DATETIME(6)                                           NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_request_receiver (receiver_id, status, created_at),
    INDEX idx_chat_request_sender (sender_id, status, created_at),
    CONSTRAINT ck_chat_request_not_self CHECK (sender_id <> receiver_id),
    CONSTRAINT fk_chat_request_sender FOREIGN KEY (sender_id) REFERENCES `user` (id),
    CONSTRAINT fk_chat_request_receiver FOREIGN KEY (receiver_id) REFERENCES `user` (id),
    CONSTRAINT fk_chat_request_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id)
);

-- 대화방의 참여자. 기본키는 (chat_room_id, user_id)이고 joined_at이 created_at의 구실을 한다.
-- left_at이 채워지면 그 방을 나간 것이다(다시 들어올 수 없다).
-- last_read_message_id는 chat_message를 가리키는 논리 참조다. 대화방 · 참여자 · 메시지가 서로를 가리켜 FK를 걸면 넣는 순서가
-- 꼬이고, 메시지는 지우지 않아 가리키는 줄이 사라질 일이 없어서 FK를 걸지 않는다.
CREATE TABLE chat_room_member (
    chat_room_id         BIGINT      NOT NULL,
    user_id              BIGINT      NOT NULL,
    joined_at            DATETIME(6) NOT NULL,
    left_at              DATETIME(6) NULL,
    last_read_message_id BIGINT      NULL,
    PRIMARY KEY (chat_room_id, user_id),
    INDEX idx_chat_room_member_user (user_id),
    CONSTRAINT fk_chat_room_member_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_room_member_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

-- 대화방의 메시지와 안내 메시지. 고치거나 지우지 않는다. 같은 방 안에서 id가 클수록 나중 메시지다.
-- 안내 메시지(ACCEPTED, JOINED, LEFT, WITHDRAWN)는 content가 비어 있고, 문구는 프론트엔드가 type과 sender로 만든다.
CREATE TABLE chat_message (
    id           BIGINT                                                      NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT                                                      NOT NULL,
    type         ENUM ('TEXT', 'ACCEPTED', 'JOINED', 'LEFT', 'WITHDRAWN')   NOT NULL,
    sender_id    BIGINT                                                      NOT NULL,
    content      TEXT                                                        NULL,
    created_at   DATETIME(6)                                                 NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_message_room (chat_room_id, id),
    CONSTRAINT fk_chat_message_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES `user` (id)
);
