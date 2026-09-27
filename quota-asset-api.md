# 额度计费域 & 资产配置域 · 前端接口文档

> 对应后端模块：`com.xiaoa.quota`（额度计费）、`com.xiaoa.asset`（素材 + 内容包）。
> 本文档描述当前已实现接口的真实行为，供前端对接使用。

---

## 1. 通用约定

### 1.1 认证与请求头

| 请求头 | 说明 |
| --- | --- |
| `Authorization: Bearer {token}` | 所有业务接口必传（登录类接口除外） |
| `Content-Type: application/json` | JSON 请求体接口 |
| `Content-Type: multipart/form-data` | 文件上传接口 |

两套账号体系使用**同一种 token 格式**：

- **企业端账号**：微信登录 / 手机号登录获得，角色 `OWNER / STAFF / HQ_ADMIN / REGION_ADMIN / VIEWER`。
- **平台端账号**：`POST /platform/auth/login` 获得，角色 `PLATFORM_OPS / PLATFORM_FINANCE`，没有租户归属。

### 1.2 统一响应结构

```json
{ "code": 0, "msg": "success", "data": { } }
```

- `code = 0` 成功；非 0 见错误码表。`data` 为空时字段不返回。
- 分页统一返回：

```json
{ "code": 0, "msg": "success", "data": { "list": [], "total": 0, "pageNo": 1, "pageSize": 20 } }
```

### 1.3 时间格式

- 请求/响应中的日期时间统一为 ISO 格式字符串：`yyyy-MM-ddTHH:mm:ss`（如 `2026-09-25T10:00:00`）。
- 日期（仅日期）为 `yyyy-MM-dd`。
- 内容包任务模板中的 `endTime` 例外，要求 `yyyy-MM-dd HH:mm:ss`（带空格）。

### 1.4 错误码速查

| code | 含义 | 常见触发场景 |
| --- | --- | --- |
| 0 | 成功 | — |
| 1000 | 系统繁忙 | 服务器内部错误 |
| 1001 | 请求参数错误 | 校验失败、枚举不合法、文件超限等 |
| 1002 | 数据不存在 | 账户/素材/收款单不存在 |
| 1003 | 数据已存在 | 幂等键冲突、重复推优 |
| 2001 | 未登录 | token 缺失/过期 |
| 2003 | 没有操作权限 | 角色不满足 |
| 3001 | 额度不足 | 下发/扣费时余额不够 |
| 2004 | 账号未入店 | STAFF 未加入门店就上传/推优 |

---

## 2. 额度计费域（com.xiaoa.quota）

### 2.1 业务模型（前端需要理解的几点）

1. **两级账户**：每个租户一个 `TENANT` 级"额度池"，每个门店一个 `STORE` 级"门店账户"。
   - 充值（credit）把额度充进指定账户；
   - 下发（allocate）从租户池扣出、充入门店账户（一次请求产生 `ALLOCATE_OUT` + `ALLOCATE_IN` 两条流水）；
   - AI 生成按门店账户扣费（`CONSUME`），失败退款（`REFUND`）。这两条由后端在 AI 流程内自动完成，前端无需调用。
2. **流水类型 bizType（枚举，筛选流水时用）**：

| bizType | 方向 | 含义 |
| --- | --- | --- |
| `CREDIT` | + | 充值入账 |
| `ALLOCATE_OUT` | − | 从租户池下发出去 |
| `ALLOCATE_IN` | + | 门店账户收到下发 |
| `CONSUME` | − | AI 生成扣费 |
| `REFUND` | + | AI 生成失败退款 |
| `RECALL` | + | 回收（预留） |

3. **幂等**：充值/下发接口的 `bizId` 是幂等键（≤64 字符）。同一个 `bizId` 重复提交不会重复加钱，而是返回首次结果；若同一 `bizId` 被用于金额/账户不同的请求，返回 `1003`。
4. **余额为整数**（单位：积分/点数），前端直接展示数字即可。

### 2.2 接口总览

