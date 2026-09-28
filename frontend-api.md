# 小AI · 前端接口文档（全量）

> 面向前端的全量接口与业务逻辑说明，基于当前后端代码整理（29 个 Controller、89 个接口，18 个模块）。
> 更新时间：2026-09-27

---

## 目录

1. [基础约定](#1-基础约定)
2. [角色与数据权限](#2-角色与数据权限)
3. [枚举字典](#3-枚举字典)
4. [接口明细](#4-接口明细)
   - 4.1 [健康检查](#41-健康检查)
   - 4.2 [认证与账号](#42-认证与账号)
   - 4.3 [租户与组织](#43-租户与组织)
   - 4.4 [管理端总览 / 门店 / 成员](#44-管理端总览--门店--成员)
   - 4.5 [素材中心](#45-素材中心)
   - 4.6 [内容包](#46-内容包)
   - 4.7 [风格选项](#47-风格选项)
   - 4.8 [合规配置](#48-合规配置)
   - 4.9 [作品审核](#49-作品审核)
   - 4.10 [导出任务](#410-导出任务)
   - 4.11 [提示词模板](#411-提示词模板)
   - 4.12 [AI 媒体任务查询](#412-ai-媒体任务查询)
   - 4.13 [作品与 AI 生成](#413-作品与-ai-生成)
   - 4.14 [对话创作](#414-对话创作)
   - 4.15 [任务管理与发布核销](#415-任务管理与发布核销)
   - 4.16 [任务统计与激励](#416-任务统计与激励)
   - 4.17 [额度](#417-额度)
   - 4.18 [平台端](#418-平台端)
5. [典型业务流程（前端调用时序）](#5-典型业务流程前端调用时序)
6. [前端注意事项](#6-前端注意事项)

---

## 1. 基础约定

### 1.1 服务地址

| 环境 | BaseURL |
|---|---|
| 本地开发 | `http://localhost:8080`（`SERVER_PORT` 可配置） |

### 1.2 认证方式

- 除「免登录接口」外，所有请求需携带请求头：`Authorization: Bearer {token}`
- **免登录接口**：`POST /api/auth/login`、`POST /api/auth/join`、`POST /api/auth/takeover`、`GET /api/invites/{code}`、`POST /api/tenants/open`、`POST /platform/auth/login`、`GET /api/health`
- token 为 Redis 会话，服务端重启或 `POST /api/auth/logout` 后失效；过期/无效返回 `code=2001`
- 登录后租户信息由会话携带，前端**无需**额外传 `X-Tenant-Id` 请求头

### 1.3 跨域（CORS）

后端已配置全局 CORS，开发环境默认放行以下来源（允许携带凭证）：

```
http://localhost:3000、http://localhost:5173、http://127.0.0.1:3000、http://127.0.0.1:5173
```

生产环境通过后端环境变量 `CORS_ALLOWED_ORIGINS` 配置前端域名（逗号分隔，不支持 `*`）。

### 1.4 统一响应结构

所有接口（含错误）返回：

```json
{ "code": 0, "msg": "success", "data": { } }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | `0`=成功，非 0=失败（见错误码表） |
| msg | String | 提示信息，可直接 toast 展示 |
| data | T | 业务数据，失败时为 null（序列化时省略） |

分页数据嵌在 `data` 中：

```json
{ "list": [], "total": 100, "pageNo": 1, "pageSize": 20 }
```

### 1.5 时间格式

- 日期时间（LocalDateTime 入参/出参）：`yyyy-MM-dd HH:mm:ss`，如 `2026-09-27 17:00:00`
- 日期（LocalDate）：`yyyy-MM-dd`，如 `2026-09-27`

### 1.6 错误码表

| code | 枚举 | message |
|---|---|---|
| 0 | SUCCESS | success |
| 1000 | SYSTEM_ERROR | 系统繁忙，请稍后重试 |
| 1001 | INVALID_PARAMETER | 请求参数错误 |
| 1002 | NOT_FOUND | 数据不存在 |
| 1003 | DUPLICATE | 数据已存在 |
| 2001 | UNAUTHORIZED | 未登录或登录已过期 |
| 2003 | FORBIDDEN | 没有操作权限 |
| 2004 | NEED_JOIN | 账号未入店，请使用邀请码入店 |
| 2005 | USER_REMOVED | 你已被移出门店，请联系店长 |
| 2006 | USER_DISABLED | 账号已被禁用 |
| 2007 | TENANT_EXPIRED | 租户已到期，请续费 |
| 2008 | INVITE_INVALID | 邀请码无效 |
| 2009 | INVITE_USED | 邀请码已被使用 |
| 2010 | INVITE_EXPIRED | 邀请码已过期，请联系店长重新生成 |
| 2011 | ALREADY_JOINED | 当前微信已加入门店 |
| 2012 | PHONE_ACCOUNT_EXISTS | 手机号已绑定其他微信，请使用接管流程 |
| 3001 | QUOTA_NOT_ENOUGH | 额度不足 |
| 4001 | COMPLIANCE_REJECTED | 内容未通过合规检查 |

> **前端建议**：`2004` 引导至入店页；`2001` 清除本地 token 跳登录页；`3001` 引导充值/找店长划拨；`4001` 展示 msg 中的拦截原因。

---

## 2. 角色与数据权限

### 2.1 租户内角色

| 角色 | 说明 | 管理端读 | 管理端写 | 建任务 |
|---|---|---|---|---|
| `HQ_ADMIN` | 总部管理员 | ✅ | ✅ | 全部范围（1/2/3/4） |
| `REGION_ADMIN` | 区域管理员 | ✅（限本区域） | ✅（限本区域） | 区域/门店/员工（不能建全域） |
| `VIEWER` | 只读角色 | ✅ | ❌ | ❌ |
| `OWNER` | 店长 | ❌（走门店端接口） | ❌ | 本店（scope=3） |
| `STAFF` | 员工 | ❌ | ❌ | ❌ |

平台端角色：`PLATFORM_FINANCE`（平台财务，可登记/确认/撤销收款单）。

### 2.2 数据范围（dataScope）

| 值 | 含义 |
|---|---|
| 1 | 全局（总部） |
| 2 | 区域 |
| 3 | 门店 |
| 4 | 个人（员工入店默认值） |

### 2.3 权限判定规则速查

- **管理端读**（`/api/admin/*` 查询类）：HQ_ADMIN / REGION_ADMIN / VIEWER
- **管理端写**（`/api/admin/*` 新增/修改类）：HQ_ADMIN / REGION_ADMIN
- **总部专属**（合规词、提示词模板、租户续费、素材推优审核）：仅 HQ_ADMIN
- **任务可见性**：HQ_ADMIN/VIEWER 全部；REGION_ADMIN 按目标范围匹配本区域；OWNER/STAFF 只能看到 目标为本店/本人/本店上级区域 的任务
- **门店可见性**：OWNER/STAFF 仅本店；REGION_ADMIN 仅本区域直营门店

---

## 3. 枚举字典

### 任务 task

| 字段 | 取值 |
|---|---|
| formType | 1=固定动作任务；2=指定内容任务（须关联 contentPackageId） |
| frequency | 1=每日（周期=当天）；2=每周（周期=本周一）；3=每月（周期=本月1号） |
| targetScope | 1=全员；2=区域（targetIds=区域id）；3=门店（targetIds=门店id）；4=员工（targetIds=用户id） |
| judgeType | 1=直接完成；2=需截图凭证（发布时 proofUrl 必填） |
| status | 1=启用；2=停用 |
| createdLevel | 1=总部创建；2=区域创建；3=门店创建 |
| targetIds | Long 数组（请求传 JSON 数组，响应为 JSON 字符串） |
| task_record.status | 0=未完成；1=已完成 |

### 作品 work

| 字段 | 取值 |
|---|---|
| status（生成状态） | PENDING=生成中；SUCCESS=成功；FAILED=失败（failReason 给出原因） |
| type | IMAGE=图片；VIDEO=视频 |
| publishStatus（发布状态机） | 见下方状态机 |

```text
NONE（初始/生成中/失败）
  ├→ DRAFT（审核开关关闭时的默认态，可直接发布）
  │    └→ PUBLISHED
  └→ PENDING_AUDIT（审核开关开启时进入待审核）
       ├→ APPROVED → PUBLISHED
       └→ REJECTED →（改文案后自动重新提审）PENDING_AUDIT
PUBLISHED 为终态，重复发布不流转
```

### 素材 asset

| 字段 | 取值 |
|---|---|
| scope | PLATFORM=行业包（只读）；BRAND=品牌层（总部管理）；STORE=门店层（店长管理） |
| status | APPROVED=可用；PENDING_REVIEW=推优待审；REJECTED=推优驳回；DELETED=已软删 |
| type | IMAGE / VIDEO / SCRIPT 等 |
| 上传限制 | jpg/png/mp4；图片 ≤10MB，视频 ≤100MB |

### 其他

| 对象 | 取值 |
|---|---|
| tenant.status | 1=正常；2=停用；3=到期 |
| org.type | 1=品牌；2=区域；3=门店 |
| user.status | 1=正常；2=禁用 |
| user_org_role.status | 1=有效；2=已移除 |
| content_package.status | 1=ACTIVE 待下发；2=DISPATCHED 已下发；3=CANCELED 已撤销 |
| compliance_word.level | 1=替换（replacement）；2=拦截（直接拒绝生成） |
| quota_account.level | TENANT=租户池；STORE=门店；STAFF=员工 |
| quota_flow.bizType | CREDIT=充值；ALLOCATE_OUT=划拨出账；ALLOCATE_IN=划拨入账；CONSUME=消费；REFUND=退款；RECALL=回收 |
| payment_order.status | PENDING=待确认；SETTLED=已结算；CANCELED=已撤销 |
| export_task.status | 0=进行中；1=成功；2=失败 |
| chat action | ASK=继续追问；GENERATE=产出文案版本 |
| chat_session.status | ACTIVE=进行中；CLOSED=已关闭 |
| ranking scope | STORE=门店榜；NATIONAL=全国榜 |
| ranking period | WEEK=周榜；MONTH=月榜 |
| 徽章 code | STREAK_7_DAYS=连续7天打卡；WEEK_100_PERCENT=周任务全勤；MONTH_TASK_STAR=月度任务之星；STORE_TASK_STAR=门店完成率第一 |

---

## 4. 接口明细

> 「权限」列说明：`登录` = 任意已登录用户；`管理读` = HQ_ADMIN/REGION_ADMIN/VIEWER；`管理写` = HQ_ADMIN/REGION_ADMIN；`总部` = 仅 HQ_ADMIN；`店长` = 仅 OWNER；`财务` = 平台 PLATFORM_FINANCE。

### 4.1 健康检查

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/health` | 免登录 | 返回 `data: {"status":"UP"}` |

### 4.2 认证与账号

#### POST `/api/auth/login` 微信登录（免登录）

请求：`{ "openid": "微信openid（必填）" }`

响应 `data`：TokenResponse

```json
{
  "token": "xxx", "userId": 1, "tenantId": 1, "orgId": 3,
  "role": "STAFF", "dataScope": 4,
  "tenantStatus": 1, "tenantName": "演示品牌", "orgName": "门店A"
}
```

**业务逻辑**：
1. 按 openid 查用户；**不存在返回 `code=2004 NEED_JOIN`**，前端应引导走邀请码入店；
2. 存在则校验租户状态：停用（status=2）、到期（status=3 或 expireAt 已过）均拒绝（`2007`）；
3. 取该用户第一条有效成员关系作为登录身份（一人多店时默认第一条）。

#### POST `/api/auth/join` 邀请码入店（免登录）

请求：

```json
{ "code": "ABC123DEF456", "phone": "13800000000", "openid": "微信openid", "nickname": "昵称" }
```

响应 `data`：`{ "login": TokenResponse, "storeId": 3 }`

**业务逻辑**：
1. 原子核销邀请码（校验有效、未过期、未使用；已用返回 `2009`，过期 `2010`），邀请码一次性使用；
2. 按 openid 或手机号查/建用户；该微信已有有效成员关系返回 `2011 ALREADY_JOINED`；
3. 入店建立成员关系（dataScope=4），STAFF 同时初始化额度账户（余额 0）；
4. 直接返回登录 token，前端拿到后即完成登录。

#### POST `/api/auth/takeover` 手机号接管微信账号（免登录）

请求：`{ "phone": "手机号（必填，^1[3-9]\d{9}$）", "openid": "新openid（必填）" }`

响应 `data`：TokenResponse

**业务逻辑**：按手机号找用户（不存在报错）→ 新 openid 已绑定其他账号返回 `2012` → openid 与当前一致直接登录 → 否则记录换绑并更新 openid 后登录。

> ⚠️ 当前实现未做短信验证码等二次核验，生产接入微信正式登录前仅可用于联调。

#### POST `/api/auth/logout` 退出登录

请求头携带 `Authorization`，无请求体，删除会话。

#### GET `/api/auth/me` 当前身份

响应 `data`：`{ "userId", "tenantId", "orgId", "role", "dataScope" }`（前端启动时调用一次，用于路由/菜单控制）

#### PUT `/api/users/{userId}/roles` 授予角色（管理角色）

请求：`{ "orgId": 3, "role": "STAFF", "dataScope": 4 }`

**业务逻辑**：需管理角色；用户和组织须存在；非 HQ_ADMIN 只能授权本组织；同一 用户+组织+角色 已存在则更新，否则新增。

#### PATCH `/api/users/roles/{roleId}` 修改成员角色（管理角色）

请求：`{ "role": "OWNER", "dataScope": 3 }`（按成员关系 id 修改）；非 HQ_ADMIN 只能改本店成员。

#### DELETE `/api/users/roles/{roleId}` 移出成员（管理角色）

**业务逻辑**：STAFF 关系不可删除（应走禁用）；OWNER 只能移除本店成员。

#### PATCH `/api/users/{userId}/disable` 禁用账号（管理角色）

禁用后该用户无法登录（`2006 USER_DISABLED`）。

#### POST `/api/invites` 创建邀请码（管理角色）

请求：`{ "storeId": 3, "expireAt": "2026-10-01 00:00:00", "role": "STAFF" }`（role 可选，默认 STAFF，支持 STAFF/OWNER）

响应 `data`：`{ "id", "storeId", "code", "role", "expireAt" }`

**业务逻辑**：目标必须是门店（type=3）；HQ_ADMIN 可为任意门店创建，OWNER 仅本店，REGION_ADMIN 仅本区域门店；code 为 12 位大写随机码。

#### GET `/api/invites/{code}` 校验邀请码（免登录）

响应 `data` 同上；无效/已用/过期分别返回 2008/2009/2010。前端入店页先调它展示门店信息。

### 4.3 租户与组织

#### POST `/api/tenants/open` 开通租户（免登录）

请求：

```json
{
  "name": "品牌名（必填）", "type": 1, "industry": "餐饮（必填）",
  "assetPackageId": 1, "adminPhone": "13800000000（必填）",
  "adminOpenid": "微信openid", "adminNickname": "昵称",
  "expireAt": "2027-09-27 00:00:00"
}
```

响应 `data`：`{ "tenantId", "orgId", "userId" }`

**业务逻辑**：事务内创建 租户 → 顶级品牌组织 → 管理员用户 → HQ_ADMIN 角色关系。

#### GET `/api/tenants/{tenantId}` 查询租户（登录即可，未做额外校验）

响应 `data`：Tenant（含 status、expireAt，前端可据此提示到期）

#### PATCH `/api/tenants/{tenantId}/renew` 租户续费（总部）

请求体：`"2027-09-27 00:00:00"`（JSON 字符串，新的到期时间）。仅 HQ_ADMIN 且只能续本租户。

#### POST `/api/orgs` 创建组织节点（管理角色）

请求：`{ "type": 2, "name": "华东区域", "parentId": 1 }`（type: 1品牌/2区域/3门店；parentId=0 或不传为根）

**业务逻辑**：OWNER 只能创建门店；parentId 非 0 时必须存在。

#### GET `/api/orgs/tree` 组织树（登录）

响应 `data`：`[{ "id", "parentId", "type", "name", "children": [ ... ] }]`（当前返回本租户全部组织）

#### PATCH `/api/orgs/{orgId}/name` 重命名（管理角色，需有该组织管理权）

### 4.4 管理端总览 / 门店 / 成员

#### GET `/api/admin/dashboard/overview` 总览（管理读，区域管理员限本区域）

响应 `data`：

```json
{ "activeStores": 12, "weeklyWorks": 340, "weeklyPublishes": 210, "totalQuota": 50000, "usedQuota": 1230 }
```

**业务逻辑**：活跃门店=近 7 天有作品；统计本周作品/发布数、额度余额合计、本周已用额度；结果有缓存。

#### GET `/api/admin/dashboard/trend` 近 7 天趋势（管理读）

响应 `data`：`[{ "date": "2026-09-21", "works": 40, "publishes": 25 }]`（固定补齐 7 天）

#### GET `/api/admin/stores` 门店列表（管理读）

响应 `data`：`[{ "store": {…Org}, "memberCount": 8, "ownerUserId": 5 }]`；区域管理员只见本区域门店。

#### POST `/api/admin/stores` 创建门店（管理写）

请求：`{ "name": "门店B", "parentId": 2 }`（parentId 为品牌或区域 id，不传默认挂当前组织下）

**业务逻辑**：区域管理员只能在本区域下创建；创建后自动初始化门店额度账户（余额 0）。

#### PATCH `/api/admin/stores/{storeId}/parent` 调整门店归属（管理写）

请求：`{ "parentId": 2 }`；新上级不能是门店；区域管理员只能调到本区域。

#### GET `/api/admin/members` 成员分页（管理读）

Query：`orgId`（可选）、`pageNo`（默认 1）、`pageSize`（默认 20，最大 100）

响应 `data`：PageResult&lt;UserAccount&gt;，每项含：

```json
{ "id": 8, "phone": "138****0000", "nickname": "小王", "status": 1,
  "quotaBalance": 35, "userOrgRoleId": 21 }
```

**业务逻辑**：`quotaBalance` 按角色回填——STAFF=员工账户余额、OWNER=门店账户余额、管理层=租户池余额；STAFF 的 `userOrgRoleId` 是后续店长划拨/回收额度的 `memberRoleId` 参数。

### 4.5 素材中心

#### POST `/api/assets` 门店素材上传（STAFF/OWNER，即传即用）

- Content-Type: `multipart/form-data`
- 表单字段：`file`（必填）、`name`（可选）、`category`（可选，默认 DEFAULT）

响应 `data`：Asset。素材直接入库为 `scope=STORE, status=APPROVED`，本店立即可见。

#### GET `/api/assets` 三层可见素材（登录）

Query：`category`（可选）。响应 `data`：Asset 数组。

**业务逻辑**：返回 `PLATFORM（挂载行业包）∪ BRAND（本租户品牌层）∪ STORE（本店）` 三层合集；门店角色按本店过滤。

#### POST `/api/assets/recommend` 推优本店素材（STAFF/OWNER）

请求：`{ "assetId": 10 }`

**业务逻辑**：仅 `scope=STORE` 的素材可推优；推优后进入 `PENDING_REVIEW` 待总部审核；已在流程中的素材重复推优返回 `1003 DUPLICATE`。

#### POST `/api/admin/assets` 品牌素材上传（总部）

- 同上 multipart 表单；入库为 `scope=BRAND, status=APPROVED`，全租户可见。

#### GET `/api/admin/assets` 素材列表（管理读）

Query：`scope`、`status`、`category`、`pageNo`、`pageSize`。
**`status=PENDING_REVIEW` 即「推优审核 Tab」数据源**。

#### PUT `/api/admin/assets/{id}` 编辑名称/分类（管理写）

请求：`{ "name": "新名称", "category": "海报" }`；PLATFORM 层素材只读；BRAND 层仅总部可改；STORE 层仅本店店长可改。

#### DELETE `/api/admin/assets/{id}` 软删（管理写，同上权限）

#### PUT `/api/admin/assets/{id}/review` 推优审核（总部）

请求：`{ "pass": true, "category": "海报" }`

**业务逻辑**：`pass=true` → 素材升层为 BRAND（全租户可见，可指定分类）；`pass=false` → 状态置 REJECTED 并向**上传人发站内消息**。

### 4.6 内容包

#### POST `/api/admin/content-packages` 创建（管理写）

请求：

```json
{
  "name": "国庆活动包（必填，≤128字）",
  "calendarDate": "2026-10-01（必填，不能早于今天）",
  "publishAt": "2026-10-01 09:00:00（必填，下发时刻）",
  "copyDirection": "推广方向（≤2000字）",
  "taskTemplate": {
    "title": "发一条国庆海报",
    "actionType": 2, "platform": "朋友圈", "frequency": 1,
    "judgeType": 2, "endTime": "2026-10-01 23:59:59",
    "targetScope": 1, "targetIds": []
  }
}
```

**业务逻辑**：模板强校验（actionType=2 即指定内容时校验发布平台等要素）；创建后状态 ACTIVE，等定时任务在 `publishAt` 扫描下发。

#### GET `/api/admin/content-packages` 分页（管理写）

Query：`status`（可选，按枚举过滤）、`pageNo`、`pageSize`。

#### DELETE `/api/admin/content-packages/{id}` 撤销（管理写）

仅 ACTIVE 状态可撤销（ACTIVE→CANCELED）；已下发（DISPATCHED）不可撤销。

**定时下发**：每分钟扫描 `publishAt` 到期的 ACTIVE 内容包 → 按 taskTemplate 自动创建一个**全域任务** → 内容包置为 DISPATCHED。前端创建后无需再调用任务接口。

### 4.7 风格选项

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/admin/styles` | 管理读 | 生成页风格选择器数据源（含名称/说明/示例图/排序） |
| POST | `/api/admin/styles` | 管理写 | `{ "name": "必填", "description", "exampleUrl", "sortNo": 0, "packageId" }` |
| PUT | `/api/admin/styles/{id}` | 管理写 | 同创建字段（无 packageId） |
| DELETE | `/api/admin/styles/{id}` | 管理写 | 删除风格 |

### 4.8 合规配置

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/admin/compliance/words` | 管理读 | 合规词列表 |
| PUT | `/api/admin/compliance/words` | 总部 | 新增/更新：`{ "id"?, "word": "必填", "level": 1, "replacement": "替换词" }`；level=1 替换（replacement 为空则删除命中词），level=2 拦截 |
| DELETE | `/api/admin/compliance/words/{id}` | 总部 | 停用 |
| GET | `/api/admin/compliance/audit-config/{orgId}` | 管理读 | 组织审核开关（orgId=0 为品牌默认） |
| PUT | `/api/admin/compliance/audit-config/{orgId}` | 管理写 | `{ "enabled": true }`；总部可改任意组织，其他角色只能改本组织 |

**生效时机**：合规词在**对话出稿（chat / revise）环节**对每版文案过滤，level=2 命中直接抛 `4001 COMPLIANCE_REJECTED`。审核开关决定新作品进入 `PENDING_AUDIT`（开）还是 `DRAFT`（关）；查无组织配置时回退品牌默认（orgId=0）。

### 4.9 作品审核

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/admin/audit/works` | 管理读 | 待审核作品列表（PENDING_AUDIT，上限 100 条，按可见门店过滤） |
| POST | `/api/admin/audit/works/{id}/approve` | 管理读权限即可调 | 通过：PENDING_AUDIT→APPROVED |
| POST | `/api/admin/audit/works/{id}/reject` | 管理读权限即可调 | 驳回：请求 `{ "opinion": "必填，≤512字" }`，PENDING_AUDIT→REJECTED，并向作品作者**发站内消息** |

**业务逻辑**：作品必须处于 PENDING_AUDIT 且作者门店在操作者可见范围内；状态机条件更新防并发双审。

### 4.10 导出任务

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/admin/exports` | 管理写 | `{ "exportType": 1, "queryParams": "{}" }`；exportType 取 1~4（具体类型与后端约定） |
| GET | `/api/admin/exports` | 管理读 | 任务列表 |
| GET | `/api/admin/exports/{id}` | 管理读 | 任务详情（前端轮询 status） |

**业务逻辑**：
1. 同租户**最多 3 个进行中任务**，超限返回 `1003 DUPLICATE`；
2. 创建后异步处理：成功 `status=1` + `fileUrl`；失败 `status=2` + `failReason`；
3. ⚠️ 当前 `fileUrl` 为 `local://export/...` 占位地址，**不能直接下载**，接入 OSS 前请勿展示下载按钮。

### 4.11 提示词模板

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/admin/prompt-template` | 管理读 | 模板列表（scene=IMAGE/VIDEO，含 version/status） |
| PUT | `/api/admin/prompt-template` | 总部 | `{ "id"?, "scene": "IMAGE", "template": "必填" }`；保存时版本自动 +1，旧版本自动停用 |
| PUT | `/api/admin/prompt-template/{id}/rollback` | 总部 | 回滚：停用该 scene 全部版本后激活指定历史版本 |

### 4.12 AI 媒体任务查询

#### GET `/api/admin/media-task/list`（管理读）

Query：`limit`（默认 50，1~200）。响应 `data`：MediaTask 数组（status：PENDING/PROCESSING/SUBMITTED/POLLING/SUCCESS/FAILED），用于管理端排查生成任务（含 providerTaskId、errorMessage、refundStatus）。

### 4.13 作品与 AI 生成

#### POST `/api/work/generate` 生成作品（STAFF/OWNER 等，登录）

请求：

```json
{
  "type": "IMAGE（必填，IMAGE/VIDEO）",
  "platform": "朋友圈（必填，≤32字）",
  "styleId": 1,
  "productName": "产品名（≤2000字）",
  "userInput": "用户输入（≤4000字）",
  "refImageUrls": ["参考图URL，最多10张"],
  "assetIds": [1, 2],
  "chatSessionId": 5
}
```

响应 `data`：Work（此时 `status` 通常为 `PENDING`）

**业务逻辑（生成链路）**：
1. 校验类型与登录态 → 按租户/平台/风格/产品名/用户输入组装提示词 → 合规词过滤（对话模式外此环节 level=2 同样拦截）；
2. 创建 work → **扣减门店额度**（幂等键 `gen:{workId}`，员工有个人账户时优先扣员工额度）→ 额度不足返回 `3001`；
3. 创建 media_task，事务提交后**异步**派发给模型；
4. 成功：结果转存 → `status=SUCCESS` + `contentUrl`；转存失败最多重试 3 次；
5. 失败：`status=FAILED` + `failReason`，并**自动全额退款**（幂等键 `refund:{taskId}`）；
6. 图片同步返回；视频为「提交-轮询」模式，超过 30 分钟由超时扫描兜底置失败并退款。

#### GET `/api/work/{id}` 作品详情（登录，仅本人或管理角色）

响应 `data`：Work 完整字段。**前端轮询此接口直到 `status` 变为 SUCCESS/FAILED**（建议 2~3 秒一次）。

#### PUT `/api/work/{id}/caption` 改文案（登录，仅本人）

请求：`{ "caption": "必填，≤2000字" }`

**业务逻辑**：DRAFT/APPROVED/REJECTED 可改；**REJECTED 改稿后自动重新提审**（→PENDING_AUDIT）。

#### POST `/api/work/{id}/regenerate` 重新生成（登录，仅本人）

**业务逻辑**：DRAFT/APPROVED/REJECTED 可重生成；**按旧任务价格全额重新扣费**（幂等键 `gen:{新任务ID}`）；作品回到生成中状态，旧成品地址保留在 media_task 历史。

### 4.14 对话创作

> 引导式聊天创作：员工说人话 → AI 追问补齐要素 → 一次出 3 版文案 → 可微调换版。
> 计费：首次出稿 1 点（幂等键 `chat:{sessionId}:gen`），每次微调 1 点（`chat:{sessionId}:rev:{n}`）。

#### POST `/api/chat/sessions` 创建会话（登录）

请求：`{ "scene": "朋友圈文案（必填，≤32字）" }`；响应 `data`：ChatSession。

#### GET `/api/chat/sessions` 我的会话列表（登录）

#### GET `/api/chat/sessions/{id}` 会话全量历史（登录，仅本人）

响应 `data`：`{ "session": ChatSession, "messages": [ChatMessage] }`（重进页面恢复用）

#### POST `/api/chat/sessions/{id}/messages` 发送消息（核心对话接口）

请求：`{ "text": "必填，≤2000字" }`

响应 `data`：ChatReplyVO

```json
{
  "sessionId": 5, "action": "ASK", "question": "产品有什么卖点？",
  "versions": ["版本1文案", "版本2文案", "版本3文案"],
  "context": "{}", "messageId": 88
}
```

**业务逻辑**：
1. `action=ASK`：要素未齐，继续追问（`question` 展示给用户；模型异常时兜底追问「能再具体一点吗？」）；
2. `action=GENERATE`：要素已齐，返回 `versions`（3 版文案），每版都经过合规过滤（level=2 命中会抛 4001 使本次请求回滚），**全部为空则降级为 ASK**；
3. 出稿时扣 1 点额度（`QUOTA_NOT_ENOUGH` 时报 3001）。

#### POST `/api/chat/sessions/{id}/revise` 微调指定版本

请求：`{ "versionNo": 2, "instruction": "口语化一点（必填，≤1000字）" }`

响应 `data`：ChatReplyVO（`action=GENERATE`，`versions` 只有 1 版新文案，`revisedFrom=2` 标记来源版本）

**业务逻辑**：基于最近一次 GENERATE 的指定版本改写 → 合规过滤 → 扣 1 点 → 微调次数 +1。

### 4.15 任务管理与发布核销

#### POST `/api/task` 创建任务（管理角色/店长）

请求：

```json
{
  "title": "每日朋友圈打卡（必填）",
  "formType": 1,
  "contentPackageId": null,
  "platform": "朋友圈",
  "frequency": 1,
  "targetScope": 3,
  "targetIds": [3],
  "judgeType": 2,
  "startAt": "2026-09-27 00:00:00",
  "endAt": "2026-09-30 23:59:59"
}
```

**业务逻辑**：
1. 权限：HQ_ADMIN 全部范围；REGION_ADMIN 不能建全域（scope=1），建区域任务时 targetIds 必须且仅为本区域；OWNER 只能建本店任务（scope=3，targetIds=本店）；VIEWER/STAFF 禁止；
2. 校验 endAt ≥ startAt；formType=2 必须传 contentPackageId；
3. 落库（status=1，记录 createdLevel）并为当前周期内**所有命中的 STAFF/OWNER 预生成 task_record**。

#### PUT `/api/task/{id}` 编辑任务

请求：`{ "title": "必填", "contentPackageId", "platform", "startAt", "endAt" }`（只能改标题/关联内容包/平台/起止时间）

**业务逻辑**：
- HQ_ADMIN 可改任意；REGION_ADMIN 可改本区域可见的、区域及以下层级的任务；OWNER 可改本店可见的总部/区域任务；
- **店长改总部/区域任务时生成「门店变体副本」**：不覆盖原任务，新任务 `sourceTaskId=原任务`、`targetScope=3`、`targetIds=本店`、`createdLevel=3`；原任务保留，本店员工优先看到副本；
- 每次修改写入 task_modify_log（`GET /api/task/{id}/modify-logs` 可查，仅 HQ_ADMIN/REGION_ADMIN）。

#### PATCH `/api/task/{id}/status` 启停任务

请求：`{ "status": 2 }`（1=启用/2=停用）；权限同编辑。

#### GET `/api/task/{id}` 任务详情（登录，本租户内）

#### GET `/api/task/my` 我的任务（STAFF/OWNER 核心接口）

响应 `data`：TaskResponse 数组：

```json
{
  "id": 10, "title": "每日朋友圈打卡", "formType": 1, "contentPackageId": null,
  "platform": "朋友圈", "frequency": 1, "targetScope": 3, "judgeType": 2,
  "sourceTaskId": null, "createdLevel": 1, "status": 1,
  "periodDate": "2026-09-27", "recordStatus": 0, "publishRecordId": null
}
```

**业务逻辑**：返回当前租户所有「活跃且命中我」的任务；若本店存在某总部任务的**变体副本**，则只返回副本不返回原任务；返回前自动确保当前周期 task_record 存在。`recordStatus`（0/1）直接用于列表角标展示「未完成/已完成」。

#### POST `/api/task/remind` 任务提醒（总部/区域/店长）

请求：`{ "taskId": 10, "periodDate": "2026-09-27（可选，默认当前周期）" }`

响应 `data`：`3`（成功提醒的人数）

**业务逻辑**：向目标范围内当前周期未完成的 STAFF/OWNER 写站内消息（message 表）；店长只能提醒本店在目标内的任务；返回值为提醒人数，前端可直接 toast「已提醒 N 人」。

#### GET `/api/task/store-board` 门店看板

Query：`storeId`（必填）、`periodDate`（必填，yyyy-MM-dd）

响应 `data`：

```json
{ "storeId": 3, "periodDate": "2026-09-27", "expected": 8, "finished": 5,
  "completionRate": 0.625, "unfinished": [ {…TaskRecord} ] }
```

**业务逻辑**：非总部/区域角色只能看本店（storeId=登录 orgId）；统计前自动补齐当前周期记录。

#### POST `/api/publish-record` 提交发布核销（STAFF/OWNER 核心接口）

请求：

```json
{ "workId": 20, "taskId": 10, "platform": "朋友圈", "proofUrl": "截图地址（judgeType=2 时必填）" }
```

响应 `data`：PublishRecord

**业务逻辑（校验顺序）**：
1. 作品必须是**当前用户**的；
2. 作品发布状态须为 DRAFT / APPROVED / PUBLISHED（PENDING_AUDIT 不可发布）；
3. 关联任务时依次校验：任务启用（status=1）→ 当前时间在任务有效期内 → 发布平台与任务要求一致 → 当前用户在任务目标范围内（scope=1 全员 / 2 区域 / 3 门店 / 4 本人）→ `judgeType=2` 时必须带 `proofUrl`；
4. 通过后：写 publish_record（留存凭证）→ 当前周期 task_record 标记完成（幂等，`uk_task_user_period` 唯一键）→ 作品状态流转到 PUBLISHED。

### 4.16 任务统计与激励

#### GET `/api/task/board` 任务总看板

Query：`date`（可选，默认今天）。响应 `data`：`{ "date": "2026-09-27", "tasks": [ { "taskId", "title", "status", "targetScope", "periodDate", "expected", "finished", "completionRate" } ] }`
**业务逻辑**：遍历当前用户可见任务（区域管理员限本区域）逐任务统计期望/完成数与完成率。

#### GET `/api/task/board/stores` 任务×门店汇总

Query：`taskId`（必填）、`date`（可选）。响应 `data`：`[{ "storeId", "storeName", "expected", "finished", "completionRate" }]`（区域管理员限本区域门店）

#### GET `/api/task/board/records` 员工完成明细（三级穿透最后一层）

Query：`taskId`、`storeId`（必填）、`date`（可选）。响应 `data`：

```json
[{ "id": 31, "taskId": 10, "userId": 8, "storeId": 3, "nickname": "小王",
   "storeName": "门店A", "periodDate": "2026-09-27", "status": 1,
   "publishRecordId": 55, "proofUrl": "截图地址", "finishedAt": "2026-09-27 10:20:00" }]
```

**业务逻辑**：需对该门店有可读权限（`requireStoreReadable`：HQ/VIEWER 全部、区域限本区域、店长/员工仅本店）。

#### GET `/api/task/ranking` 完成率排行榜

Query：`scope`（STORE 默认 / NATIONAL）、`period`（WEEK 默认 / MONTH）、`storeId`、`limit`（默认 50，1~100）

响应 `data`：`[{ "rank": 1, "userId", "nickname", "storeId", "storeName", "expected", "finished", "completionRate", "lastFinishedAt" }]`

**业务逻辑**：NATIONAL（全国榜）仅 HQ_ADMIN/VIEWER 可查；OWNER/STAFF 只能看本店榜（scope=STORE）。

#### GET `/api/task/badges` 荣誉徽章

Query：`userId`（可选，默认查自己）、`date`（可选，默认今天）

响应 `data`：

```json
[{ "code": "STREAK_7_DAYS", "name": "连续7天打卡", "description": "…",
   "achieved": true, "value": 7, "threshold": 7 }]
```

**徽章类型**：STREAK_7_DAYS（连续 7 天完成）、WEEK_100_PERCENT（本周全勤）、MONTH_TASK_STAR（本月可见范围完成率第一）、STORE_TASK_STAR（本月门店第一）。查他人需有可见权限。

#### GET `/api/admin/task/{taskId}/report` 管理端任务报表

Query：`storeId`（可选）、`periodDate`（可选）。响应 `data`：`{ "taskId", "periodDate", "expected", "finished", "completionRate", "records": [TaskRecord…] }`
**业务逻辑**：需对该任务有可见权限（`canViewTask`），统计范围自动按角色裁剪（区域管理员限本区域）；传 storeId 时可下钻单店（会自动补齐该店记录）。

### 4.17 额度

> 三级账户模型：`TENANT（租户池）→ STORE（门店）→ STAFF（员工）`；每次变动写 quota_flow 双流水，全部操作带幂等键防重。

#### GET `/api/quota/my` 我的额度（登录）

响应 `data`：`{ "account": {…QuotaAccount}, "recentFlows": [最近10条 QuotaFlow] }`
**业务逻辑**：STAFF 返回员工账户；OWNER 返回门店账户；管理层返回租户池。

#### POST `/api/quota/staff/allocate` 店长向员工划拨（店长）

请求：`{ "memberRoleId": 21, "amount": 10, "bizId": "可选幂等ID", "remark": "可选" }`

**业务逻辑**：memberRoleId 必须是**本店 STAFF** 的有效成员关系；门店池余额不足整体失败；写门店 `ALLOCATE_OUT` + 员工 `ALLOCATE_IN` 双流水。`memberRoleId` 从成员列表接口的 `userOrgRoleId` 获取。

#### POST `/api/quota/staff/recall` 店长回收员工额度（店长）

请求：同上。**回收金额不能超过员工当前余额**；写员工 `RECALL`（-）+ 门店 `RECALL`（+）双流水。

#### POST `/api/admin/quota/credit` 租户池充值（总部）

请求：`{ "accountId": 1, "amount": 1000, "bizId": "可选幂等ID", "remark" }`；建议前端生成 UUID 作为 `bizId` 防重复提交。

#### POST `/api/admin/quota/allocate` 租户池→门店划拨（总部）

请求：`{ "storeId": 3, "amount": 200, "bizId", "remark" }`；租户池余额不足整体失败。

#### GET `/api/admin/quota/store/{storeId}` 门店账户（管理读）

#### GET `/api/admin/quota/account/{accountId}/flows` 账户流水分页（管理读）

Query：`bizType`（可选）、`from`、`to`（可选）、`pageNo`（1）、`pageSize`（20）
响应 `data`：PageResult&lt;QuotaFlow&gt;（含 `amount` 正负、`balanceAfter`）。

### 4.18 平台端

> 供平台运营后台使用，与租户端登录态互相独立（`tenantId=null` 的特殊会话）。

#### POST `/platform/auth/login` 平台登录（免登录）

请求：`{ "loginName": "账号", "credential": "密码" }`
响应 `data`：`{ "token", "platformUserId", "role" }`（role 当前为 PLATFORM_FINANCE）；账号不存在/停用/密码错误统一返回 `2001`。

#### POST `/platform/payment/register` 登记收款单（财务）

请求：

```json
{ "tenantId": 1, "amount": 5000, "channel": "对公转账",
  "orderNo": "可选，不传自动生成 PAY-UUID", "voucherUrl": "凭证", "invoiceNo": "发票号" }
```

响应 `data`：PaymentOrder（status=PENDING）。**orderNo 重复时幂等返回已有订单**。

#### POST `/platform/payment/{id}/confirm` 确认收款（财务）

请求体可选：`{ "invoiceNo": "补填发票号" }`

**业务逻辑**：
1. 订单必须为 PENDING（SETTLED 幂等返回；CANCELED 报错）；
2. 确认后**自动按金额充值到该租户的租户池**（幂等键 `pay:{orderId}`）；
3. 订单置 SETTLED，记录确认人和时间。

#### POST `/platform/payment/{id}/cancel` 撤销收款单（财务）

仅 PENDING 可撤销；已撤销幂等返回。

---

## 5. 典型业务流程（前端调用时序）

### 流程一：登录 / 入店

```text
POST /api/auth/login {openid}
  ├─ code=0 → 拿 token，GET /api/auth/me 初始化身份 → 进首页
  └─ code=2004 → 入店页：
       GET /api/invites/{code} 展示门店信息
       → POST /api/auth/join {code, phone, openid, nickname}
       → 拿 token 直接进入
```

### 流程二：AI 生成 → 发布核销

```text
GET /api/admin/styles 选风格
→ POST /api/work/generate（扣额度，返回 PENDING 的 work）
→ 轮询 GET /api/work/{id}（2~3s 一次）直至 status=SUCCESS/FAILED
   ├─ FAILED：展示 failReason（额度已自动退）
   └─ SUCCESS：拿 contentUrl 预览
→ GET /api/task/my 找到要完成的任务（recordStatus=0）
→ POST /api/publish-record {workId, taskId, platform, proofUrl?}
→ 任务完成，看板/统计自动更新
```

### 流程三：对话创作

```text
POST /api/chat/sessions {scene} → sessionId
→ 循环 POST /api/chat/sessions/{id}/messages {text}
   ├─ action=ASK → 展示 question，继续收集
   └─ action=GENERATE → 展示 versions 3 版文案（已扣 1 点）
→ 不满意：POST /api/chat/sessions/{id}/revise {versionNo, instruction}（再扣 1 点）
→ 拿选中文案去 POST /api/work/generate（可带 chatSessionId 关联溯源）
```

### 流程四：作品审核

```text
审核开关开启时：作品发布/提审后 publishStatus=PENDING_AUDIT
→ 管理端 GET /api/admin/audit/works
→ POST /api/admin/audit/works/{id}/approve 或 /reject {opinion}
→ 员工端 GET /api/work/{id} 看到 auditOpinion
→ REJECTED：PUT /api/work/{id}/caption 改稿 → 自动重新提审
```

### 流程五：素材推优

```text
店长/员工 POST /api/assets 上传本店素材
→ POST /api/assets/recommend {assetId}（→ PENDING_REVIEW）
→ 总部 GET /api/admin/assets?status=PENDING_REVIEW
→ PUT /api/admin/assets/{id}/review {pass}
   ├─ pass=true → 升层 BRAND 全租户可见
   └─ pass=false → 置 REJECTED + 上传人收到站内消息
```

### 流程六：额度链路

```text
平台财务 POST /platform/payment/register → POST /platform/payment/{id}/confirm
（确认即自动充值租户池）
→ 总部 POST /api/admin/quota/allocate {storeId, amount}（租户池→门店）
→ 店长 POST /api/quota/staff/allocate {memberRoleId, amount}（门店→员工）
→ 员工生成扣费（CONSUME）→ 失败自动退款（REFUND）
→ 各级 GET …/flows 查流水
```

### 流程七：内容包定时下发

```text
总部 POST /api/admin/content-packages（含 taskTemplate）
→ 到达 publishAt 后系统自动创建全域任务（前端无需干预）
→ 员工在 GET /api/task/my 中看到新任务
→ 内容包状态变为 DISPATCHED（管理端分页可查）
```

---

## 6. 前端注意事项

1. **占位地址**：`work.contentUrl`、`asset.content`、`exportTask.fileUrl` 当前为 `demo://` / `local://` 占位地址（未接 OSS），联调阶段不要直接当可访问 URL 使用；导出任务也不要展示下载入口。
2. **轮询约定**：AI 生成用 `GET /api/work/{id}` 轮询（建议 2~3s，图片通常很快，视频可能数十秒到分钟级）；导出任务用 `GET /api/admin/exports/{id}` 轮询。
3. **幂等防重**：额度充值/划拨、店长划拨回收等写操作，建议前端生成 UUID 填入 `bizId`，避免重复点击造成重复扣充（后端按幂等键去重）。
4. **分页**：`pageNo` 从 1 开始；`pageSize` 成员列表上限 100。
5. **任务变体**：店长编辑总部任务会生成本店副本，员工列表接口已自动做「副本优先」，前端无需处理 `sourceTaskId` 去重。
6. **完成判定**：`judgeType=2` 的任务发布时必须传 `proofUrl`（截图地址），前端需在上传截图后再调发布接口。
7. **平台与租户登录态隔离**：`/platform/*` 与 `/api/*` 的 token 不通用。
8. **本地开发跨域**：前端跑在 3000/5173 端口即可直连后端，无需代理；其他端口需后端调整 `CORS_ALLOWED_ORIGINS`。
