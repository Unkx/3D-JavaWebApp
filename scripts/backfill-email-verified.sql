-- One-time backfill for the email-verification feature (2026-07-11).
--
-- Hibernate's ddl-auto=update adds the new `email_verified` column with a database-level
-- DEFAULT of false (see User.java's @ColumnDefault("false")), so every row that existed
-- before this deploy starts out unverified. Run this ONCE, immediately after deploying the
-- backend that introduces the column, to grandfather existing accounts in — otherwise every
-- user who registered before today is locked out of login until they click a verification
-- link they never received.
--
-- Safe to run more than once (idempotent — only touches rows still at the default).
-- Does NOT affect accounts created after this point in time; new registrations correctly
-- start unverified via application logic (AuthService.register()).

UPDATE users
SET email_verified = true
WHERE email_verified = false;