| # | 方法 | 路径 | 用途 | 角色 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/platform/auth/login` | 平台账号登录 | 匿名 |
| 2 | POST | `/platform/payment/register` | 登记收款单 | PLATFORM_FINANCE |
| 3 | POST | `/platform/payment/{id}/confirm` | 确认收款并充值 | PLATFORM_FINANCE |
| 4 | POST | `/platform/payment/{id}/cancel` | 撤销收款单 | PLATFORM_FINANCE |
| 5 | POST | `/api/admin/quota/credit` | 账户充值 | HQ_ADMIN |
| 6 | POST | `/api/admin/quota/allocate` | 池→门店下发 | HQ_ADMIN |
| 7 | GET | `/api/admin/quota/store/{storeId}` | 查门店账户余额 | HQ_ADMIN / REGION_ADMIN / VIEWER |
| 8 | GET | `/api/admin/quota/account/{accountId}/flows` | 账户流水分页 | HQ_ADMIN / REGION_ADMIN / VIEWER |
| 9 | GET | `/api/quota/my` | 我的额度概览 | 任意已登录企业账号 |

### 2.3 平台端

#### 2.3.1 平台登录

`POST /platform/auth/login`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| loginName | string | 是 | 登录名 |
| credential | string | 是 | 密码（明文提交，服务端 SHA-256 比对） |

```json
{ "loginName": "platform-finance", "credential": "platform-finance-dev" }
```

响应 `data`：

```json
{ "token": "xxx", "platformUserId": 2, "role": "PLATFORM_FINANCE" }
```

> 开发环境种子账号（V6 迁移）：
> - `platform-ops` / `platform-ops-dev`（平台运营 PLATFORM_OPS）
> - `platform-finance` / `platform-finance-dev`（平台财务 PLATFORM_FINANCE）
>
> 登录失败（账号不存在/禁用/密码错）返回 `2001`。后续平台接口同样用 `Authorization: Bearer {token}`。

#### 2.3.2 登记收款单

`POST /platform/payment/register`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| tenantId | number | 是 | 收款对象租户 |
| amount | number | 是 | 金额（≥1 的整数） |
| channel | string | 是 | 收款渠道（如 `BANK` / `WECHAT`，前端自定义字符串） |
| orderNo | string | 否 | 外部单号；不传自动生成 `PAY-{uuid}`；重复 orderNo 幂等返回已有单 |
| voucherUrl | string | 否 | 收款凭证地址 |
| invoiceNo | string | 否 | 发票号 |

响应 `data` 为收款单对象（结构见 2.3.5），新建单 `status = "PENDING"`。

#### 2.3.3 确认收款

`POST /platform/payment/{id}/confirm`

- 请求体可选：`{ "invoiceNo": "..." }`，也可以完全不传 body。
- 仅 `PENDING` 状态可确认；确认后**同事务给该租户额度池充值**（幂等键 `pay:{orderId}`）。
- 对已 `SETTLED` 的单重复调用：幂等成功，直接返回当前单。
- 其他状态返回 `1001`（"当前收款单状态不可确认"）。

#### 2.3.4 撤销收款单

`POST /platform/payment/{id}/cancel`

- 仅 `PENDING` 可撤销，撤销后 `status = "CANCELED"`，**不产生任何额度变动**。
- 已确认（SETTLED）的单不可撤销，返回 `1001`；需要退额度请走负向充值或后续退款流程。

#### 2.3.5 收款单对象（PaymentOrder）字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 收款单 ID |
| tenantId | number | 收款对象租户 |
| orderNo | string | 单号 |
| amount | number | 金额 |
| channel | string | 渠道 |
| status | string | `PENDING` 待确认 / `SETTLED` 已确认入账 / `CANCELED` 已撤销 |
| voucherUrl | string | 收款凭证 |
| invoiceNo | string | 发票号 |
| confirmBy | number | 复核人（平台用户 ID） |
| confirmedAt | string | 复核时间 |
| paidAt / callbackPayload / createdAt / updatedAt | — | 时间与扩展字段，一期可能为 null |

状态机：`PENDING → SETTLED`（确认）、`PENDING → CANCELED`（撤销）。

### 2.4 企业管理端

#### 2.4.1 账户充值

`POST /api/admin/quota/credit`（仅 HQ_ADMIN，2003 拒绝其他角色）

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| accountId | number | 是 | 额度账户 ID（租户池或门店账户均可，须属于本租户） |
| amount | number | 是 | 充值额度（≥1） |
| bizId | string | 否 | 幂等键（≤64 字符），建议前端用 `recharge:{订单号}` 之类 |
| remark | string | 否 | 备注 |

