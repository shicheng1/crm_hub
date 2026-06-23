-- 工单审批系统 建表脚本
CREATE DATABASE IF NOT EXISTS order_approval DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE order_approval;

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT '密码',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/APPROVER',
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

-- 初始数据
INSERT INTO sys_user (username, password, role) VALUES ('admin', '123456', 'APPROVER');
INSERT INTO sys_user (username, password, role) VALUES ('user1', '123456', 'USER');
INSERT INTO sys_user (username, password, role) VALUES ('user2', '123456', 'USER');
