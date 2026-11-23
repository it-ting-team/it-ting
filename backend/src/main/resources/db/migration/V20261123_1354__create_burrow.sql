-- 땅굴과 땅굴 입구(docs/db/burrow.md의 burrow, burrow_entrance). 온보딩을 확정할 때 함께 만든다.
-- user는 V3, campus_floor는 V2, term은 V20261123_0012에서 만든다.

-- 한 계정에 땅굴은 하나다. 방의 모습은 저장하지 않고 주인의 관심사로 그린다.
CREATE TABLE burrow (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_burrow_user UNIQUE (user_id),
    CONSTRAINT fk_burrow_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

-- 땅굴이 지금 놓인 들판(학기 + 캠퍼스-층)과 자리 번호. 땅굴 하나에 입구 하나이고, 이사하면 이 행을 옮긴다.
-- 자리 번호는 그 들판의 가장 큰 번호 + 1이다. 번호를 줄 때 campus_floor 행을 잠가 겹치지 않게 한다.
CREATE TABLE burrow_entrance (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    burrow_id       BIGINT      NOT NULL,
    term_id         BIGINT      NOT NULL,
    campus_floor_id BIGINT      NOT NULL,
    slot            INT         NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_burrow_entrance_burrow UNIQUE (burrow_id),
    CONSTRAINT uk_burrow_entrance_field_slot UNIQUE (term_id, campus_floor_id, slot),
    CONSTRAINT ck_burrow_entrance_slot CHECK (slot >= 1),
    CONSTRAINT fk_burrow_entrance_burrow FOREIGN KEY (burrow_id) REFERENCES burrow (id),
    CONSTRAINT fk_burrow_entrance_term FOREIGN KEY (term_id) REFERENCES term (id),
    CONSTRAINT fk_burrow_entrance_campus_floor FOREIGN KEY (campus_floor_id) REFERENCES campus_floor (id)
);