响应 `data` 为充值后的账户对象（QuotaAccount）。

QuotaAccount 结构：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 账户 ID（后续查流水用它） |
| tenantId | number | 租户 |
| level | string | `TENANT` 租户池 / `STORE` 门店账户 |
| ownerId | number | 池=租户 ID；门店=门店 ID |
| balance | number | 当前余额 |
| version | number | 乐观锁版本（前端忽略） |
| createdAt / updatedAt | string | 时间 |

错误：账户不存在 `1002`；amount<1 `1001`；bizId 冲突 `1003`。

#### 2.4.2 池→门店下发

`POST /api/admin/quota/allocate`（仅 HQ_ADMIN）

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| storeId | number | 是 | 目标门店 |
| amount | number | 是 | 下发额度（≥1） |
| bizId | string | 否 | 幂等基键（实际落库为 `{bizId}:out` / `{bizId}:in` 两条） |
| remark | string | 否 | 备注 |

- 成功响应 `data: null`（`{"code":0,"msg":"success"}`）。
- 租户池余额不足时返回 `3001 额度不足`。
- 门店账户不存在会自动创建（余额 0）。

#### 2.4.3 查门店账户余额

`GET /api/admin/quota/store/{storeId}`（HQ_ADMIN / REGION_ADMIN / VIEWER）

响应 `data` 为 QuotaAccount（门店账户不存在会自动创建，余额 0）。

#### 2.4.4 账户流水分页

`GET /api/admin/quota/account/{accountId}/flows`（HQ_ADMIN / REGION_ADMIN / VIEWER）

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| bizType | string | 否 | 枚举：CREDIT / ALLOCATE_OUT / ALLOCATE_IN / CONSUME / REFUND / RECALL（不区分大小写；非法值返回 1001） |
| from | string | 否 | 起始时间 `yyyy-MM-ddTHH:mm:ss` |
| to | string | 否 | 结束时间 |
| pageNo | number | 否 | 默认 1 |
| pageSize | number | 否 | 默认 20，上限 200（超出自动截断） |

响应 `data.list` 元素（QuotaFlow）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 流水 ID |
| tenantId / accountId | number | 归属 |
| bizType | string | 见 2.1 枚举 |
| bizId | string | 业务标识（如 `gen:{workId}`、`refund:{taskId}`、`pay:{orderId}`） |
| idempotentKey | string | 幂等键 |
| amount | number | 正=入账，负=出账 |
| balanceAfter | number | 该笔后的余额 |
| remark | string | 备注 |
| createdAt | string | 时间 |

> 流水只增不改，前端列表可直接用 `amount` 正负渲染收支方向。

### 2.5 门店端 / 个人额度

`GET /api/quota/my`（任意已登录企业账号）

- 角色判定：`OWNER` / `STAFF` → 返回**本店账户**；`HQ_ADMIN` / `REGION_ADMIN` / `VIEWER` → 返回**租户池**。
- 平台账号（无租户）调用返回 `2003`。

响应 `data`：

```json
{
  "account": { "id": 3, "level": "STORE", "ownerId": 5, "balance": 880, "...": "..." },
  "recentFlows": [ { "bizType": "CONSUME", "amount": -20, "balanceAfter": 880, "...": "..." } ]
}
```

`recentFlows` 固定返回最近 10 条流水（不分页参数）。

---

## 3. 资产配置域（com.xiaoa.asset）

### 3.1 业务模型（前端需要理解的几点）

1. **三层 scope**：`PLATFORM`（行业包，租户侧只读）、`BRAND`（品牌层，总部上传）、`STORE`（本店层，门店上传）。
2. **status 状态机**：

```
APPROVED ──(门店推优)──▶ PENDING_REVIEW ──(审核通过)──▶ APPROVED（同时 scope: STORE → BRAND）
                                        └─(审核驳回)──▶ REJECTED（终态：素材不可见、不可再推优，需重新上传）
APPROVED/PENDING_REVIEW/REJECTED ──(软删)──▶ DELETED
```

