-- 시연·테스트용 회차 (F-0201). local과 dev에서만 실행 (application-local.yml 의 spring.flyway.locations)
-- 운영(prod) 설정에는 db/seed 폴더를 절대 넣지 말 것
--
-- 2027 회차는 2027년 2월부터 신청이 가능해서, 개발·시연 중에도 참가 신청을 해 볼 수 있게 따로 둔 회차.
-- 날짜를 고정하지 않고 이 파일이 실행되는 날 기준으로 계산 (7일 전 ~ 1년 뒤). 고정 날짜는 시간이 지나면 다시 신청을 못 하게 되기 때문.
-- R__(반복) 마이그레이션이라 V 번호를 쓰지 않고, 이 파일 내용이 바뀔 때만 다시 실행됨.
-- 이미 있으면 건너뜀.

INSERT INTO rounds (round_no, year, start_date, end_date, apply_start, apply_end)
VALUES (99, 2026, CURRENT_DATE - 7, CURRENT_DATE + 365, CURRENT_DATE - 7, CURRENT_DATE + 365)
ON CONFLICT (year, round_no) DO NOTHING;

INSERT INTO courses (round_id, name, is_family, target_pages, target_books, target_reviews, sort_order)
SELECT r.id, c.name, c.is_family, c.target_pages, c.target_books, c.target_reviews, c.sort_order
FROM rounds r
CROSS JOIN (VALUES
    ('걷기코스',     FALSE,  3500,  10,  3, 1),
    ('단축코스',     FALSE,  7000,  20,  5, 2),
    ('하프코스',     FALSE, 17500,  50, 10, 3),
    ('풀코스(개인)', FALSE, 42195, 120, 15, 4),
    ('풀코스(가족)', TRUE,  42195, 120, 15, 5),
    ('고수코스',     FALSE, 49000, 140, 20, 6)
) AS c (name, is_family, target_pages, target_books, target_reviews, sort_order)
WHERE r.year = 2026 AND r.round_no = 99
  AND NOT EXISTS (SELECT 1 FROM courses WHERE round_id = r.id);
