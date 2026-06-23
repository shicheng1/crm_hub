-- v3: 多审批人 + 驳回策略 + 相同审批人跳过
USE order_approval;

-- 步骤-审批人关联表（一个步骤可以配多个审批人）
DROP TABLE IF EXISTS approval_step_approver;
CREATE TABLE approval_step_approver (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    step_id     BIGINT NOT NULL COMMENT '审批步骤ID',
    user_id     BIGINT NOT NULL COMMENT '审批人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_step_id (step_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='步骤审批人';

-- 审批流模板增加驳回策略
ALTER TABLE approval_flow ADD COLUMN reject_mode VARCHAR(20) DEFAULT 'RESTART'
    COMMENT '驳回策略: RESTART=回到第一步, PREVIOUS=回到上一步, ORIGIN=退回发起人';

-- 工单增加「发起步骤」（驳回时用）
ALTER TABLE work_order ADD COLUMN submit_step INT DEFAULT 1
    COMMENT '发起人提交时所在的步骤(驳回ORIGIN模式回到此步)';

-- 迁移旧数据：把 approval_flow_step.approver_id 迁移到新表
INSERT INTO approval_step_approver (step_id, user_id)
SELECT id, approver_id FROM approval_flow_step WHERE approver_id IS NOT NULL;

-- 旧字段不再使用，设为可空（不删字段，兼容）
-- approval_flow_step.approver_id 保留但不再依赖

-- 更新审批流的驳回策略
UPDATE approval_flow SET reject_mode = 'ORIGIN' WHERE id = 1;  -- 普通审批：退回发起人
UPDATE approval_flow SET reject_mode = 'PREVIOUS' WHERE id = 2; -- 快速审批：退回上一步

-- 给新表加更多测试数据：步骤1配置两个审批人（admin和user2都可以审）
-- 步骤1已有 admin(user_id=1)，再加 user2(user_id=3)
INSERT INTO approval_step_approver (step_id, user_id) VALUES (1, 3);
