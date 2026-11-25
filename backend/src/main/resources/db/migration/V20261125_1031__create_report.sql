-- 신고와 관리자의 신고 조치(docs/db/community.md의 report, report_action).
-- user는 V3에서 만든다. target_id는 대상 종류에 따라 post · comment · user · guestbook_entry를 가리키는 논리 참조라 FK를 두지 않는다.

-- 한 사람은 한 대상을 한 번만 신고한다. 가리기는 마지막 복구 뒤에 접수된 신고(id가 더 큰 것)를 세므로
-- 대상별 id 순서의 색인을 둔다. 내가 신고했는지(isReportedByMe)는 UNIQUE 색인으로 찾는다.
CREATE TABLE report (
    id             BIGINT                                         NOT NULL AUTO_INCREMENT,
    reporter_id    BIGINT                                         NOT NULL,
    target_type    ENUM ('POST', 'COMMENT', 'MEMBER', 'GUESTBOOK') NOT NULL,
    target_id      BIGINT                                         NOT NULL,
    target_version INT                                            NULL,
    reason         ENUM ('SPAM', 'ABUSE', 'OBSCENE', 'ETC')       NOT NULL,
    detail         VARCHAR(100)                                   NULL,
    status         ENUM ('RECEIVED', 'RESOLVED')                  NOT NULL,
    created_at     DATETIME(6)                                    NOT NULL,
    updated_at     DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_reporter_target (reporter_id, target_type, target_id),
    INDEX idx_report_target (target_type, target_id, id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES `user` (id),
    -- 글과 댓글은 신고 당시의 버전 번호를 적고, 사용자와 방명록 글은 비운다.
    CONSTRAINT ck_report_target_version CHECK (
        (target_type IN ('POST', 'COMMENT') AND target_version IS NOT NULL)
        OR (target_type IN ('MEMBER', 'GUESTBOOK') AND target_version IS NULL)),
    -- 기타 사유만 상세 내용을 적는다.
    CONSTRAINT ck_report_detail CHECK (
        (reason = 'ETC' AND detail IS NOT NULL) OR (reason <> 'ETC' AND detail IS NULL))
);

-- 신고 종결(REPORT_ACTIONED · REPORT_DISMISSED)은 report_id로 대상을 읽고,
-- 대상 복구(TARGET_RESTORED)는 대상과 복구 직전의 가장 큰 신고 id를 적는다. 관리자 API(11 관리자)가 쓴다.
CREATE TABLE report_action (
    id                         BIGINT                                                            NOT NULL AUTO_INCREMENT,
    action_type                ENUM ('REPORT_ACTIONED', 'REPORT_DISMISSED', 'TARGET_RESTORED') NOT NULL,
    admin_id                   BIGINT                                                            NOT NULL,
    reason                     TEXT                                                              NOT NULL,
    created_at                 DATETIME(6)                                                       NOT NULL,
    report_id                  BIGINT                                                            NULL,
    target_type                ENUM ('POST', 'COMMENT', 'GUESTBOOK')                             NULL,
    target_id                  BIGINT                                                            NULL,
    restored_through_report_id BIGINT                                                            NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_action_report (report_id),
    INDEX idx_report_action_restore (target_type, target_id, action_type, id),
    CONSTRAINT fk_report_action_admin FOREIGN KEY (admin_id) REFERENCES `user` (id),
    CONSTRAINT fk_report_action_report FOREIGN KEY (report_id) REFERENCES report (id),
    CONSTRAINT ck_report_action_columns CHECK (
        (action_type IN ('REPORT_ACTIONED', 'REPORT_DISMISSED')
            AND report_id IS NOT NULL AND target_type IS NULL AND target_id IS NULL
            AND restored_through_report_id IS NULL)
        OR (action_type = 'TARGET_RESTORED'
            AND report_id IS NULL AND target_type IS NOT NULL AND target_id IS NOT NULL))
);
