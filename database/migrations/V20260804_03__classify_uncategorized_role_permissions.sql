-- 将角色管理中原先显示为“未归类权限”的权限点挂到对应业务菜单。
-- 仅补充菜单权限树关系，不修改角色已有授权数据。

INSERT INTO `sys_menus`
(`id`, `parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`)
VALUES
(200, 4, '新建多维表格', 'BUTTON', NULL, NULL, NULL, NULL, 1, 'button:bitable:create', 1, 1, 0),
(201, 4, '编辑多维表格', 'BUTTON', NULL, NULL, NULL, NULL, 2, 'button:bitable:update', 1, 1, 0),
(202, 4, '删除多维表格', 'BUTTON', NULL, NULL, NULL, NULL, 3, 'button:bitable:delete', 1, 1, 0),
(203, 3, '查看迭代/燃尽图', 'BUTTON', NULL, NULL, NULL, NULL, 4, 'button:iteration:view', 1, 1, 0),
(204, 7, '通知管理', 'BUTTON', NULL, NULL, NULL, NULL, 9, 'button:notification:manage', 1, 1, 0),
(205, 10, '导入项目', 'BUTTON', NULL, NULL, NULL, NULL, 5, 'button:project:import', 1, 1, 0),
(206, 10, '导出项目', 'BUTTON', NULL, NULL, NULL, NULL, 6, 'button:project:export', 1, 1, 0),
(207, 8, 'RAG文档上传', 'BUTTON', NULL, NULL, NULL, NULL, 8, 'button:rag:upload', 1, 1, 0),
(208, 8, 'RAG文档搜索', 'BUTTON', NULL, NULL, NULL, NULL, 9, 'button:rag:search', 1, 1, 0),
(209, 2, '创建需求关联', 'BUTTON', NULL, NULL, NULL, NULL, 16, 'button:relation:create', 1, 1, 0),
(210, 2, '删除需求关联', 'BUTTON', NULL, NULL, NULL, NULL, 17, 'button:relation:delete', 1, 1, 0),
(211, 2, '会签通过', 'BUTTON', NULL, NULL, NULL, NULL, 18, 'button:requirement:countersign-approve', 1, 1, 0),
(212, 2, '会签驳回', 'BUTTON', NULL, NULL, NULL, NULL, 19, 'button:requirement:countersign-reject', 1, 1, 0),
(213, 2, '保存需求草稿', 'BUTTON', NULL, NULL, NULL, NULL, 20, 'button:requirement:draft', 1, 1, 0),
(214, 2, '发起评审', 'BUTTON', NULL, NULL, NULL, NULL, 21, 'button:review:create', 1, 1, 0),
(215, 2, '编辑评审', 'BUTTON', NULL, NULL, NULL, NULL, 22, 'button:review:update', 1, 1, 0),
(216, 2, '提交评审', 'BUTTON', NULL, NULL, NULL, NULL, 23, 'button:review:submit', 1, 1, 0),
(217, 2, '查看评审详情', 'BUTTON', NULL, NULL, NULL, NULL, 24, 'button:review:view', 1, 1, 0),
(218, 17, '批量导入角色', 'BUTTON', NULL, NULL, NULL, NULL, 5, 'button:role:import', 1, 1, 0),
(219, 17, '导出角色', 'BUTTON', NULL, NULL, NULL, NULL, 6, 'button:role:export', 1, 1, 0),
(220, 11, '批量启停/删除用户', 'BUTTON', NULL, NULL, NULL, NULL, 8, 'button:user:batch-delete', 1, 1, 0),
(221, 11, '邀请成员', 'BUTTON', NULL, NULL, NULL, NULL, 9, 'button:user:invite', 1, 1, 0),
(222, 11, '导出花名册', 'BUTTON', NULL, NULL, NULL, NULL, 10, 'button:user:export', 1, 1, 0),
(223, 11, '导入花名册', 'BUTTON', NULL, NULL, NULL, NULL, 11, 'button:user:import', 1, 1, 0),
(224, 14, '工作流配置', 'BUTTON', NULL, NULL, NULL, NULL, 8, 'button:workflow:config', 1, 1, 0)
ON DUPLICATE KEY UPDATE
    `parent_id` = VALUES(`parent_id`),
    `name` = VALUES(`name`),
    `menu_type` = VALUES(`menu_type`),
    `sort_order` = VALUES(`sort_order`),
    `permission_code` = VALUES(`permission_code`),
    `visible` = VALUES(`visible`),
    `enabled` = VALUES(`enabled`),
    `keep_alive` = VALUES(`keep_alive`);
