-- V5: 독서기록 조회용 인덱스
--
-- 누적 집계(F-0502)는 참가 신청 → 등록 도서 → 독서기록 순서로 찾아 들어간다.
-- enrollment_books(enrollment_id)는 V1의 UNIQUE (enrollment_id, book_id)가 이미 인덱스 역할을 하므로,
-- reading_logs(enrollment_book_id)만 추가한다.
CREATE INDEX idx_reading_logs_enrollment_book_id ON reading_logs (enrollment_book_id);
