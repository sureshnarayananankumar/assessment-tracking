-- Runs once, only when the MySQL data volume is first created.
-- The database and application user are created from MYSQL_DATABASE / MYSQL_USER /
-- MYSQL_PASSWORD, utf8mb4 + UTC are set on the server command line, and all tables
-- are created by Flyway when the backend starts. Add extra one-time setup here if needed.
SELECT 'AssessHub MySQL initialised' AS info;
