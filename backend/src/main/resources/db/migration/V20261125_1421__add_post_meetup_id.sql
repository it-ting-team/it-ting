-- 글에 연결하는 모임. 모임 홍보 · 모임 후기는 꼭 연결하고, 자유 글은 연결해도 되고 안 해도 된다(NULL).
-- 연결한 뒤 모임이 모집 완료 · 취소돼도 그대로 두고, 삭제된 모임(소프트 삭제)도 줄을 지우지 않는다.
ALTER TABLE post
    ADD COLUMN meetup_id BIGINT NULL AFTER category,
    ADD CONSTRAINT fk_post_meetup FOREIGN KEY (meetup_id) REFERENCES meetup (id);
