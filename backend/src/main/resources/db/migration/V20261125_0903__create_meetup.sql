CREATE TABLE meetup (
    id                  BIGINT                   NOT NULL AUTO_INCREMENT,
    host_id             BIGINT                   NOT NULL,
    category            VARCHAR(20)              NOT NULL,
    title               VARCHAR(30)              NOT NULL,
    description         TEXT                     NULL,
    capacity            INT                      NOT NULL,
    supplies            VARCHAR(100)             NULL,
    accepts_message     BOOLEAN                  NOT NULL,
    starts_at           DATETIME(6)              NOT NULL,
    ends_at             DATETIME(6)              NULL,
    recruit_deadline_at DATETIME(6)              NULL,
    mode                ENUM ('OFFLINE','ONLINE') NOT NULL,
    place               VARCHAR(100)             NULL,
    place_detail        VARCHAR(100)             NULL,
    join_url            VARCHAR(500)             NULL,
    canceled_at         DATETIME(6)              NULL,
    deleted_at          DATETIME(6)              NULL,
    created_at          DATETIME(6)              NOT NULL,
    updated_at          DATETIME(6)              NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_meetup_upcoming (deleted_at, canceled_at, starts_at),
    CONSTRAINT fk_meetup_host FOREIGN KEY (host_id) REFERENCES `user` (id),
    CONSTRAINT ck_meetup_category CHECK (category IN ('HOBBY','FOOD','STUDY','ETC','GAME','EXERCISE')),
    CONSTRAINT ck_meetup_capacity CHECK (capacity BETWEEN 2 AND 50),
    CONSTRAINT ck_meetup_ends_at CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_meetup_recruit_deadline CHECK (recruit_deadline_at IS NULL OR recruit_deadline_at <= starts_at),
    CONSTRAINT ck_meetup_place CHECK (
        (mode = 'OFFLINE' AND place IS NOT NULL AND join_url IS NULL)
        OR (mode = 'ONLINE' AND place IS NULL AND place_detail IS NULL)
    )
);

CREATE TABLE meetup_application (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    meetup_id         BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    status            ENUM ('PENDING','WITHDRAWN','REJECTED','CONFIRMED','CANCELED','KICKED','MEETUP_CANCELED') NOT NULL,
    message           VARCHAR(100) NULL,
    created_at        DATETIME(6) NOT NULL,
    status_changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meetup_application_user UNIQUE (meetup_id, user_id),
    INDEX idx_meetup_application_status (meetup_id, status, status_changed_at, id),
    CONSTRAINT fk_meetup_application_meetup FOREIGN KEY (meetup_id) REFERENCES meetup (id),
    CONSTRAINT fk_meetup_application_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

CREATE TABLE meetup_like (
    meetup_id     BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    vacancy_alert BOOLEAN NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (meetup_id, user_id),
    CONSTRAINT fk_meetup_like_meetup FOREIGN KEY (meetup_id) REFERENCES meetup (id),
    CONSTRAINT fk_meetup_like_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);
