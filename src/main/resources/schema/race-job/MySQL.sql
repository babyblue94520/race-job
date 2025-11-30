CREATE TABLE IF NOT EXISTS `race_job`
(
    `instance`              varchar(100)    NOT NULL DEFAULT '',
    `group`                 varchar(100)    NOT NULL DEFAULT '',
    `name`                  varchar(100)    NOT NULL DEFAULT '',
    `timezone`              varchar(10)     NOT NULL DEFAULT '',
    `description`           varchar(200)    NOT NULL DEFAULT '',
    `cron`                  varchar(200)    NOT NULL DEFAULT '',
    `after_group`           varchar(100)    NOT NULL DEFAULT '',
    `after_name`            varchar(100)    NOT NULL DEFAULT '',
    `prev_time`             bigint(13)      NOT NULL DEFAULT 0,
    `next_time`             bigint(13)      NOT NULL DEFAULT 0,
    `enabled`               tinyint(1)      NOT NULL DEFAULT 1,
    `state`                 int(1)          NOT NULL DEFAULT 0,
    `start_time`            bigint(13)      NOT NULL DEFAULT 0,
    `end_time`              bigint(13)      NOT NULL DEFAULT 0,
    `last_active_time`      bigint(13)      NOT NULL DEFAULT 0,
    `data`                  text            NULL,
    PRIMARY KEY (`instance`, `group`, `name`) USING BTREE
) ENGINE = InnoDB
CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
ROW_FORMAT = Dynamic;