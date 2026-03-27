CREATE TABLE IF NOT EXISTS `race_job`
(
    `instance`              varchar(100)    NOT NULL DEFAULT '',
    `group`                 varchar(100)    NOT NULL DEFAULT '',
    `name`                  varchar(100)    NOT NULL DEFAULT '',
    `key`                   varchar(100)    NOT NULL DEFAULT '',
    `version`               int             NOT NULL DEFAULT 1,
    `timezone`              varchar(10)     NOT NULL DEFAULT '',
    `description`           varchar(200)    NOT NULL DEFAULT '',
    `cron`                  varchar(200)    NOT NULL DEFAULT '',
    `depends_key`           varchar(100)    NOT NULL DEFAULT '',
    `prev_time`             bigint          NOT NULL DEFAULT 0,
    `next_time`             bigint          NOT NULL DEFAULT 0,
    `enabled`               tinyint         NOT NULL DEFAULT 1,
    `state`                 int             NOT NULL DEFAULT 0,
    `start_time`            bigint          NOT NULL DEFAULT 0,
    `end_time`              bigint          NOT NULL DEFAULT 0,
    `last_active_time`      bigint          NOT NULL DEFAULT 0,
    `data`                  text            NULL,
    PRIMARY KEY (`instance`, `group`, `name`) USING BTREE
) ENGINE = InnoDB ROW_FORMAT = Dynamic;
