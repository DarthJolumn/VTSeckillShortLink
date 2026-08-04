CREATE TABLE IF NOT EXISTS t_url (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_url  TEXT NOT NULL,
    short_key     VARCHAR(50) NOT NULL,
    title         VARCHAR(255),
    user_id       BIGINT,
    clicks        INT DEFAULT 0 NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at    DATETIME NULL,
    UNIQUE INDEX uk_short_key (short_key),
    INDEX idx_user_id (user_id),
    INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_analytics (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_id      BIGINT NOT NULL,
    clicked_at  DATETIME NOT NULL,
    ip_address  VARCHAR(100) NOT NULL,
    user_agent  TEXT NOT NULL,
    referrer    VARCHAR(255),
    country     VARCHAR(100),
    device      VARCHAR(50),
    browser     VARCHAR(50),
    os          VARCHAR(50),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_url_id (url_id),
    INDEX idx_clicked_at (clicked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