3. **上传即用**：门店/品牌上传成功即 `APPROVED`，无审核环节；只有"推优"才触发审核。
4. **文件限制**：仅 `jpg / jpeg / png`（≤10M）和 `mp4`（≤100M）；类型由扩展名判断，服务端自动归为 `type = IMAGE / VIDEO`。超限或类型不符返回 `1001`，不落库不留文件。
5. **文件地址**：`content` 字段当前返回 `local://asset/...` 协议的本地存储占位地址（后续接 OSS）。**前端现阶段不能直接把 `content` 当 http URL 加载图片/视频**，预览能力需等文件下载/访问接口上线，或后端切换 OSS 后自然可用。
6. **可见性查询**（`GET /api/assets`）= `PLATFORM`（租户挂载的）∪ `BRAND`（本租户）∪ `STORE`（本店/本租户），只返回 `APPROVED`，最多 200 条，不分页。

### 3.2 接口总览

| # | 方法 | 路径 | 用途 | 角色 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/api/assets` | 本店素材上传 | STAFF / OWNER（已入店） |
| 2 | GET | `/api/assets` | 三层可见性素材列表 | 任意企业账号 |
| 3 | POST | `/api/assets/recommend` | 推优本店素材 | 已入店账号 |
| 4 | POST | `/api/admin/assets` | 品牌素材上传 | HQ_ADMIN |
| 5 | GET | `/api/admin/assets` | 素材分页（含推优 Tab） | HQ_ADMIN / REGION_ADMIN / VIEWER |
| 6 | PUT | `/api/admin/assets/{id}` | 编辑名称/分类 | HQ_ADMIN（品牌层）/ OWNER（本店层） |
| 7 | DELETE | `/api/admin/assets/{id}` | 软删 | 同上 |
| 8 | PUT | `/api/admin/assets/{id}/review` | 推优审核 | HQ_ADMIN |
| 9 | POST | `/api/admin/content-packages` | 创建内容包 | HQ_ADMIN |
| 10 | GET | `/api/admin/content-packages` | 内容包分页 | HQ_ADMIN / REGION_ADMIN / VIEWER |
| 11 | DELETE | `/api/admin/content-packages/{id}` | 撤销内容包 | HQ_ADMIN |

### 3.3 门店端素材

#### 3.3.1 上传本店素材

`POST /api/assets`（`multipart/form-data`）

| 表单字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | jpg/jpeg/png ≤10M，mp4 ≤100M |
| name | string | 否 | 素材名，缺省取文件名，再缺省"未命名素材" |
| category | string | 否 | 分类标签（≤50 字符，空白会归一化为 null） |

响应 `data` 为素材对象（Asset，见 3.3.5）。未入店账号返回 `2003`。

#### 3.3.2 可见素材列表（创作选素材用）

`GET /api/assets?category={category}`

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| category | 否 | 分类过滤 |

- `STAFF` / `OWNER`（已入店）：PLATFORM + 本租户 BRAND + **本店** STORE。
- `HQ_ADMIN` / `REGION_ADMIN` / `VIEWER`：品牌视角，只有 PLATFORM + BRAND，**不包含任何门店层素材**（门店层按 storeId 精确匹配，管理层无 storeId 故不命中）。
- 响应 `data` 为 `Asset[]`（非分页，最多 200 条），仅 `APPROVED`。

#### 3.3.3 推优素材

`POST /api/assets/recommend`

```json
{ "assetId": 12 }
```

规则：

- 仅 `SCOPE_STORE` 的素材可推优（平台/品牌素材报 `1001`）；
- 素材必须对当前账号可见（即 `APPROVED`；`REJECTED` / `DELETED` 不可见，报 `1002`，驳回素材需重新上传后再推优）；
- 已在推优流程中（`PENDING_REVIEW`）重复推优返回 `1003` "已在推优流程中"；
- 推优后素材离开可见列表（status 不再是 APPROVED），审核通过后以品牌层身份（BRAND + APPROVED）重新可见；
- 未入店账号返回 `2003`。

成功响应 `data: null`。

