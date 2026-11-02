-- V2: 독서 독려 알림 수신 동의 (F-0101, F-0701)
--
-- 가입할 때 "독서 독려 알림을 받을게요(선택)"에 체크하면 동의한 시각을 저장합니다.
-- NULL이면 동의하지 않은 회원입니다. 알림(F-0701)은 이 값이 있는 회원에게만 보냅니다.
-- 언제 동의했는지 남겨야 나중에 동의 여부를 증명할 수 있어서 true/false 대신 시각으로 저장합니다.

ALTER TABLE users ADD COLUMN reminder_agreed_at TIMESTAMPTZ;
