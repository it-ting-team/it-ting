-- 기반 설정. 테이블은 기능을 만들 때 영역별 마이그레이션(V2 이후)으로 더한다.
-- 서버 설정과 상관없이 이 DB의 기본 문자셋과 정렬을 utf8mb4로 맞춘다. 이모지와 한글을 그대로 저장한다.
ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
