-- =============================================================================
--  作业：ORM + JDBC 增删改查演示用数据库脚本
--  数据库：DateTest      包含两张表：student（学生表）、college（学院表）
--  执行方式：
--      mysql -uroot -p < sql/date_test.sql
--  或在 MySQL 客户端中： source D:/javacode/JDBC/sql/date_test.sql
-- =============================================================================

-- 1. 创建数据库 DateTest
DROP DATABASE IF EXISTS `DateTest`;
CREATE DATABASE `DateTest`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;
USE `DateTest`;

-- 2. 学院表 college：id、name、code
DROP TABLE IF EXISTS `college`;
CREATE TABLE `college`
(
    `id`   INT         NOT NULL AUTO_INCREMENT COMMENT '学院主键，自增',
    `name` VARCHAR(64) NOT NULL COMMENT '学院名称',
    `code` VARCHAR(32) NOT NULL COMMENT '学院代码',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_college_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='学院表';

-- 3. 学生表 student：Id、name、major、age、入学时间、是否毕业、学费
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student`
(
    `id`              INT           NOT NULL AUTO_INCREMENT COMMENT '学生主键 Id，自增',
    `name`            VARCHAR(64)   NOT NULL COMMENT '姓名',
    `major`           VARCHAR(64)   NOT NULL COMMENT '专业',
    `age`             INT                    DEFAULT NULL COMMENT '年龄',
    `enrollment_date` DATE                   DEFAULT NULL COMMENT '入学时间',
    `graduated`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否毕业：1=已毕业，0=未毕业',
    `tuition`         DECIMAL(10, 2)         DEFAULT NULL COMMENT '学费',
    PRIMARY KEY (`id`),
    KEY `idx_student_major` (`major`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='学生表';

-- 4. 初始化数据（演示程序也会自行插入数据，这里提供一份初始数据便于手工验证）
INSERT INTO `college` (`name`, `code`)
VALUES ('计算机学院', 'CS'),
       ('外国语学院', 'FL'),
       ('数学学院', 'MATH');

INSERT INTO `student` (`name`, `major`, `age`, `enrollment_date`, `graduated`, `tuition`)
VALUES ('张伟', '计算机科学与技术', 19, '2021-09-01', 0, 5200.00),
       ('李娜', '软件工程', 20, '2021-09-01', 1, 5800.00),
       ('王强', '人工智能', 18, '2024-09-01', 0, 6800.00),
       ('陈静', '网络工程', 21, '2020-09-01', 1, 5000.00);

-- 5. 查看结果
SELECT * FROM `college`;
SELECT * FROM `student`;
