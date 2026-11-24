CREATE TABLE regular_burrow (
    user_id    BIGINT      NOT NULL,
    burrow_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, burrow_id),
    INDEX idx_regular_burrow_burrow (burrow_id),
    CONSTRAINT fk_regular_burrow_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_regular_burrow_burrow FOREIGN KEY (burrow_id) REFERENCES burrow (id)
);

CREATE TABLE daily_recommendation (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    recommended_on DATE        NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_recommendation_user_date UNIQUE (user_id, recommended_on),
    CONSTRAINT fk_daily_recommendation_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

CREATE TABLE daily_recommendation_member (
    daily_recommendation_id BIGINT  NOT NULL,
    sort_order              TINYINT NOT NULL,
    target_user_id          BIGINT  NOT NULL,
    PRIMARY KEY (daily_recommendation_id, sort_order),
    CONSTRAINT ck_daily_recommendation_member_order CHECK (sort_order BETWEEN 1 AND 3),
    CONSTRAINT fk_daily_recommendation_member_day FOREIGN KEY (daily_recommendation_id) REFERENCES daily_recommendation (id),
    CONSTRAINT fk_daily_recommendation_member_user FOREIGN KEY (target_user_id) REFERENCES `user` (id)
);
