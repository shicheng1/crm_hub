-- v5: 工单审批流快照
-- 创建工单时保存当时的审批流配置，后续修改审批流不会影响历史工单
USE order_approval;

CREATE TABLE IF NOT EXISTS order_flow_snapshot (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL COMMENT '工单ID',
    flow_id       BIGINT       NOT NULL COMMENT '原审批流ID',
    snapshot_json LONGTEXT     NOT NULL COMMENT '审批流快照JSON，包含流程、步骤、审批人、审批模式、驳回策略',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_id (order_id),
    INDEX idx_flow_id (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单审批流快照';
