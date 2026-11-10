-- V6: 2027 회차와 코스 6개 (F-0201)
--
-- 회차 관리(F-0806)가 2순위라 첫 회차는 마이그레이션으로 넣습니다. 모든 환경(local, dev, prod)에 들어갑니다.
-- 코스 기준 출처: 도서관 완주 기준 문서 (걷기, 단축, 하프, 풀(개인/가족), 고수)
-- 운영 기간 출처: 완주 기준 문서 "1월 5일 ~ 11월 30일"
-- 참가 신청 기간: 임의값. 2026년 신청 기간(2월 2일 ~ 6월 30일)을 그대로 적용. 도서관 확인 후 새 마이그레이션으로 수정
--
-- 각자 로컬 DB에 2027년 1회차를 직접 넣어 둔 경우에도 실패하지 않도록, 이미 있으면 건너뜁니다.

INSERT INTO rounds (round_no, year, start_date, end_date, apply_start, apply_end)
VALUES (1, 2027, DATE '2027-01-05', DATE '2027-11-30', DATE '2027-02-02', DATE '2027-06-30')
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
WHERE r.year = 2027 AND r.round_no = 1
  AND NOT EXISTS (SELECT 1 FROM courses WHERE round_id = r.id);
