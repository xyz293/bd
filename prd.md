# 小AI · 租户与组织域 — 业务流程与数据表设计

> 范围：tenant / org / user / user_wechat_bind / user_org_role / invite_code 六张表支撑的全部业务流程。

---

## 一、表结构

### 1. tenant — 租户

```sql
CREATE TABLE tenant (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(128) NOT NULL COMMENT '企业/店铺名称',
  type          TINYINT NOT NULL COMMENT '1企业版 2个人版',
  industry      VARCHAR(32) NOT NULL COMMENT '主行业：JEWELRY/BEAUTY',
  asset_package_id BIGINT COMMENT '挂载的行业资产包',
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 2停用 3到期',
  expire_at     DATETIME COMMENT '订阅到期时间',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT DEFAULT 0
) COMMENT '租户';
```

### 2. org — 组织树（品牌→区域→门店）

```sql
CREATE TABLE org (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  parent_id     BIGINT NOT NULL DEFAULT 0 COMMENT '0=顶级',
  type          TINYINT NOT NULL COMMENT '1品牌 2区域 3门店',
  name          VARCHAR(128) NOT NULL,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT DEFAULT 0,
  KEY idx_tenant (tenant_id, type)
) COMMENT '组织树';
```

### 3. user — 用户（手机号=主体，微信=钥匙）

```sql
CREATE TABLE user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone         VARCHAR(20) NOT NULL,
  openid        VARCHAR(64) COMMENT '当前绑定的微信openid',
  nickname      VARCHAR(64),
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 2禁用',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT DEFAULT 0,
  UNIQUE KEY uk_phone (phone)
) COMMENT '用户';
```

### 4. user_wechat_bind — 微信绑定历史（换微信接管留痕）

```sql
CREATE TABLE user_wechat_bind (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT NOT NULL,
  openid        VARCHAR(64) NOT NULL,
  action        TINYINT COMMENT '1绑定 2解绑 3接管',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) COMMENT '微信绑定历史';
```

### 5. user_org_role — 角色授权（RBAC 核心）

```sql
CREATE TABLE user_org_role (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  org_id        BIGINT NOT NULL COMMENT '所属组织节点',
  role          VARCHAR(32) NOT NULL COMMENT 'HQ_ADMIN/REGION_ADMIN/VIEWER/OWNER/STAFF',
  data_scope    TINYINT COMMENT '数据范围：1全域 2本区域 3本店 4本人',
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1在职 2已移除',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_org (user_id, org_id, role),
  KEY idx_tenant_org (tenant_id, org_id)
) COMMENT '角色授权';
```

### 6. invite_code — 邀请码

```sql
CREATE TABLE invite_code (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  store_id      BIGINT NOT NULL COMMENT '入店的门店org_id',
  code          VARCHAR(32) NOT NULL,
  role          VARCHAR(32) NOT NULL DEFAULT 'STAFF',
  expire_at     DATETIME NOT NULL,
  used          TINYINT DEFAULT 0,
  created_by    BIGINT,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) COMMENT '邀请码：一次性+有效期';
```

---

## 二、业务流程

### 流程 1：租户开通

#### 1a. 企业版（人工开通，平台超管操作）

```
签约对公打款
   ↓
平台超管在超级后台「开通向导」：
   ↓
① INSERT tenant（type=企业版，industry 必选，挂 asset_package_id）
   ↓
② INSERT org 品牌节点（type=1, parent_id=0）
   ↓
③ 建总部超管账号：INSERT user + INSERT user_org_role（role=HQ_ADMIN, data_scope=1全域）
   ↓
④ 总部超管登录企业版管理端，搭建组织：
   INSERT org 区域节点（type=2，挂品牌下）
   INSERT org 门店节点（type=3，挂区域下）
   ↓
⑤ 建店长账号：INSERT user + user_org_role（role=OWNER, data_scope=3本店, org_id=门店）
   ↓
⑥ 店长生成邀请码 → 员工扫码入店（见流程2）
   ↓
⑦ 总部备弹药：传素材/配风格库/配内容包/下发首批任务
   ↓
30天陪跑 → 正式交付
```

**涉及表**：tenant、org、user、user_org_role

#### 1b. 个人版（纯自助，零人工）

```
小程序注册 → 选行业（一期珠宝）
   ↓
选档位付费（主账号+3 / +5）→ 微信虚拟支付
   ↓
支付成功回调（payment_order.status=1）
   ↓
【一个事务内自动完成】：
   INSERT tenant（type=个人版, industry=JEWELRY, 挂珠宝资产包）
   INSERT org（直接一个门店节点，type=3）
   INSERT user（老板，手机号授权获得）
   INSERT user_org_role（role=OWNER, data_scope=3本店）
   INSERT quota_account（品牌总池，入账额度）
   ↓
内置垂类资产即刻可用 → 老板可邀请员工开干
```

**涉及表**：tenant、org、user、user_org_role（+ 支付/额度域的表）

### 流程 2：员工邀请入店

