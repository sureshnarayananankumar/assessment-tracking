-- =====================================================================
-- AssessHub - step 1: create the database and an application user.
-- Run this in MySQL Workbench while connected as an administrative user
-- (for example root) to your MySQL Server 8.x instance.
--
-- BEFORE RUNNING: replace CHANGE_ME_STRONG_PASSWORD with a strong password
-- and put the same value in DB_PASSWORD in your .env file.
-- Never commit the real password to source control.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS assesshub
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- '%' allows connections from any host (needed when the backend runs in Docker).
-- For tighter security, replace '%' with the backend host/IP or a subnet such as '172.%'.
CREATE USER IF NOT EXISTS 'assesshub_app'@'%' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

-- DDL privileges are required because Flyway applies schema migrations at startup.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON assesshub.* TO 'assesshub_app'@'%';

FLUSH PRIVILEGES;

-- Optional: confirm the server runs in UTC-compatible mode (the application stores UTC instants).
SELECT @@global.time_zone AS global_time_zone, @@session.time_zone AS session_time_zone, VERSION() AS mysql_version;
