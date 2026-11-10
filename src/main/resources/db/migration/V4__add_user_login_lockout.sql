-- V4: 참가자 로그인 실패 잠금 (F-0102)
--
-- 문자 인증 없이 전화번호 + 비밀번호로 로그인하므로 비밀번호를 계속 넣어 보는 공격을 막아야 합니다.
-- 연속으로 정해진 횟수(기본 5번) 틀리면 locked_until까지 로그인을 막습니다. 성공하면 0으로 되돌립니다.
-- 관리자 잠금(F-0104)은 이후 마이그레이션에서 추가합니다.

ALTER TABLE users
    ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until       TIMESTAMPTZ;
