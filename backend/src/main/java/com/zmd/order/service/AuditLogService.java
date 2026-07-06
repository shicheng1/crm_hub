package com.zmd.order.service;

import com.zmd.order.entity.AuditLog;

/**
 * 审计日志服务
 *
 * 记录关键操作：登录、登出、用户管理、审批流变更
 */
public interface AuditLogService {

    /**
     * 记录审计日志
     *
     * @param operatorId   操作人ID（登录/登出时可为null）
     * @param operatorName 操作人用户名
     * @param action       操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/RESET_PASSWORD/STATUS_CHANGE
     * @param targetType   目标类型：USER/ORDER/FLOW
     * @param targetId     目标ID
     * @param detail       操作详情
     * @param ip           操作人IP
     */
    void log(Long operatorId, String operatorName, String action,
             String targetType, Long targetId, String detail, String ip);
}
