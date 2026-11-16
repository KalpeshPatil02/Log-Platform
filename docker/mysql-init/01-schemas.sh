#!/bin/bash
# Runs once on first start of an empty MySQL volume. One schema and one user per service (database ownership).
set -e
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS identity_db     CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS logs_db         CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS alert_db        CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS notification_db CHARACTER SET utf8mb4;

CREATE USER IF NOT EXISTS 'identity_svc'@'%'     IDENTIFIED BY '${IDENTITY_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'processing_svc'@'%'   IDENTIFIED BY '${PROCESSING_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'query_svc'@'%'        IDENTIFIED BY '${QUERY_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'alert_svc'@'%'        IDENTIFIED BY '${ALERT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'notification_svc'@'%' IDENTIFIED BY '${NOTIFICATION_DB_PASSWORD}';

GRANT ALL ON identity_db.*     TO 'identity_svc'@'%';
GRANT ALL ON logs_db.*         TO 'processing_svc'@'%';   -- sole writer of logs_db (also runs its Flyway migrations)
GRANT SELECT ON logs_db.*      TO 'query_svc'@'%';        -- query-service is read-only
GRANT ALL ON alert_db.*        TO 'alert_svc'@'%';
GRANT ALL ON notification_db.* TO 'notification_svc'@'%';
FLUSH PRIVILEGES;
SQL
