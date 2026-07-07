-- V1: 基础表结构 + 初始用户（BCrypt 密码）
-- 由 db/init.sql 合并而来。注意：Flyway 连接已存在的数据库，故不再包含 CREATE DATABASE。
USE order_approval;

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT '密码',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/APPROVER/ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 工单表
DROP TABLE IF EXISTS work_order;
CREATE TABLE work_order (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    title        VARCHAR(200) NOT NULL COMMENT '工单标题',
    content      TEXT         COMMENT '工单内容',
    status       INT          NOT NULL DEFAULT 0 COMMENT '状态: 0待审批 1审批中 2已通过 3已驳回 4已关闭',
    creator_id   BIGINT       NOT NULL COMMENT '创建人ID',
    approver_id  BIGINT       COMMENT '审批人ID',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    approve_time DATETIME     COMMENT '审批时间',
    INDEX idx_creator (creator_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- 初始数据（密码均为 BCrypt 加密的 123456）
INSERT INTO sys_user (username, password, role, status) VALUES ('admin', '$2a$10$UufSIYKvR.buOeLGoK5Mc.kwOvigsT2rqh0T5dSynDz0Vdto9My6q', 'ADMIN', 1);
INSERT INTO sys_user (username, password, role, status) VALUES ('user1', '$2a$10$08mG7m0DNBQNd1MHhWQ8aeXttg52Oz0LCocS2CqRDZcgHusj/rIoq', 'USER', 1);
INSERT INTO sys_user (username, password, role, status) VALUES ('user2', '$2a$10$8Sh2GPpUxk7yPN3oID3/uudZtYE4TLtTb3oQkoXNOFQbKBJ00iHeu', 'APPROVER', 1);
