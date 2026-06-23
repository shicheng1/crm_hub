-- v2: 多级审批流 + 操作日志 + 部门管理
USE order_approval;

-- 部门表
DROP TABLE IF EXISTS department;
CREATE TABLE department (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_id   BIGINT       DEFAULT 0 COMMENT '上级部门ID(0为顶级)',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 审批流模板
DROP TABLE IF EXISTS approval_flow;
CREATE TABLE approval_flow (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '流程名称',
    description VARCHAR(500) COMMENT '流程描述',
    status      TINYINT      DEFAULT 1 COMMENT '1启用 0禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流模板';

-- 审批流步骤
DROP TABLE IF EXISTS approval_flow_step;
CREATE TABLE approval_flow_step (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    flow_id     BIGINT       NOT NULL COMMENT '所属流程ID',
    step_order  INT          NOT NULL COMMENT '步骤顺序(1,2,3...)',
    step_name   VARCHAR(50)  NOT NULL COMMENT '步骤名称',
    approver_id BIGINT       COMMENT '指定审批人ID(为空则自动匹配部门主管)',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_flow_id (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流步骤';

-- 审批记录
DROP TABLE IF EXISTS approval_record;
CREATE TABLE approval_record (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL COMMENT '工单ID',
    step_id      BIGINT       NOT NULL COMMENT '审批步骤ID',
    step_order   INT          NOT NULL COMMENT '步骤顺序',
    approver_id  BIGINT       NOT NULL COMMENT '审批人ID',
    result       VARCHAR(20)  NOT NULL COMMENT 'APPROVED/REJECTED',
    remark       VARCHAR(500) COMMENT '审批备注',
    operate_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录';

-- 操作日志
DROP TABLE IF EXISTS order_operation_log;
CREATE TABLE order_operation_log (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL COMMENT '工单ID',
    operator_id  BIGINT       NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(50) COMMENT '操作人姓名',
    operation    VARCHAR(50)  NOT NULL COMMENT '操作类型(CREATE/SUBMIT/APPROVE/REJECT/CLOSE)',
    detail       VARCHAR(500) COMMENT '操作详情',
    operate_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- 给 work_order 加字段
ALTER TABLE work_order ADD COLUMN flow_id BIGINT COMMENT '审批流模板ID';
ALTER TABLE work_order ADD COLUMN current_step INT DEFAULT 0 COMMENT '当前审批步骤序号';
ALTER TABLE work_order ADD COLUMN dept_id BIGINT COMMENT '所属部门ID';

-- 给 sys_user 加字段
ALTER TABLE sys_user ADD COLUMN dept_id BIGINT COMMENT '所属部门ID';

-- 初始数据
INSERT INTO department (id, name, parent_id) VALUES (1, '技术部', 0);
INSERT INTO department (id, name, parent_id) VALUES (2, '业务部', 0);
INSERT INTO department (id, name, parent_id) VALUES (3, '管理层', 0);

UPDATE sys_user SET dept_id = 1 WHERE id IN (2, 3);  -- user1, user2 属于技术部
UPDATE sys_user SET dept_id = 3 WHERE id = 1;         -- admin 属于管理层

-- 审批流模板：普通工单审批（部门主管 -> 管理层）
INSERT INTO approval_flow (id, name, description, status) VALUES (1, '普通工单审批', '部门主管审批 → 管理层审批', 1);
INSERT INTO approval_flow_step (id, flow_id, step_order, step_name, approver_id) VALUES (1, 1, 1, '部门主管审批', NULL);
INSERT INTO approval_flow_step (id, flow_id, step_order, step_name, approver_id) VALUES (2, 1, 2, '管理层审批', 1);

-- 审批流模板：快速审批（admin 直接审批）
INSERT INTO approval_flow (id, name, description, status) VALUES (2, '快速审批', '单级审批，admin直接处理', 1);
INSERT INTO approval_flow_step (id, flow_id, step_order, step_name, approver_id) VALUES (3, 2, 1, '直接审批', 1);
