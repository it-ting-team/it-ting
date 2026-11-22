-- 학기(docs/db/burrow.md의 term), 관심사 · 자기소개 질문(docs/db/account.md의 interest_category, interest,
-- user_interest, intro_question). 운영자가 DB에서 직접 관리하는 목록이다(관리 화면 없음).
-- user_answer는 03 온보딩에서 만든다.

CREATE TABLE term (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    year       SMALLINT    NOT NULL,
    half       TINYINT     NOT NULL,
    opened_at  DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_term_year_half UNIQUE (year, half),
    CONSTRAINT ck_term_half CHECK (half IN (1, 2))
);

CREATE TABLE interest_category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_interest_category_name UNIQUE (name)
);

-- 관심사 태그는 대분류 없이 이름만 보여 주므로 이름은 대분류가 달라도 겹치지 않게 한다.
CREATE TABLE interest (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    interest_category_id BIGINT      NOT NULL,
    name                 VARCHAR(30) NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    deleted_at           DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_interest_name UNIQUE (name),
    CONSTRAINT fk_interest_interest_category FOREIGN KEY (interest_category_id) REFERENCES interest_category (id)
);

-- 한 사람은 관심사를 3 ~ 10개 고른다. sort_order 1이 대표 관심사다.
CREATE TABLE user_interest (
    user_id     BIGINT      NOT NULL,
    interest_id BIGINT      NOT NULL,
    sort_order  TINYINT     NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, interest_id),
    CONSTRAINT ck_user_interest_sort_order CHECK (sort_order BETWEEN 1 AND 10),
    CONSTRAINT fk_user_interest_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_user_interest_interest FOREIGN KEY (interest_id) REFERENCES interest (id)
);

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

-- 첫 학기는 2026 하반기, 2026-07-01 00:00(한국 시간)에 열린 것으로 둔다. 다음 학기를 여는 방법은
-- 명세의 보류한 결정이라, 정해지기 전까지는 새 마이그레이션으로 더한다.
INSERT INTO term (year, half, opened_at, created_at, updated_at)
VALUES (2026, 2, '2026-06-30 15:00:00', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- 관심사는 명세(docs/spec/00-common.md 5)의 표 순서대로 넣는다. API가 id 순으로 주므로 화면도 표 순서가 된다.
-- 대분류의 이모지(아이콘)는 프론트엔드가 갖는다.
INSERT INTO interest_category (id, name, created_at, updated_at)
VALUES (1, '음식·음료', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '스포츠', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '문화생활', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, '게임', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '음악·댄스', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (6, '아웃도어', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '외국어·자기계발', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '책·글', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (9, '사진·영상', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '미술·수공예', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (11, '덕질·수집', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '반려동물·식물', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO interest (interest_category_id, name, created_at, updated_at)
VALUES (1, '맛집탐방', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (1, '카페/커피', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (1, '주류/와인/위스키', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (1, '다이어트/건강식', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (1, '요리/레시피', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (1, '베이킹/디저트', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (1, '차/다도', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '헬스/필라테스/요가', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '러닝/마라톤', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '클라이밍', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '축구', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '야구', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '농구/배구/구기', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '탁구/테니스/배드민턴', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '당구/포켓볼/볼링', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '스키/보드', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '격투기/무술', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (2, '수상레저', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '영화', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '드라마/예능', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '애니메이션', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '콘서트/페스티벌', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '미술관/전시', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '연극/뮤지컬', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (3, '사주/타로', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, 'PC게임', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, '모바일게임', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, '보드게임', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, '방탈출/추리', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, '콘솔게임', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, 'VR/아케이드', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, '큐브/퍼즐/두뇌', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (4, '바둑/장기/체스', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, 'K-POP', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '팝송/해외음악', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, 'JPOP/OST', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '힙합/락', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '재즈/클래식', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '댄스/무용', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '보컬', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '기타/베이스/일렉', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '피아노/건반', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '드럼/관현악기', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (5, '작곡/미디/DJ', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (6, '국내여행', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (6, '해외여행', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (6, '등산/트레킹', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (6, '캠핑/글램핑', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (6, '자전거/바이크/드라이브', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (6, '낚시', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '영어', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '일본어', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '중국어', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '스페인어', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '기타언어', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '금융/투자/재테크', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (7, '봉사', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '소설', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '시/에세이', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '웹툰/만화', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '웹소설', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '시사/경제', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '역사/철학/인문', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (8, '글쓰기/필사/블로그', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (9, '스마트폰촬영', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (9, '영상제작/편집', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (9, '보정/포토샵', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (9, '미러리스/DSLR', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (9, '필름/컴팩트', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (9, '액션캠/드론', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '드로잉/회화', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '자수/뜨개/패브릭', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '캔들/디퓨저/석고', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '가죽/도예/목공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '캘리그라피', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '플라워아트', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (10, '액세서리/기타', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (11, '굿즈/포토카드', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (11, '키링/인형', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (11, '다꾸/소품/문구', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (11, '피규어', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (11, '프라모델/레고', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (11, 'LP/음반', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '강아지', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '고양이', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '설치류/소동물', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '물고기', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '새', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '곤충/파충류/기타', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       (12, '식물/테라리움', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

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
