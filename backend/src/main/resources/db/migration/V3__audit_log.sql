-- 审计日志表
USE order_approval;

CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    operator_id  BIGINT       COMMENT '操作人ID',
    operator_name VARCHAR(50) COMMENT '操作人用户名',
    action       VARCHAR(50)  NOT NULL COMMENT '操作类型: LOGIN/LOGOUT/CREATE/UPDATE/DELETE/RESET_PASSWORD/STATUS_CHANGE',
    target_type  VARCHAR(50)  COMMENT '目标类型: USER/ORDER/FLOW',
    target_id    BIGINT       COMMENT '目标ID',
    detail       VARCHAR(500) COMMENT '操作详情',
    ip           VARCHAR(50)  COMMENT '操作人IP',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operator (operator_id),
    INDEX idx_action (action),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';
