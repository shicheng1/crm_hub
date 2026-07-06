package com.zmd.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmd.order.auth.LoginUser;
import com.zmd.order.cache.OrderCacheService;
import com.zmd.order.common.BusinessException;
import com.zmd.order.common.Constants;
import com.zmd.order.dto.ApprovalDTO;
import com.zmd.order.dto.OrderCreateDTO;
import com.zmd.order.entity.*;
import com.zmd.order.lock.RedisDistributedLock;
import com.zmd.order.mapper.*;
import com.zmd.order.mq.ApprovalMessage;
import com.zmd.order.mq.ApprovalProducer;
import com.zmd.order.service.DashboardService;
import com.zmd.order.service.OrderService;
import com.zmd.order.statemachine.OrderStatusTransition;
import com.zmd.order.websocket.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工单服务实现
 *
 * 核心亮点（面试重点讲）：
 * 1. 多级审批流：可配置的审批步骤 + 每步骤多人审批（任一通过即可推进）
 * 2. 相同审批人跳过：连续步骤审批人相同时自动跳过
 * 3. 驳回策略：RESTART(回到第一步) / PREVIOUS(回到上一步) / ORIGIN(退回发起人)
 * 4. 分布式锁防并发审批
 * 5. Redis 缓存 + 操作日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ApprovalFlowStepMapper stepMapper;
    private final ApprovalStepApproverMapper stepApproverMapper;
    private final ApprovalRecordMapper recordMapper;
    private final OperationLogMapper logMapper;
    private final ApprovalFlowMapper flowMapper;
    private final OrderFlowSnapshotMapper flowSnapshotMapper;
    private final RedisDistributedLock redisLock;
    private final OrderCacheService orderCacheService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ApprovalProducer> approvalProducerProvider;
    private final NotificationService notificationService;
    private final DashboardService dashboardService;

    // ==================== 创建工单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateDTO dto) {
        LoginUser loginUser = LoginUser.get();
        String lockKey = Constants.LOCK_ORDER_CREATE + loginUser.getUserId();

        boolean locked = redisLock.tryLock(lockKey, 5, 30);
        if (!locked) throw new BusinessException(429, "操作过于频繁，请稍后再试");

        try {
            User creator = userMapper.selectById(loginUser.getUserId());
            WorkOrder order = new WorkOrder();
            order.setTitle(dto.getTitle());
            order.setContent(dto.getContent());
            order.setFlowId(dto.getFlowId());
            order.setStatus(Constants.STATUS_PENDING);
            order.setCurrentStep(0);
            order.setSubmitStep(1); // 默认从步骤1开始
            order.setCreatorId(loginUser.getUserId());
            order.setDeptId(creator != null ? creator.getDeptId() : null);
            order.setCreateTime(LocalDateTime.now());
            orderMapper.insert(order);
            createFlowSnapshot(order.getId(), dto.getFlowId());

            saveLog(order.getId(), loginUser, "CREATE", "创建工单");
            log.info("创建工单, orderId={}, flowId={}", order.getId(), dto.getFlowId());
            return order.getId();
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    // ==================== 重新提交（被退回后） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitOrder(Long orderId) {
        LoginUser loginUser = LoginUser.get();
        WorkOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("工单不存在");
        if (!order.getCreatorId().equals(loginUser.getUserId())) throw new BusinessException("只有创建人可以重新提交");
        if (order.getStatus() != Constants.STATUS_RETURNED) throw new BusinessException("该工单不在退回状态");

        // 根据驳回模式决定从哪一步重新开始
        String rejectMode = getRejectMode(order);
        int restartStep;
        if ("ORIGIN".equals(rejectMode)) {
            restartStep = 1; // 退回发起人：从头开始
        } else if ("PREVIOUS".equals(rejectMode)) {
            restartStep = Math.max(1, order.getCurrentStep()); // 退回上一步：从当前步开始
        } else {
            restartStep = 1; // RESTART
        }

        transitStatus(order, Constants.STATUS_REVIEWING);
        order.setCurrentStep(restartStep);
        orderMapper.updateById(order);

        saveLog(orderId, loginUser, "RESUBMIT", "重新提交，从步骤" + restartStep + "开始");
        orderCacheService.evictOrder(orderId);
        log.info("工单重新提交, orderId={}, restartStep={}", orderId, restartStep);
    }

    // ==================== 查询 ====================

    @Override
    public IPage<WorkOrder> pageOrders(int page, int size, Integer status, String title) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(WorkOrder::getStatus, status);
        if (title != null && !title.trim().isEmpty()) {
            wrapper.like(WorkOrder::getTitle, title.trim());
        }
        wrapper.orderByDesc(WorkOrder::getCreateTime);
        IPage<WorkOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        // 批量填充创建人姓名（避免 N+1）
        if (!result.getRecords().isEmpty()) {
            List<Long> creatorIds = result.getRecords().stream()
                    .map(WorkOrder::getCreatorId).distinct().collect(Collectors.toList());
            Map<Long, String> nameMap = userMapper.selectBatchIds(creatorIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername));
            result.getRecords().forEach(o -> o.setCreatorName(nameMap.get(o.getCreatorId())));
        }
        return result;
    }

    @Override
    public WorkOrder getOrderDetail(Long orderId) {
        // 1. 查缓存
        WorkOrder cached = orderCacheService.getCachedOrder(orderId);
        if (cached != null) return enrichOrder(cached);

        // 2. 命中空值缓存（防穿透），直接返回不存在
        if (orderCacheService.isNullCached(orderId)) {
            throw new BusinessException("工单不存在");
        }

        // 3. 查数据库
        WorkOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            orderCacheService.cacheNull(orderId); // 缓存空值防穿透
            throw new BusinessException("工单不存在");
        }
        orderCacheService.cacheOrder(order);
        return enrichOrder(order);
    }

    @Override
    public IPage<WorkOrder> todoList(int page, int size) {
        LoginUser loginUser = LoginUser.get();
        // 一条 SQL 搞定：JOIN 步骤+审批人+审批记录，直接查出我的待办
        IPage<WorkOrder> result = orderMapper.selectTodoPage(new Page<>(page, size), loginUser.getUserId());
        // 补充展示信息（创建人名等）
        result.getRecords().forEach(this::enrichOrder);
        return result;
    }

    @Override
    public IPage<WorkOrder> doneList(int page, int size) {
        LoginUser loginUser = LoginUser.get();
        IPage<WorkOrder> result = orderMapper.selectDonePage(new Page<>(page, size), loginUser.getUserId());
        result.getRecords().forEach(this::enrichOrder);
        return result;
    }

    // ==================== 多级审批（核心） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveOrder(ApprovalDTO dto) {
        LoginUser loginUser = LoginUser.get();
        String lockKey = Constants.LOCK_ORDER_APPROVE + dto.getOrderId();

        boolean locked = redisLock.tryLock(lockKey, 5, 60);
        if (!locked) throw new BusinessException(429, "该工单正在审批中，请稍后再试");

        try {
            WorkOrder order = orderMapper.selectById(dto.getOrderId());
            if (order == null) throw new BusinessException("工单不存在");
            if (order.getStatus() != Constants.STATUS_PENDING && order.getStatus() != Constants.STATUS_REVIEWING) {
                throw new BusinessException("工单状态不允许审批");
            }

            // 自动进入第一步（如果还没开始审批流）
            if (order.getCurrentStep() == null || order.getCurrentStep() == 0) {
                order.setCurrentStep(1);
                order.setSubmitStep(1);
                transitStatus(order, Constants.STATUS_REVIEWING);
            }

            // 验证：当前步骤是否有此人审批权限
            ApprovalFlowStep currentStep = getStep(order, order.getCurrentStep());
            if (currentStep == null) throw new BusinessException("审批流配置异常");
            if (!isStepApprover(currentStep, loginUser.getUserId())) {
                throw new BusinessException("您不是当前步骤的审批人");
            }

            // 检查是否重复审批（同一步骤同一人只能审一次）
            if (hasApproved(dto.getOrderId(), currentStep.getId(), loginUser.getUserId())) {
                throw new BusinessException("您已经审批过该步骤");
            }

            // 写审批记录
            ApprovalRecord record = new ApprovalRecord();
            record.setOrderId(order.getId());
            record.setStepId(currentStep.getId());
            record.setStepOrder(currentStep.getStepOrder());
            record.setApproverId(loginUser.getUserId());
            record.setResult(Boolean.TRUE.equals(dto.getApproved()) ? "APPROVED" : "REJECTED");
            record.setRemark(dto.getRemark());
            record.setOperateTime(LocalDateTime.now());
            recordMapper.insert(record);

            if (Boolean.TRUE.equals(dto.getApproved())) {
                handleApproved(order, currentStep, loginUser);
            } else {
                handleRejected(order, currentStep, loginUser, dto.getRemark());
            }
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    // ==================== 审批通过逻辑 ====================

    private void handleApproved(WorkOrder order, ApprovalFlowStep currentStep, LoginUser loginUser) {
        String approveMode = currentStep.getApproveMode() != null ? currentStep.getApproveMode() : "ANY";
        if ("ALL".equalsIgnoreCase(approveMode) && !isAllApproversApproved(order.getId(), currentStep)) {
            transitStatus(order, Constants.STATUS_REVIEWING);
            order.setCurrentStep(currentStep.getStepOrder());
            orderMapper.updateById(order);
            saveLog(order.getId(), loginUser, "APPROVE",
                    "通过 [" + currentStep.getStepName() + "]，会签模式，等待其他审批人处理");
            orderCacheService.evictOrder(order.getId());
            dashboardService.evictStatsCache();
            log.info("会签步骤部分通过, orderId={}, stepId={}, approverId={}", order.getId(), currentStep.getId(), loginUser.getUserId());
            return;
        }

        // 查找下一步
        ApprovalFlowStep nextStep = getNextStep(order, currentStep.getStepOrder());

        if (nextStep == null) {
            // === 最后一步通过 → 工单完成 ===
            transitStatus(order, Constants.STATUS_APPROVED);
            order.setApproveTime(LocalDateTime.now());
            order.setApproverId(loginUser.getUserId());
            orderMapper.updateById(order);
            saveLog(order.getId(), loginUser, "APPROVE", "通过 [" + currentStep.getStepName() + "]，审批全部完成");
            log.info("工单审批完成, orderId={}", order.getId());
        } else {
            // === 跳过逻辑：如果下一步审批人和当前审批人相同，自动跳过 ===
            nextStep = skipDuplicateApprovers(order, nextStep.getStepOrder(), loginUser.getUserId());

            if (nextStep == null) {
                // 跳过所有剩余步骤后，审批完成
                transitStatus(order, Constants.STATUS_APPROVED);
                order.setApproveTime(LocalDateTime.now());
                order.setApproverId(loginUser.getUserId());
                orderMapper.updateById(order);
                saveLog(order.getId(), loginUser, "APPROVE", "通过 [" + currentStep.getStepName() + "]，后续步骤审批人相同自动跳过，审批完成");
            } else {
                // 流转到下一步
                order.setCurrentStep(nextStep.getStepOrder());
                transitStatus(order, Constants.STATUS_REVIEWING);
                orderMapper.updateById(order);
                saveLog(order.getId(), loginUser, "APPROVE",
                        "通过 [" + currentStep.getStepName() + "]，流转至 [" + nextStep.getStepName() + "]");
                log.info("工单流转, orderId={}, {} -> {}", order.getId(), currentStep.getStepName(), nextStep.getStepName());
            }
        }

        orderCacheService.evictOrder(order.getId());
        dashboardService.evictStatsCache();
        sendNotifications(order, loginUser, true, null);
    }

    // ==================== 驳回逻辑（三种策略） ====================

    private void handleRejected(WorkOrder order, ApprovalFlowStep currentStep, LoginUser loginUser, String remark) {
        String rejectMode = getRejectMode(order);
        String logDetail;

        switch (rejectMode) {
            case "RESTART":
                // 回到第一步重新走
                transitStatus(order, Constants.STATUS_REVIEWING);
                order.setCurrentStep(1);
                logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=RESTART，回到第一步重新审批";
                break;

            case "PREVIOUS":
                // 回到上一步
                ApprovalFlowStep prevStep = getPrevStep(order, currentStep.getStepOrder());
                if (prevStep != null) {
                    order.setCurrentStep(prevStep.getStepOrder());
                    transitStatus(order, Constants.STATUS_REVIEWING);
                    logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=PREVIOUS，退回 [" + prevStep.getStepName() + "]";
                } else {
                    // 没有上一步了，退回到发起人
                    transitStatus(order, Constants.STATUS_RETURNED);
                    order.setCurrentStep(0);
                    logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=PREVIOUS，无上一步，退回发起人";
                }
                break;

            case "ORIGIN":
            default:
                // 退回发起人重新提交
                transitStatus(order, Constants.STATUS_RETURNED);
                order.setCurrentStep(0);
                logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=ORIGIN，退回发起人重新提交";
                break;
        }

        order.setApproveTime(LocalDateTime.now());
        order.setApproverId(loginUser.getUserId());
        orderMapper.updateById(order);

        saveLog(order.getId(), loginUser, "REJECT", logDetail + (remark != null ? "，备注：" + remark : ""));
        orderCacheService.evictOrder(order.getId());
        dashboardService.evictStatsCache();
        sendNotifications(order, loginUser, false, remark);

        log.info("工单驳回, orderId={}, rejectMode={}, currentStep={}", order.getId(), rejectMode, currentStep.getStepName());
    }

    // ==================== 内部工具方法 ====================

    /** 创建工单时保存审批流快照，防止流程后续修改影响历史工单 */
    private void createFlowSnapshot(Long orderId, Long flowId) {
        ApprovalFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BusinessException("审批流不存在");
        }
        flow.setSteps(loadRuntimeStepsWithApprovers(flowId));
        OrderFlowSnapshot snapshot = new OrderFlowSnapshot();
        snapshot.setOrderId(orderId);
        snapshot.setFlowId(flowId);
        try {
            snapshot.setSnapshotJson(objectMapper.writeValueAsString(flow));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "审批流快照创建失败");
        }
        snapshot.setCreateTime(LocalDateTime.now());
        flowSnapshotMapper.insert(snapshot);
    }

    private ApprovalFlow getFlowSnapshot(WorkOrder order) {
        OrderFlowSnapshot snapshot = flowSnapshotMapper.selectOne(
                new LambdaQueryWrapper<OrderFlowSnapshot>()
                        .eq(OrderFlowSnapshot::getOrderId, order.getId()));
        if (snapshot == null || snapshot.getSnapshotJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(), ApprovalFlow.class);
        } catch (Exception e) {
            log.warn("审批流快照反序列化失败, orderId={}", order.getId(), e);
            return null;
        }
    }

    private List<ApprovalFlowStep> getSnapshotSteps(WorkOrder order) {
        ApprovalFlow snapshot = getFlowSnapshot(order);
        return snapshot != null && snapshot.getSteps() != null ? snapshot.getSteps() : null;
    }

    private List<ApprovalFlowStep> loadRuntimeStepsWithApprovers(Long flowId) {
        List<ApprovalFlowStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, flowId)
                        .orderByAsc(ApprovalFlowStep::getStepOrder));
        if (!steps.isEmpty()) {
            List<Long> stepIds = steps.stream().map(ApprovalFlowStep::getId).collect(Collectors.toList());
            List<ApprovalStepApprover> allApprovers = stepApproverMapper.selectList(
                    new LambdaQueryWrapper<ApprovalStepApprover>()
                            .in(ApprovalStepApprover::getStepId, stepIds));
            Map<Long, List<ApprovalStepApprover>> approverMap = allApprovers.stream()
                    .collect(Collectors.groupingBy(ApprovalStepApprover::getStepId));
            for (ApprovalFlowStep step : steps) {
                step.setApprovers(approverMap.getOrDefault(step.getId(), new ArrayList<>()));
            }
        }
        return steps;
    }

    /** 判断某人是否是当前步骤的审批人（兼容快照和运行时配置） */
    private boolean isStepApprover(ApprovalFlowStep step, Long userId) {
        if (step.getApprovers() != null) {
            return step.getApprovers().stream().anyMatch(a -> userId.equals(a.getUserId()));
        }
        return stepApproverMapper.selectCount(
                new LambdaQueryWrapper<ApprovalStepApprover>()
                        .eq(ApprovalStepApprover::getStepId, step.getId())
                        .eq(ApprovalStepApprover::getUserId, userId)) > 0;
    }

    /** 判断某人是否已审批过该步骤 */
    private boolean hasApproved(Long orderId, Long stepId, Long userId) {
        return recordMapper.selectCount(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getOrderId, orderId)
                        .eq(ApprovalRecord::getStepId, stepId)
                        .eq(ApprovalRecord::getApproverId, userId)) > 0;
    }

    /** 会签模式：判断当前步骤所有审批人是否都已通过 */
    private boolean isAllApproversApproved(Long orderId, ApprovalFlowStep step) {
        long approverCount;
        if (step.getApprovers() != null) {
            approverCount = step.getApprovers().size();
        } else {
            approverCount = stepApproverMapper.selectCount(
                    new LambdaQueryWrapper<ApprovalStepApprover>()
                            .eq(ApprovalStepApprover::getStepId, step.getId()));
        }
        if (approverCount == 0) {
            return false;
        }
        long approvedCount = recordMapper.selectCount(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getOrderId, orderId)
                        .eq(ApprovalRecord::getStepId, step.getId())
                        .eq(ApprovalRecord::getResult, "APPROVED"));
        return approvedCount >= approverCount;
    }

    /** 判断是否轮到此人审批（待办列表用） */
    private boolean isMyTurn(WorkOrder order, Long userId) {
        if (order.getCurrentStep() == null || order.getCurrentStep() == 0) return false;
        ApprovalFlowStep step = getStep(order, order.getCurrentStep());
        if (step == null) return false;
        return isStepApprover(step, userId) && !hasApproved(order.getId(), step.getId(), userId);
    }

    /** 跳过审批人相同的连续步骤 */
    private ApprovalFlowStep skipDuplicateApprovers(WorkOrder order, int fromStepOrder, Long approverId) {
        ApprovalFlowStep step = getStep(order, fromStepOrder);
        while (step != null && isStepApprover(step, approverId) && !hasApproved(order.getId(), step.getId(), approverId)) {
            log.info("跳过审批人相同的步骤: orderId={}, flowId={}, stepOrder={}, approverId={}", order.getId(), order.getFlowId(), step.getStepOrder(), approverId);
            step = getNextStep(order, step.getStepOrder());
        }
        return step;
    }

    private ApprovalFlowStep getStep(WorkOrder order, int stepOrder) {
        List<ApprovalFlowStep> snapshotSteps = getSnapshotSteps(order);
        if (snapshotSteps != null) {
            return snapshotSteps.stream()
                    .filter(s -> s.getStepOrder() != null && s.getStepOrder() == stepOrder)
                    .findFirst()
                    .orElse(null);
        }
        return stepMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, order.getFlowId())
                        .eq(ApprovalFlowStep::getStepOrder, stepOrder));
    }

    private ApprovalFlowStep getNextStep(WorkOrder order, int currentStepOrder) {
        List<ApprovalFlowStep> snapshotSteps = getSnapshotSteps(order);
        if (snapshotSteps != null) {
            return snapshotSteps.stream()
                    .filter(s -> s.getStepOrder() != null && s.getStepOrder() > currentStepOrder)
                    .min(java.util.Comparator.comparing(ApprovalFlowStep::getStepOrder))
                    .orElse(null);
        }
        return stepMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, order.getFlowId())
                        .gt(ApprovalFlowStep::getStepOrder, currentStepOrder)
                        .orderByAsc(ApprovalFlowStep::getStepOrder)
                        .last("LIMIT 1"));
    }

    private ApprovalFlowStep getPrevStep(WorkOrder order, int currentStepOrder) {
        List<ApprovalFlowStep> snapshotSteps = getSnapshotSteps(order);
        if (snapshotSteps != null) {
            return snapshotSteps.stream()
                    .filter(s -> s.getStepOrder() != null && s.getStepOrder() < currentStepOrder)
                    .max(java.util.Comparator.comparing(ApprovalFlowStep::getStepOrder))
                    .orElse(null);
        }
        return stepMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, order.getFlowId())
                        .lt(ApprovalFlowStep::getStepOrder, currentStepOrder)
                        .orderByDesc(ApprovalFlowStep::getStepOrder)
                        .last("LIMIT 1"));
    }

    private String getRejectMode(WorkOrder order) {
        ApprovalFlow snapshot = getFlowSnapshot(order);
        if (snapshot != null && snapshot.getRejectMode() != null) {
            return snapshot.getRejectMode();
        }
        ApprovalFlow flow = flowMapper.selectById(order.getFlowId());
        return flow != null && flow.getRejectMode() != null ? flow.getRejectMode() : "ORIGIN";
    }

    private WorkOrder enrichOrder(WorkOrder order) {
        if (order.getFlowId() != null) {
            ApprovalFlow snapshot = getFlowSnapshot(order);
            List<ApprovalFlowStep> steps;
            if (snapshot != null) {
                steps = snapshot.getSteps() != null ? snapshot.getSteps() : new ArrayList<>();
                order.setFlowName(snapshot.getName());
                order.setRejectMode(snapshot.getRejectMode());
            } else {
                steps = loadRuntimeStepsWithApprovers(order.getFlowId());
                ApprovalFlow flow = flowMapper.selectById(order.getFlowId());
                if (flow != null) {
                    order.setFlowName(flow.getName());
                    order.setRejectMode(flow.getRejectMode());
                }
            }
            order.setFlowSteps(steps);
        }
        order.setRecords(recordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getOrderId, order.getId())
                        .orderByAsc(ApprovalRecord::getStepOrder)));
        User creator = userMapper.selectById(order.getCreatorId());
        order.setCreatorName(creator != null ? creator.getUsername() : "未知");
        return order;
    }

    private void transitStatus(WorkOrder order, int targetStatus) {
        OrderStatusTransition.assertCanTransit(order.getStatus(), targetStatus);
        order.setStatus(targetStatus);
    }

    private void saveLog(Long orderId, LoginUser loginUser, String operation, String detail) {
        OrderOperationLog opLog = new OrderOperationLog();
        opLog.setOrderId(orderId);
        opLog.setOperatorId(loginUser.getUserId());
        opLog.setOperatorName(loginUser.getUsername());
        opLog.setOperation(operation);
        opLog.setDetail(detail);
        opLog.setOperateTime(LocalDateTime.now());
        logMapper.insert(opLog);
    }

    private void sendNotifications(WorkOrder order, LoginUser loginUser, boolean approved, String remark) {
        User approver = userMapper.selectById(loginUser.getUserId());
        User creator = userMapper.selectById(order.getCreatorId());
        ApprovalMessage message = new ApprovalMessage(
                order.getId(), order.getTitle(),
                order.getCreatorId(), creator != null ? creator.getUsername() : "未知",
                loginUser.getUserId(), approver != null ? approver.getUsername() : "未知",
                approved ? "APPROVED" : "REJECTED", remark, LocalDateTime.now());

        ApprovalProducer producer = approvalProducerProvider.getIfAvailable();
        if (producer != null) producer.sendApprovalNotify(message);
        notificationService.notifyCreator(message);
    }
}
