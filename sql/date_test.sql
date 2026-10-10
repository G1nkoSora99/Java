DROP DATABASE IF EXISTS `DateTest`;
CREATE DATABASE `DateTest` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `DateTest`;

DROP TABLE IF EXISTS `college`;
CREATE TABLE `college`
(
    `id`   INT         NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL,
    `code` VARCHAR(32) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_college_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

DROP TABLE IF EXISTS `student`;
CREATE TABLE `student`
(
    `id`              INT            NOT NULL AUTO_INCREMENT,
    `name`            VARCHAR(64)    NOT NULL,
    `major`           VARCHAR(64)    NOT NULL,
    `age`             INT                     DEFAULT NULL,
    `enrollment_date` DATE                    DEFAULT NULL,
    `graduated`       TINYINT(1)     NOT NULL DEFAULT 0,
    `tuition`         DECIMAL(10, 2)          DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_student_major` (`major`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO `college` (`name`, `code`)
VALUES ('计算机学院', 'CS'),
       ('外国语学院', 'FL'),
       ('数学学院', 'MATH');

INSERT INTO `student` (`name`, `major`, `age`, `enrollment_date`, `graduated`, `tuition`)
VALUES ('张伟', '计算机科学与技术', 19, '2021-09-01', 0, 5200.00),
       ('李娜', '软件工程', 20, '2021-09-01', 1, 5800.00),
       ('王强', '人工智能', 18, '2024-09-01', 0, 6800.00),
       ('陈静', '网络工程', 21, '2020-09-01', 1, 5000.00);