```
【店长侧】
管理Tab → 邀请员工 → 可设有效期
   ↓
INSERT invite_code（store_id=本店org_id, role=STAFF, expire_at, used=0）
   ↓
生成二维码展示（码内容=invite_code.code）

【员工侧】
微信扫码打开小程序
   ↓
wx.login 静默拿 code → 后端换 openid
   ↓
按 openid 查 user：
   ├─ 已存在且在职 → 直接进首页（无需重复入店）
   └─ 不存在/未绑定 → 走入店流程 ↓
   ↓
手机号授权（getPhoneNumber）→ INSERT 或 UPDATE user（手机号=主体）
   ↓
校验邀请码：
   ├─ used=1 → "邀请码已被使用"
   ├─ 已过期 → "邀请码已过期，请联系店长重新生成"
   └─ 有效 → UPDATE invite_code SET used=1
   ↓
INSERT user_org_role（user_id, store_id, role=STAFF, data_scope=4本人）
   ↓
签发登录态（Redis session）→ 进首页
```

**涉及表**：invite_code、user、user_org_role

**边界情况**：
- 一个微信一期只绑一个门店：入店前校验该 user 已无 status=1 的 user_org_role，有则提示
- 同手机号换微信进来：按手机号找到已有 user → 走「接管」流程（见流程4）

### 流程 3：登录与身份识别（每次打开小程序）

```
wx.login 拿 code → 后端调微信接口换 openid
   ↓
SELECT user WHERE openid=?
   ↓
查 user_org_role WHERE user_id AND status=1：
   ├─ 有在职记录 → 签发 token（Redis: session:{token} → userId/tenantId/role/orgId）
   │              → 返回角色+门店信息 → 前端按 role 渲染（OWNER 多「管理」Tab）
   ├─ 记录存在但 status=2（被移除）→ 提示"你已被移出门店，请联系店长"
   └─ 无记录 → 跳邀请码入店页（流程2）
   ↓
租户状态校验：tenant.status=3（到期）→ 提示续费，功能只读
```

**涉及表**：user、user_org_role、tenant

### 流程 4：账号生命周期管理

| 场景 | 触发人 | 操作链路 | 涉及表 |
|---|---|---|---|
| **换微信接管** | 员工本人 | 新微信登录 → 手机号验证 → 找到已有 user → UPDATE user.openid → INSERT user_wechat_bind（action=3接管）→ 作品与记录保留 | user、user_wechat_bind |
| **离职移除** | 店长 | 管理Tab移除员工 → UPDATE user_org_role.status=2 → 该微信下次登录查不到在职记录，即刻失去访问；历史数据留存可审计 | user_org_role |
| **禁用账号** | 店长 | UPDATE user.status=2 → 登录直接拦截 | user |
| **角色调整** | 总部/店长 | 升店长：INSERT/UPDATE user_org_role（role=OWNER, data_scope=3）；建区域管理员（REGION_ADMIN, data_scope=2）；建查看型管理员（VIEWER, data_scope=1，只读） | user_org_role |
| **续费/到期** | 平台超管 | 对公登记续费 → UPDATE tenant.expire_at；到期定时任务 UPDATE tenant.status=3 → 登录拦截提示 | tenant |

---

## 三、权限判定规则（全系统通用）

所有接口的权限校验统一走一条规则：

```
当前用户 user_org_role.role（能做什么）
        ×
当前用户 user_org_role.data_scope（能看哪里的数据）
        ×
请求目标数据的 org_id 归属
```

| 角色 | data_scope | 能看 | 典型场景 |
|---|---|---|---|
| HQ_ADMIN 总部超管 | 1 全域 | 全品牌 | 管理端全功能 |
| REGION_ADMIN 区域管理员 | 2 本区域 | 区域下所有门店 | 区域看板+区域任务 |
| VIEWER 查看型管理员 | 1 全域（只读） | 全品牌数据 | 大老板看板，零配置权 |
| OWNER 店长 | 3 本店 | 本店全部+员工 | 管理 Tab |
| STAFF 员工 | 4 本人 | 自己的作品/额度/任务 | 创作与打卡 |

**实现**：登录态写入 ThreadLocal → MyBatis 拦截器按 data_scope 自动给查询拼 org 范围条件（全域不加、本区域拼区域子树、本店拼 store_id、本人拼 user_id）。

---

## 四、表与流程对应关系总览

| 表 | 流程1 开通 | 流程2 入店 | 流程3 登录 | 流程4 生命周期 |
|---|:---:|:---:|:---:|:---:|
| tenant | ★ 建租户/续费到期 | （校验租户状态） | （到期拦截） | ★ 续费/停用 |
| org | ★ 搭组织树 | （store_id 来源） | （返回门店信息） | （区域/门店调整） |
| user | ★ 建初始账号 | ★ 手机号绑定 | ★ openid 识别 | ★ 禁用/换微信 |
| user_wechat_bind | | | | ★ 接管留痕 |
| user_org_role | ★ 初始授权 | ★ 入店授权 | ★ 在职判定 | ★ 移除/角色调整 |
| invite_code | | ★ 生成与核销 | | |

---

**一句话总结**：租户与组织域 = 「开通建租户（绑行业挂资产包）→ 搭组织树 → 邀请码扫码入店 → 登录识别角色 → 换微信/离职/禁用生命周期管理」；六张表里 user_org_role 是权限心脏（role 定能力、data_scope 定范围），invite_code 是入店唯一入口，user_wechat_bind 保证换微信可追溯。
