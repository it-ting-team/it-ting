-- 브라우저 푸시를 보낼 구독(docs/db/notification.md의 push_subscription).
-- "브라우저 알림 받기"를 켠 브라우저마다 한 줄이다. 알림이 생기면 그 사람의 모든 줄로 푸시를 보낸다.
-- user는 V3에서 만든다.

-- endpoint는 브라우저가 준 푸시 수신 주소다. 영문 주소라 ASCII로 두면 2,000자에도 UNIQUE 색인을 걸 수 있다
-- (색인 한도 3,072바이트). 주소의 대소문자는 뜻이 있으므로 대소문자를 가리는 ascii_bin으로 비교한다.
-- 브라우저 하나는 한 계정의 푸시만 받는다. 같은 주소를 다른 계정이 등록하면 줄을 늘리지 않고 user_id와 키를 바꾼다.
-- p256dh는 브라우저의 공개 키, auth는 인증 값이다. 둘 다 base64url 글이고 푸시 내용을 암호화할 때 쓴다.
CREATE TABLE push_subscription (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    endpoint   VARCHAR(2000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    p256dh     VARCHAR(255)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    auth       VARCHAR(255)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint),
    INDEX idx_push_subscription_user (user_id),
    CONSTRAINT fk_push_subscription_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);
