-- ============================================================
-- 测试数据种子：新用户 + 复杂审批流（幂等，可重复执行）
-- 密码统一为 123456 (BCrypt $2a$10$...)
-- 用 INSERT ... SELECT ... WHERE NOT EXISTS 避免重复；用查询回填 ID 保持关联
-- ============================================================
SET @pw = '$2a$10$cfxEGCcJGZ3GSr4hyzDuZeEseb21AoIgYxVlEdvEjpChQGFfhyDkO';

-- ============ 创建人 (USER 角色) ============
INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'u_zhang',@pw,'USER',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='u_zhang');
SET @u_zhang = (SELECT id FROM sys_user WHERE username='u_zhang');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'u_li',@pw,'USER',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='u_li');
SET @u_li = (SELECT id FROM sys_user WHERE username='u_li');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'u_wang',@pw,'USER',2,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='u_wang');
SET @u_wang = (SELECT id FROM sys_user WHERE username='u_wang');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'u_zhao',@pw,'USER',2,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='u_zhao');
SET @u_zhao = (SELECT id FROM sys_user WHERE username='u_zhao');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'u_qian',@pw,'USER',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='u_qian');
SET @u_qian = (SELECT id FROM sys_user WHERE username='u_qian');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'u_sun',@pw,'USER',2,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='u_sun');
SET @u_sun = (SELECT id FROM sys_user WHERE username='u_sun');

-- ============ 审批人 (APPROVER 角色) ============
INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'a_techlead',@pw,'APPROVER',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='a_techlead');
SET @a_techlead = (SELECT id FROM sys_user WHERE username='a_techlead');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'a_tecmgr',@pw,'APPROVER',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='a_tecmgr');
SET @a_tecmgr = (SELECT id FROM sys_user WHERE username='a_tecmgr');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'a_bizlead',@pw,'APPROVER',2,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='a_bizlead');
SET @a_bizlead = (SELECT id FROM sys_user WHERE username='a_bizlead');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'a_bizmgr',@pw,'APPROVER',2,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='a_bizmgr');
SET @a_bizmgr = (SELECT id FROM sys_user WHERE username='a_bizmgr');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'a_fin',@pw,'APPROVER',3,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='a_fin');
SET @a_fin = (SELECT id FROM sys_user WHERE username='a_fin');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'a_vp',@pw,'APPROVER',3,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='a_vp');
SET @a_vp = (SELECT id FROM sys_user WHERE username='a_vp');

INSERT INTO sys_user (username,password,role,dept_id,status)
SELECT 'a_gm',@pw,'APPROVER',3,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='a_gm');
SET @a_gm = (SELECT id FROM sys_user WHERE username='a_gm');

-- ============ 流程 A：三级混合审批流-采购申请 (驳回=PREVIOUS) ============
INSERT INTO approval_flow (name,description,status,reject_mode)
SELECT '三级混合审批流-采购申请','技术会签 -> 业务或签 -> 管理层或签',1,'PREVIOUS' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow WHERE name='三级混合审批流-采购申请');
SET @fA = (SELECT id FROM approval_flow WHERE name='三级混合审批流-采购申请');

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fA,1,'技术主管会签','ALL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fA AND step_order=1);
SET @sA1 = (SELECT id FROM approval_flow_step WHERE flow_id=@fA AND step_order=1);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sA1,@a_techlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sA1 AND user_id=@a_techlead);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sA1,@a_tecmgr FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sA1 AND user_id=@a_tecmgr);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fA,2,'业务主管或签','ANY' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fA AND step_order=2);
SET @sA2 = (SELECT id FROM approval_flow_step WHERE flow_id=@fA AND step_order=2);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sA2,@a_bizlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sA2 AND user_id=@a_bizlead);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sA2,@a_bizmgr FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sA2 AND user_id=@a_bizmgr);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fA,3,'管理层或签','ANY' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fA AND step_order=3);
SET @sA3 = (SELECT id FROM approval_flow_step WHERE flow_id=@fA AND step_order=3);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sA3,@a_gm FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sA3 AND user_id=@a_gm);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sA3,@a_vp FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sA3 AND user_id=@a_vp);

