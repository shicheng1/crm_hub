package com.zmd.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmd.order.approval.engine.ApprovalEngineService;
import com.zmd.order.auth.LoginUser;
import com.zmd.order.cache.OrderCacheService;
import com.zmd.order.common.BusinessException;
import com.zmd.order.common.Constants;
import com.zmd.order.dto.ApprovalDTO;
import com.zmd.order.dto.ApprovalOutcome;
import com.zmd.order.dto.OrderCreateDTO;
import com.zmd.order.entity.*;
import com.zmd.order.event.OrderApprovalEvent;
import com.zmd.order.lock.RedisDistributedLock;
import com.zmd.order.mapper.ApprovalRecordMapper;
import com.zmd.order.mapper.OperationLogMapper;
import com.zmd.order.mapper.OrderMapper;
import com.zmd.order.mapper.UserMapper;
import com.zmd.order.service.OrderService;
import com.zmd.order.statemachine.OrderStatusTransition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工单服务实现（编排层）。
 *
 * <p>职责收敛为：工单 CRUD 编排 + 分布式锁 + 发布领域事件。
 * 审批流程的计算逻辑已下沉到 {@link ApprovalEngineService}，
 * 缓存失效与通知通过 {@link OrderApprovalEvent} 解耦到监听器，
 * 本类不再直接依赖 DashboardService、不再在锁内发通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ApprovalRecordMapper recordMapper;
    private final OperationLogMapper logMapper;
    private final RedisDistributedLock redisLock;
    private final OrderCacheService orderCacheService;
    private final ApprovalEngineService approvalEngine;
    private final ApplicationEventPublisher eventPublisher;

    /** 分页 size 上限，防止 size 过大触发全表扫描 + N+1 放大（P1-3 防御） */
    private static final int MAX_PAGE_SIZE = 100;

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
            // 工单一创建即进入审批流第一步：currentStep 直接置为 1（审批步骤 step_order 从 1 开始）。
            // 否则 currentStep=0 会让 selectTodoPage 的 s.step_order = w.current_step 匹配不到任何步骤，
            // 导致首审在待办列表不可见、详情页审批按钮不显示，形成“永远无法触发首次审批”的死锁。
            order.setCurrentStep(1);
            order.setSubmitStep(1);
            order.setCreatorId(loginUser.getUserId());
            order.setDeptId(creator != null ? creator.getDeptId() : null);
            order.setCreateTime(LocalDateTime.now());
            orderMapper.insert(order);
            approvalEngine.createFlowSnapshot(order.getId(), dto.getFlowId());

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
        String rejectMode = approvalEngine.getRejectMode(order);
        int restartStep;
        if ("ORIGIN".equals(rejectMode)) {
            restartStep = 1; // 退回发起人：从头开始
        } else if ("PREVIOUS".equals(rejectMode)) {
            restartStep = Math.max(1, order.getCurrentStep()); // 退回上一步：从当前步开始
        } else {
            restartStep = 1; // RESTART
        }

        OrderStatusTransition.assertCanTransit(order.getStatus(), Constants.STATUS_REVIEWING);
        order.setStatus(Constants.STATUS_REVIEWING);
        order.setCurrentStep(restartStep);
        orderMapper.updateById(order);

        saveLog(orderId, loginUser, "RESUBMIT", "重新提交，从步骤" + restartStep + "开始");
        orderCacheService.evictOrder(orderId);
        log.info("工单重新提交, orderId={}, restartStep={}", orderId, restartStep);
    }

    // ==================== 查询 ====================

    @Override
    public IPage<WorkOrder> pageOrders(int page, int size, Integer status, String title) {
        size = Math.min(size, MAX_PAGE_SIZE);
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
        // 先 enrich 再缓存：缓存的是已填充完整展示信息的对象（enriched=true），
        // 下次命中缓存直接返回，不再 re-enrich（修复 P1-1）
        orderCacheService.cacheOrder(enrichOrder(order));
        return order;
    }

    @Override
    public IPage<WorkOrder> todoList(int page, int size) {
        LoginUser loginUser = LoginUser.get();
        size = Math.min(size, MAX_PAGE_SIZE);
        // 一条 SQL 搞定：JOIN 步骤+审批人+审批记录，直接查出我的待办
        IPage<WorkOrder> result = orderMapper.selectTodoPage(new Page<>(page, size), loginUser.getUserId());
        // 批量填充展示信息（消灭逐条 enrich 的 N+1，P0-2）
        enrichOrders(result.getRecords());
        return result;
    }

    @Override
    public IPage<WorkOrder> doneList(int page, int size) {
        LoginUser loginUser = LoginUser.get();
        size = Math.min(size, MAX_PAGE_SIZE);
        IPage<WorkOrder> result = orderMapper.selectDonePage(new Page<>(page, size), loginUser.getUserId());
        enrichOrders(result.getRecords());
        return result;
    }

    // ==================== 多级审批（核心编排） ====================

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
                OrderStatusTransition.assertCanTransit(order.getStatus(), Constants.STATUS_REVIEWING);
                order.setStatus(Constants.STATUS_REVIEWING);
            }

            // 验证：当前步骤是否有此人审批权限
            ApprovalFlowStep currentStep = approvalEngine.getStep(order, order.getCurrentStep());
            if (currentStep == null) throw new BusinessException("审批流配置异常");
            if (!approvalEngine.isStepApprover(currentStep, loginUser.getUserId())) {
                throw new BusinessException("您不是当前步骤的审批人");
            }

            // 检查是否重复审批（同一步骤同一人只能审一次）
            if (approvalEngine.hasApproved(dto.getOrderId(), currentStep.getId(), loginUser.getUserId())) {
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

            // 计算审批结果（推进/驳回/跳过），引擎只算状态不写库
            ApprovalOutcome outcome = Boolean.TRUE.equals(dto.getApproved())
                    ? approvalEngine.approve(order, currentStep, loginUser)
                    : approvalEngine.reject(order, currentStep, loginUser, dto.getRemark());

            // 落库：状态流转 + 步骤 + 终态回填
            OrderStatusTransition.assertCanTransit(order.getStatus(), outcome.getTargetStatus());
            order.setStatus(outcome.getTargetStatus());
            if (outcome.getTargetStep() != null) {
                order.setCurrentStep(outcome.getTargetStep());
            }
            if (outcome.isMarkApprover()) {
                order.setApproveTime(LocalDateTime.now());
                order.setApproverId(loginUser.getUserId());
            }
            orderMapper.updateById(order);

            String logDetail = outcome.getLogDetail()
                    + (dto.getRemark() != null && !dto.getRemark().trim().isEmpty() ? "，备注：" + dto.getRemark() : "");
            saveLog(order.getId(), loginUser, outcome.getLogOp(), logDetail);

            // 发布领域事件：缓存失效 + 通知由监听器在事务提交后处理（不在锁内）
            eventPublisher.publishEvent(new OrderApprovalEvent(
                    this,
                    order.getId(),
                    order.getTitle(),
                    order.getCreatorId(),
                    creatorName(order.getCreatorId()),
                    loginUser.getUserId(),
                    loginUser.getUsername(),
                    Boolean.TRUE.equals(dto.getApproved()),
                    dto.getRemark(),
                    LocalDateTime.now()));

            log.info("工单审批处理完成, orderId={}, result={}, targetStatus={}",
                    order.getId(), outcome.getLogOp(), outcome.getTargetStatus());
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    // ==================== 内部工具方法 ====================

    /** 填充单个工单展示信息（审批流步骤、审批记录、创建人姓名） */
    private WorkOrder enrichOrder(WorkOrder order) {
        if (order.isEnriched()) return order; // 已填充（含缓存命中）直接返回，避免 re-enrich（P0-2/P1-1）
        if (order.getFlowId() != null) {
            ApprovalFlow snapshot = approvalEngine.getFlowSnapshot(order);
            List<ApprovalFlowStep> steps;
            if (snapshot != null) {
                steps = snapshot.getSteps() != null ? snapshot.getSteps() : new ArrayList<>();
                order.setFlowName(snapshot.getName());
                order.setRejectMode(snapshot.getRejectMode());
            } else {
                ApprovalFlow flow = approvalEngine.getRuntimeFlow(order.getFlowId());
                steps = approvalEngine.getRuntimeSteps(order.getFlowId());
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
        order.setCreatorName(creatorName(order.getCreatorId()));
        order.setEnriched(true);
        return order;
    }

    /** 批量填充工单展示信息，消灭逐条 enrich 的 N+1（P0-2） */
    private void enrichOrders(List<WorkOrder> orders) {
        if (orders == null || orders.isEmpty()) return;
        List<WorkOrder> pending = orders.stream().filter(o -> !o.isEnriched()).collect(Collectors.toList());
        if (pending.isEmpty()) return;

        List<Long> orderIds = pending.stream().map(WorkOrder::getId).collect(Collectors.toList());

        // 批量审批记录
        List<ApprovalRecord> allRecords = recordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .in(ApprovalRecord::getOrderId, orderIds)
                        .orderByAsc(ApprovalRecord::getStepOrder));
        Map<Long, List<ApprovalRecord>> recordsByOrder = allRecords.stream()
                .collect(Collectors.groupingBy(ApprovalRecord::getOrderId));

        // 批量创建人
        List<Long> creatorIds = pending.stream().map(WorkOrder::getCreatorId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> nameMap = creatorIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(creatorIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername));

        // 批量流程快照
        Map<Long, ApprovalFlow> snapshotMap = approvalEngine.getFlowSnapshots(orderIds);

        for (WorkOrder o : pending) {
            ApprovalFlow snapshot = snapshotMap.get(o.getId());
            if (snapshot != null) {
                o.setFlowName(snapshot.getName());
                o.setRejectMode(snapshot.getRejectMode());
                o.setFlowSteps(snapshot.getSteps() != null ? snapshot.getSteps() : new ArrayList<>());
            } else {
                ApprovalFlow flow = approvalEngine.getRuntimeFlow(o.getFlowId());
                if (flow != null) {
                    o.setFlowName(flow.getName());
                    o.setRejectMode(flow.getRejectMode());
                }
                o.setFlowSteps(approvalEngine.getRuntimeSteps(o.getFlowId()));
            }
            o.setRecords(recordsByOrder.getOrDefault(o.getId(), new ArrayList<>()));
            o.setCreatorName(nameMap.get(o.getCreatorId()));
            o.setEnriched(true);
        }
    }

    private String creatorName(Long creatorId) {
        User creator = userMapper.selectById(creatorId);
        return creator != null ? creator.getUsername() : "未知";
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
}