#### 3.3.5 素材对象（Asset）字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 素材 ID |
| tenantId | number | 租户 |
| packageId | number | 关联行业包（平台素材才有） |
| storeId | number | 所属门店（STORE 层素材才有） |
| scope | string | `PLATFORM` / `BRAND` / `STORE` |
| type | string | `IMAGE` / `VIDEO` / `SCRIPT`（话术类） |
| name | string | 素材名 |
| content | string | 文件地址（当前为 `local://` 占位）或话术内容 |
| version | string | 版本号，一期固定 `v1` |
| category | string | 分类 |
| status | string | `APPROVED` / `PENDING_REVIEW` / `REJECTED` / `DELETED` |
| uploaderId | number | 上传人用户 ID（驳回通知发给 TA） |
| recommendStatus | number | 旧推优字段，仅历史兼容，新流程忽略 |
| createdAt / updatedAt | string | 时间 |

### 3.4 管理端素材

#### 3.4.1 品牌素材上传

`POST /api/admin/assets`（仅 HQ_ADMIN，`multipart/form-data`）

表单字段同 3.3.1（file / name / category）。上传成功即 `scope=BRAND`、`status=APPROVED`，全租户可见。

#### 3.4.2 素材分页列表

`GET /api/admin/assets`（HQ_ADMIN / REGION_ADMIN / VIEWER）

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| scope | 否 | `PLATFORM` / `BRAND` / `STORE` |
| status | 否 | `APPROVED` / `PENDING_REVIEW` / `REJECTED` / `DELETED`；**`status=PENDING_REVIEW` 即推优审核 Tab** |
| category | 否 | 分类过滤 |
| pageNo | 否 | 默认 1 |
| pageSize | 否 | 默认 20，上限 100 |

响应 `data` 为 PageResult，元素结构见 3.3.5。

#### 3.4.3 编辑名称/分类

`PUT /api/admin/assets/{id}`

```json
{ "name": "开业海报", "category": "活动物料" }
```

- `name`（≤128）、`category`（≤50）至少传一个，否则 `1001`；两者都是可选字段，只更新传了的。
- 权限矩阵：`PLATFORM` 素材任何人不可改（`2003`）；`BRAND` 仅 HQ_ADMIN；`STORE` 仅本店 OWNER。REGION_ADMIN/VIEWER 无编辑权。

#### 3.4.4 软删素材

`DELETE /api/admin/assets/{id}`

- 权限同 3.4.3。删除为软删（`status=DELETED`），已生成作品里的 URL 快照不受影响，前端列表按 status 过滤即可。

#### 3.4.5 推优审核

`PUT /api/admin/assets/{id}/review`（仅 HQ_ADMIN）

