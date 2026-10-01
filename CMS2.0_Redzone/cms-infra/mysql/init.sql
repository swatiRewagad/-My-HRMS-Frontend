-- CMS Database Initialization
-- Grants and performance tuning for production

CREATE DATABASE IF NOT EXISTS cms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON cms_db.* TO 'cms_user'@'%';
FLUSH PRIVILEGES;

USE cms_db;

-- Performance tuning (session-level for init)
SET GLOBAL innodb_buffer_pool_instances = 4;
SET GLOBAL innodb_read_io_threads = 8;
SET GLOBAL innodb_write_io_threads = 8;
