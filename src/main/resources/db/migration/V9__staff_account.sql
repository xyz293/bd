-- 三级额度账户：租户池(TENANT) → 门店(STORE) → 员工(STAFF)。
-- STAFF 级 owner_id = user_org_role.id（成员关系 ID，一人多店各有账户）。
ALTER TABLE quota_account MODIFY COLUMN level VARCHAR(10) NOT NULL COMMENT 'TENANT/STORE/STAFF';
ALTER TABLE quota_account ADD UNIQUE INDEX uk_quota_account_owner (tenant_id, level, owner_id);
