-- 알림과 알림 설정(docs/db/notification.md의 notification, notification_setting).
-- 알림은 모임 · 채팅 · 커뮤니티 · 땅굴 영역의 API와 예약 작업이 만들고, 알림 API가 읽고 읽음으로 바꾼다.
-- user는 V3에서 만든다.

-- 받는 사람마다 한 줄이다. 제목과 보조 설명은 저장하지 않고 type, 갈 곳, actor_id로 만든다.
-- event_key는 같은 사건을 두 번 만들지 않게 하는 키다. 일정 임박 알림은 회차마다, 새 메시지 알림은
-- 안 읽은 동안 대화방마다 키를 두고, 새 메시지 알림을 읽으면 키를 비운다.
-- 30일이 지난 줄은 매일 지운다. 목록 · 수 · 정리가 모두 (user_id, created_at)으로 찾는다.
CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    type        ENUM (
                    'MEETUP_APPLICATION_RECEIVED',
                    'MEETUP_APPLICATION_ACCEPTED',
                    'MEETUP_APPLICATION_REJECTED',
                    'MEETUP_PARTICIPATION_CANCELED',
                    'MEETUP_KICKED',
                    'MEETUP_UPDATED',
                    'MEETUP_CANCELED',
                    'MEETUP_DELETED',
                    'MEETUP_VACANCY_OPENED',
                    'MEETUP_START_REMINDER',
                    'CHAT_MESSAGE_RECEIVED',
                    'CHAT_REQUEST_RECEIVED',
                    'CHAT_REQUEST_ACCEPTED',
                    'POST_COMMENTED',
                    'POST_LIKED',
                    'COMMENT_REPLIED',
                    'COMMENT_LIKED',
                    'POST_HIDDEN',
                    'COMMENT_HIDDEN',
                    'GUESTBOOK_ENTRY_CREATED',
                    'GUESTBOOK_ENTRY_HIDDEN',
                    'NOTICE_PUBLISHED'
                )            NOT NULL,
    target_type ENUM ('MEETUP', 'CHAT_ROOM', 'CHAT_REQUEST', 'POST', 'GUESTBOOK', 'BURROW', 'NOTICE') NOT NULL,
    target_id   BIGINT       NULL,
    actor_id    BIGINT       NULL,
    read_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    event_key   VARCHAR(191) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_user_event_key UNIQUE (user_id, event_key),
    INDEX idx_notification_user_created (user_id, created_at, id),
    INDEX idx_notification_created (created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_notification_actor FOREIGN KEY (actor_id) REFERENCES `user` (id)
);

-- 갈래별로 알림을 켜고 끈다. 줄이 없는 계정은 모두 켠 것으로 본다. 바꾸는 API는 10 설정에 있다.
CREATE TABLE notification_setting (
    user_id      BIGINT      NOT NULL,
    meetup_on    BOOLEAN     NOT NULL DEFAULT TRUE,
    chat_on      BOOLEAN     NOT NULL DEFAULT TRUE,
    community_on BOOLEAN     NOT NULL DEFAULT TRUE,
    burrow_on    BOOLEAN     NOT NULL DEFAULT TRUE,
    notice_on    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_setting_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);
