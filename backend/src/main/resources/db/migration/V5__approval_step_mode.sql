-- V5: 步骤审批模式（或签/会签）
-- 由 migration/V4__approval_step_mode.sql 合并而来，编号顺延为 V5。
USE order_approval;

ALTER TABLE approval_flow_step
    ADD COLUMN approve_mode VARCHAR(10) NOT NULL DEFAULT 'ANY'
    COMMENT '审批模式: ANY=任一审批人通过即可, ALL=全部审批人通过才进入下一步'
    AFTER step_name;

-- 兼容历史流程：默认全部按原行为 ANY（或签）
UPDATE approval_flow_step SET approve_mode = 'ANY' WHERE approve_mode IS NULL OR approve_mode = '';
