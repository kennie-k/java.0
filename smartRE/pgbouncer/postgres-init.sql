ALTER SYSTEM SET password_encryption = 'md5';
SELECT pg_reload_conf();
