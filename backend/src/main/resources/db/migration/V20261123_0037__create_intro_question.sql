-- 자기소개 질문(docs/db/account.md의 intro_question). 운영자가 더하거나 고친다.
-- interest_category를 참조하므로 V20261123_0036__create_interest보다 뒤의 시각이다. user_answer는 03 온보딩에서 만든다.

-- 질문은 숨기거나 지우지 않는다. 관심사 질문은 대분류가 꼭 있고, TMI 질문은 비워 둔다.
CREATE TABLE intro_question (
    id                   BIGINT                   NOT NULL AUTO_INCREMENT,
    type                 ENUM ('INTEREST', 'TMI') NOT NULL,
    interest_category_id BIGINT                   NULL,
    content              TEXT                     NOT NULL,
    created_at           DATETIME(6)              NOT NULL,
    updated_at           DATETIME(6)              NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_intro_question_category CHECK (
        (type = 'INTEREST' AND interest_category_id IS NOT NULL)
            OR (type = 'TMI' AND interest_category_id IS NULL)),
    CONSTRAINT fk_intro_question_interest_category FOREIGN KEY (interest_category_id)
        REFERENCES interest_category (id)
);

-- 자기소개 질문 첫 값(docs/work/KAN-43.md 부록). 관심사 질문 24개(대분류마다 2개), TMI 16개 = 60% : 40%.
INSERT INTO intro_question (type, interest_category_id, content, created_at, updated_at)
VALUES ('INTEREST', 1, '요즘 가장 자주 가는 맛집이나 카페는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 1, '누군가에게 꼭 해 주고 싶은 나만의 요리는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 2, '운동을 시작하게 된 계기는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 2, '같이 해 보고 싶은 운동은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 3, '최근에 본 것 중 가장 기억에 남는 작품은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 3, '몇 번이고 다시 보고 싶은 작품은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 4, '요즘 빠져 있는 게임은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 4, '같이 하자고 권하고 싶은 게임은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 5, '요즘 가장 많이 듣는 노래는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 5, '꼭 가 보고 싶은 공연이나 무대는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 6, '가장 기억에 남는 여행지는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 6, '다음에 꼭 가 보고 싶은 곳은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 7, '요즘 새로 배우고 있는 것은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 7, '올해 꼭 이루고 싶은 목표는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 8, '인생 책이나 인생 웹툰은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 8, '요즘 읽고 있는 것은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 9, '가장 아끼는 사진 한 장에 담긴 이야기는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 9, '꼭 찍어 보고 싶은 장면은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 10, '직접 만들어 본 것 가운데 가장 뿌듯한 것은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 10, '도전해 보고 싶은 만들기는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 11, '가장 아끼는 소장품은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 11, '덕질을 시작하게 된 계기는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 12, '함께 사는 반려동물이나 식물을 소개해 주세요', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('INTEREST', 12, '키워 보고 싶은 동물이나 식물은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '최근에 산 것 가운데 가장 잘 산 것은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '아침형 인간? 저녁형 인간?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '나만 아는 우리 동네 숨은 명소는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '스트레스를 푸는 나만의 방법은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '요즘 가장 자주 먹는 메뉴는?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '내 MBTI가 딱 맞다고 느낀 순간은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '휴일 하루를 마음대로 쓸 수 있다면?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '어릴 때 꿈은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '요즘 나를 웃게 하는 것은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '가장 좋아하는 계절과 그 까닭은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, 'SSAFY에서 가장 기억에 남는 순간은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '개발하면서 생긴 나만의 습관은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '나를 한 단어로 표현한다면?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '요즘 휴대폰에서 가장 많이 쓰는 앱은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '최근에 새로 알게 된 재미있는 사실은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('TMI', NULL, '다른 사람이 잘 모르는 나의 반전 매력은?', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
