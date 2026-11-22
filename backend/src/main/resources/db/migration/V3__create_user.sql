-- 계정, 프로필, 정지 기록(docs/db/account.md의 user, user_profile, user_suspension).
-- 계정은 회원가입 3에서 SSAFY 인증을 마칠 때 계정과 프로필을 한 트랜잭션으로 만든다.
-- user는 MySQL의 키워드라 이름을 `로 감싼다.

CREATE TABLE `user` (
    id                BIGINT                       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(254)                 NULL,
    password_hash     VARCHAR(100)                 NULL,
    google_sub        VARCHAR(255)                 NULL,
    google_email      VARCHAR(254)                 NULL,
    mattermost_id     VARCHAR(64)                  NULL,
    ssafy_verified_at DATETIME(6)                  NOT NULL,
    terms_agreed_at   DATETIME(6)                  NOT NULL,
    onboarded_at      DATETIME(6)                  NULL,
    role              ENUM ('USER', 'ADMIN')       NOT NULL,
    status            ENUM ('ACTIVE', 'WITHDRAWN') NOT NULL,
    created_at        DATETIME(6)                  NOT NULL,
    updated_at        DATETIME(6)                  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_google_sub UNIQUE (google_sub),
    CONSTRAINT uk_user_mattermost_id UNIQUE (mattermost_id)
);

CREATE TABLE user_profile (
    user_id           BIGINT       NOT NULL,
    nickname          VARCHAR(12)  NULL,
    generation        SMALLINT     NOT NULL,
    campus_floor_id   BIGINT       NOT NULL,
    intro             VARCHAR(150) NULL,
    profile_image_url VARCHAR(500) NULL,
    mole_head         VARCHAR(30)  NULL,
    mole_face         VARCHAR(30)  NULL,
    mole_neck         VARCHAR(30)  NULL,
    mole_hand         VARCHAR(30)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_profile_nickname UNIQUE (nickname),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_user_profile_campus_floor FOREIGN KEY (campus_floor_id) REFERENCES campus_floor (id)
);

CREATE TABLE user_suspension (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    starts_at   DATETIME(6)  NOT NULL,
    ends_at     DATETIME(6)  NOT NULL,
    imposed_by  BIGINT       NOT NULL,
    lifted_at   DATETIME(6)  NULL,
    lifted_by   BIGINT       NULL,
    lift_reason TEXT         NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_suspension_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_user_suspension_imposed_by FOREIGN KEY (imposed_by) REFERENCES `user` (id),
    CONSTRAINT fk_user_suspension_lifted_by FOREIGN KEY (lifted_by) REFERENCES `user` (id)
);
