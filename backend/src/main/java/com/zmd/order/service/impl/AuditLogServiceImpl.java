package com.zmd.order.service.impl;

import com.zmd.order.entity.AuditLog;
import com.zmd.order.mapper.AuditLogMapper;
import com.zmd.order.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    @Async
    public void log(Long operatorId, String operatorName, String action,
                    String targetType, Long targetId, String detail, String ip) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setOperatorId(operatorId);
            auditLog.setOperatorName(operatorName);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setDetail(detail);
            auditLog.setIp(ip);
            auditLog.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
            log.debug("审计日志: action={}, operator={}, target={}:{}", action, operatorName, targetType, targetId);
        } catch (Exception e) {
            log.error("审计日志写入失败: action={}, operator={}", action, operatorName, e);
        }
    }
}
