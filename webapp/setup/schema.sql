-- Uni-Vishwas backend schema (MySQL 5.7+ / MariaDB 10.3+).
-- Apply once via setup.php OR by importing this file in phpMyAdmin.

CREATE TABLE IF NOT EXISTS users (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(190) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    society         VARCHAR(120) NOT NULL DEFAULT 'Uninav Heights',
    house_number    VARCHAR(40)  NULL,
    email_verified  TINYINT(1)   NOT NULL DEFAULT 0,
    verify_token    VARCHAR(80)  NULL,
    is_admin        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_users_email (email),
    KEY idx_users_verify_token (verify_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sessions (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     INT UNSIGNED NOT NULL,
    token       VARCHAR(80)  NOT NULL,
    expires_at  DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_sessions_token (token),
    KEY idx_sessions_user (user_id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source-of-truth roster: known residents and their flat numbers.
-- The register endpoint matches the registered name against this
-- table to auto-allocate house_number on signup.
CREATE TABLE IF NOT EXISTS residents (
    id                INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name              VARCHAR(120) NOT NULL,
    house_number      VARCHAR(40)  NOT NULL,
    society           VARCHAR(120) NOT NULL DEFAULT 'Uninav Heights',
    assigned_user_id  INT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_residents_house (society, house_number),
    KEY idx_residents_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- House-number change requests submitted from the Profile screen.
-- Status starts as 'pending' until an admin approves / rejects.
CREATE TABLE IF NOT EXISTS house_change_requests (
    id                INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           INT UNSIGNED NOT NULL,
    current_house     VARCHAR(40)  NULL,
    requested_house   VARCHAR(40)  NOT NULL,
    status            ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
    admin_note        VARCHAR(255) NULL,
    created_at        DATETIME     NOT NULL,
    decided_at        DATETIME     NULL,
    decided_by        INT UNSIGNED NULL,
    PRIMARY KEY (id),
    KEY idx_hcr_user (user_id),
    KEY idx_hcr_status (status),
    CONSTRAINT fk_hcr_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