-- ============ 流程 B：四级全会签流-大额合同 (驳回=RESTART) ============
INSERT INTO approval_flow (name,description,status,reject_mode)
SELECT '四级全会签流-大额合同','技术会签 -> 业务会签 -> 财务会签 -> 总经理',1,'RESTART' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow WHERE name='四级全会签流-大额合同');
SET @fB = (SELECT id FROM approval_flow WHERE name='四级全会签流-大额合同');

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fB,1,'技术会签','ALL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fB AND step_order=1);
SET @sB1 = (SELECT id FROM approval_flow_step WHERE flow_id=@fB AND step_order=1);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sB1,@a_techlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sB1 AND user_id=@a_techlead);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sB1,@a_tecmgr FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sB1 AND user_id=@a_tecmgr);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fB,2,'业务会签','ALL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fB AND step_order=2);
SET @sB2 = (SELECT id FROM approval_flow_step WHERE flow_id=@fB AND step_order=2);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sB2,@a_bizlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sB2 AND user_id=@a_bizlead);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sB2,@a_bizmgr FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sB2 AND user_id=@a_bizmgr);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fB,3,'财务会签','ALL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fB AND step_order=3);
SET @sB3 = (SELECT id FROM approval_flow_step WHERE flow_id=@fB AND step_order=3);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sB3,@a_fin FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sB3 AND user_id=@a_fin);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sB3,@a_vp FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sB3 AND user_id=@a_vp);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fB,4,'总经理审批','ANY' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fB AND step_order=4);
SET @sB4 = (SELECT id FROM approval_flow_step WHERE flow_id=@fB AND step_order=4);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sB4,@a_gm FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sB4 AND user_id=@a_gm);

-- ============ 流程 C：多级或签流-日常报销 (驳回=ORIGIN) ============
INSERT INTO approval_flow (name,description,status,reject_mode)
SELECT '多级或签流-日常报销','主管或签 -> 管理层或签',1,'ORIGIN' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow WHERE name='多级或签流-日常报销');
SET @fC = (SELECT id FROM approval_flow WHERE name='多级或签流-日常报销');

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fC,1,'主管或签','ANY' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fC AND step_order=1);
SET @sC1 = (SELECT id FROM approval_flow_step WHERE flow_id=@fC AND step_order=1);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sC1,@a_techlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sC1 AND user_id=@a_techlead);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sC1,@a_bizlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sC1 AND user_id=@a_bizlead);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fC,2,'管理层或签','ANY' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fC AND step_order=2);
SET @sC2 = (SELECT id FROM approval_flow_step WHERE flow_id=@fC AND step_order=2);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sC2,@a_gm FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sC2 AND user_id=@a_gm);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sC2,@a_vp FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sC2 AND user_id=@a_vp);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sC2,@a_fin FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sC2 AND user_id=@a_fin);

-- ============ 流程 D：双人会签+单人终审流 (驳回=PREVIOUS) ============
INSERT INTO approval_flow (name,description,status,reject_mode)
SELECT '双人会签+单人终审流','部门会签 -> 财务会签 -> 总经理',1,'PREVIOUS' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow WHERE name='双人会签+单人终审流');
SET @fD = (SELECT id FROM approval_flow WHERE name='双人会签+单人终审流');

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fD,1,'部门会签','ALL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fD AND step_order=1);
SET @sD1 = (SELECT id FROM approval_flow_step WHERE flow_id=@fD AND step_order=1);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sD1,@a_techlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sD1 AND user_id=@a_techlead);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sD1,@a_bizlead FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sD1 AND user_id=@a_bizlead);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fD,2,'财务会签','ALL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fD AND step_order=2);
SET @sD2 = (SELECT id FROM approval_flow_step WHERE flow_id=@fD AND step_order=2);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sD2,@a_fin FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sD2 AND user_id=@a_fin);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sD2,@a_vp FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sD2 AND user_id=@a_vp);

INSERT INTO approval_flow_step (flow_id,step_order,step_name,approve_mode)
SELECT @fD,3,'总经理审批','ANY' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM approval_flow_step WHERE flow_id=@fD AND step_order=3);
SET @sD3 = (SELECT id FROM approval_flow_step WHERE flow_id=@fD AND step_order=3);
INSERT INTO approval_step_approver (step_id,user_id)
SELECT @sD3,@a_gm FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM approval_step_approver WHERE step_id=@sD3 AND user_id=@a_gm);

SELECT 'SEED_DONE (idempotent)' AS result;