```json
{ "pass": true, "category": "品牌精选" }
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pass | boolean | 是 | 审核结论 |
| category | string | 否 | 通过时可改分类升入品牌层；不传保持原分类 |

规则：

- 仅 `PENDING_REVIEW` 可审核，其他状态 `1001`；
- `pass=true`：素材 `scope: STORE→BRAND`、`status→APPROVED`（全租户可见）；
- `pass=false`：`status→REJECTED`，并给上传人发一条站内信（`type=ASSET_RECOMMEND`，标题"素材推优被驳回"）——前端可在消息中心展示；
- `PLATFORM` 素材无需审核（`1001`）。

### 3.5 内容包（营销日历定时下发任务）

#### 3.5.1 创建内容包

`POST /api/admin/content-packages`（仅 HQ_ADMIN）

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 内容包名称（≤128） |
| calendarDate | string | 是 | 营销日 `yyyy-MM-dd`，**早于今天直接拒绝（1001）** |
| publishAt | string | 是 | 下发时刻 `yyyy-MM-ddTHH:mm:ss` |
| copyDirection | string | 否 | 推广方向（≤2000），会随任务带给门店 |
| taskTemplate | object | 是 | 任务模板，结构见下 |

`taskTemplate` 结构与校验规则（创建时强校验）：

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| title | string | 否 | 任务标题，缺省用内容包名称 |
| actionType | number | 是 | `1` 固定动作 / `2` 指定内容 |
| platform | string | 是 | 平台（如 `douyin`，非空字符串） |
| frequency | number | 是 | `1` 每日 / `2` 每周 / `3` 每月 |
| judgeType | number | 是 | `1` 直接完成 / `2` 需截图凭证 |
| endTime | string | 是 | 任务截止 `yyyy-MM-dd HH:mm:ss`（注意空格分隔） |
| targetScope | number | 否 | `1` 全员（缺省）/ `2` 区域 / `3` 门店 / `4` 员工 |
| targetIds | number[] | 条件 | `targetScope≠1` 时必传且非空 |

请求示例：

```json
{
  "name": "国庆开门红",
  "calendarDate": "2026-10-01",
  "publishAt": "2026-10-01T09:00:00",
  "copyDirection": "突出国庆促销与到店礼",
  "taskTemplate": {
    "title": "发布国庆探店视频",
    "actionType": 2,
    "platform": "douyin",
    "frequency": 1,
    "judgeType": 2,
    "endTime": "2026-10-07 23:59:59",
    "targetScope": 3,
    "targetIds": [12, 15]
  }
}
```

响应 `data` 为内容包对象，新建 `status=1`。

#### 3.5.2 内容包对象（ContentPackage）字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 内容包 ID |
| tenantId | number | 租户 |
| name | string | 名称 |
| calendarDate | string | 营销日 `yyyy-MM-dd` |
| publishAt | string | 下发时刻 |
| copyDirection | string | 推广方向 |
| taskTemplate | string | 任务模板 **JSON 字符串**（前端需 `JSON.parse` 后渲染） |
| status | number | `1` ACTIVE 待下发 / `2` DISPATCHED 已下发 / `3` CANCELED 已撤销 |
| sourceTaskId | number | 下发生成的任务 ID（未下发为 null） |
| lastError | string | 下发失败原因（有值说明需人工补建） |
| createdBy | number | 创建人 |
| createdAt / updatedAt | string | 时间 |

#### 3.5.3 内容包分页

`GET /api/admin/content-packages`（HQ_ADMIN / REGION_ADMIN / VIEWER）

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| status | 否 | 1 / 2 / 3 |
| pageNo | 否 | 默认 1 |
| pageSize | 否 | 默认 20，上限 100 |

#### 3.5.4 撤销内容包

`DELETE /api/admin/content-packages/{id}`（仅 HQ_ADMIN）

- 仅 `status=1（ACTIVE）` 可撤销，成功后 `status=3`；
- 已下发（`DISPATCHED`）不可撤销（返回 `1001`），需要停任务请走任务管理侧的任务停用接口；
- 重复撤销同样返回 `1001`。

#### 3.5.5 下发机制（前端只需了解）

- 定时任务每轮扫描 `publishAt` 已到期且 `status=1` 的内容包（每批最多 50 个），先抢占状态再创建任务，**保证不重发**；
- 下发成功后 `status=2`、`sourceTaskId` 回填为生成的任务 ID，门店端在任务列表正常看到该任务；
- 极端情况下建任务失败：状态仍是 2 但 `sourceTaskId` 为空并记录 `lastError`，管理端列表建议对 `status=2 && !sourceTaskId` 的行展示"下发异常"标记。

---

## 4. 前端对接注意事项汇总

1. **幂等键**：充值/下发务必在重试场景传固定 `bizId`（如用前端生成订单号），避免网络重试造成重复入账；不传也能成功（后端自动生成），但重试会重复加钱。
2. **分页上限**：额度流水 pageSize≤200；素材/内容包 pageSize≤100，超出会被截断而不是报错。
3. **文件上传**：用 `multipart/form-data`，字段名必须是 `file`；上传前前端最好先做类型/大小预校验，减少无效流量。
4. **`content` 地址**：当前是 `local://` 占位协议，图片/视频预览暂不可用；接入 OSS 后无需改字段，直接变成可访问 URL。
5. **角色控制**：`/api/admin/**`、`/api/quota/my` 等接口的角色校验在服务端完成，前端只做菜单/按钮显隐即可，错误以返回码为准（2003）。
6. **时间格式**：注意内容包 `taskTemplate.endTime` 是 `yyyy-MM-dd HH:mm:ss`（空格），其余时间是 ISO `T` 分隔。
7. **推优闭环**：门店端推优后素材进入 `PENDING_REVIEW`，在门店可见列表中仍显示（状态仍可见），但不能再推优；驳回后前端可从消息中心（`ASSET_RECOMMEND` 类型）提示用户。
