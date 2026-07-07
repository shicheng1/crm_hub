-- V6: 性能索引补全
-- 对应 docs/database.md 第 6 节「索引优化」建议项
USE order_approval;

-- 1. 工单按审批流模板筛选（列表/统计常按 flow_id 过滤）
CREATE INDEX idx_flow_id ON work_order (flow_id);

-- 2. 工单状态筛选 + 时间排序（看板统计、工单列表高频）
--    (status, create_time) 复合索引同时覆盖 status 单列查询与 status+时间排序
--    原单列 idx_status 被该复合索引的左前缀覆盖，故移除避免冗余
DROP INDEX idx_status ON work_order;
CREATE INDEX idx_status_create ON work_order (status, create_time);

-- 3. 审批记录按工单+步骤+审批人联合查询（审批进度、重复审批校验）
--    原单列 idx_order_id 被该复合索引左前缀覆盖，故移除
DROP INDEX idx_order_id ON approval_record;
CREATE INDEX idx_order_step_approver ON approval_record (order_id, step_id, approver_id);

-- 4. 步骤审批人按步骤+用户联合查询（判断某人是否为某步审批人）
CREATE INDEX idx_step_user ON approval_step_approver (step_id, user_id);

-- 5. 工单审批时间范围查询（看板「今日已审批」趋势统计）
CREATE INDEX idx_approve_time ON work_order (approve_time);
