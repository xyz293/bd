# AI 创作域 & 企业管理域 · 前端接口文档

> 对应后端模块：`com.xiaoa.ai`（AI 生成 / 作品 / 提示词模板）、`com.xiaoa.admin`（企业管理端：看板 / 审核 / 合规 / 导出 / 门店 / 风格 / 成员）。
> 通用约定（认证头、`{code,msg,data}` 包装、分页结构、时间格式、错误码表）与 [quota-asset-api.md](quota-asset-api.md) 完全一致，本文不再重复。

---

## 1. AI 创作域（com.xiaoa.ai）

### 1.1 业务模型（前端必须理解的几点）

1. **作品（Work）有两个独立状态维度**：
   - `status`（生成维度）：`PENDING` 生成中 → `SUCCESS` 成功 / `FAILED` 失败（`failReason` 给出原因）。
   - `publishStatus`（发布维度）：仅在生成成功后初始化并单向流转，见 1.2 状态机。
2. **生成是异步的**：`POST /api/work/generate` 同步完成"建作品 + 扣费 + 提交任务"，返回时 `status=PENDING`；**前端需要轮询 `GET /api/work/{id}`** 直到 `status=SUCCESS/FAILED`。图片一般数秒内完成；视频由后端定时轮询模型（约 30 秒一轮）。
3. **计费**：图片 `1` 点、视频 `5` 点（服务端配置 `xiaoa.ai.cost.image/video`），从**门店账户**扣；生成失败自动按原价退回（前端无需处理退款，流水里会出现 `REFUND` 类型）。
4. **配套文案**：生成成功后服务端异步补写 `caption`，可能晚于 `SUCCESS` 到达；失败则 `caption` 留空，员工可手动调用改文案接口补写。
5. **引用素材**：`assetIds` 中的每个素材必须对当前用户三层可见（`APPROVED`），否则整次生成报 `1001`；校验通过后以 ID 快照记入作品。
6. **文件地址**：`contentUrl` / `resultUrl` 当前为 `local://` 占位协议（待接 OSS），前端暂不能直接当 http URL 预览。

### 1.2 发布维度状态机（publishStatus）

```
NONE（初始：生成中/失败/历史数据）
  └─(生成成功，按门店审核开关初始化)──▶ DRAFT（开关关：可直接发布）
                                    └─▶ PENDING_AUDIT（开关开：进审核队列）
DRAFT ──(发布)──▶ PUBLISHED
PENDING_AUDIT ──(审核通过)──▶ APPROVED ──(发布)──▶ PUBLISHED
             └─(审核驳回)──▶ REJECTED ──(员工改文案自动重提审)──▶ PENDING_AUDIT
```

- 审核开关是**组织级覆盖品牌级**：门店未配置时回退租户级（`orgId=0`）配置。
- `PUBLISHED` 由发布上报接口触发，接口明细见 1.7（该接口物理上属于任务域 `com.xiaoa.task`）。

### 1.3 接口总览

| # | 方法 | 路径 | 用途 | 角色 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/api/work/generate` | 发起 AI 生成 | 任意企业账号 |
| 2 | GET | `/api/work/{id}` | 作品详情（轮询用） | 仅作品本人 |
| 3 | PUT | `/api/work/{id}/caption` | 修改文案 | 仅作品本人 |
| 4 | POST | `/api/work/{id}/regenerate` | 重新生成媒体 | 仅作品本人 |
| 5 | GET | `/api/admin/media-task/list` | 生成任务列表（运维视角） | HQ_ADMIN / REGION_ADMIN / VIEWER |
| 6 | GET | `/api/admin/prompt-template` | 提示词模板列表 | HQ_ADMIN / REGION_ADMIN / VIEWER |
| 7 | PUT | `/api/admin/prompt-template` | 保存模板（新版本） | HQ_ADMIN |
| 8 | PUT | `/api/admin/prompt-template/{id}/rollback` | 回滚模板版本 | HQ_ADMIN |

### 1.4 作品接口

#### 1.4.1 发起生成

`POST /api/work/generate`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| type | string | 是 | `IMAGE` / `VIDEO`（大小写不敏感，其他值 1001） |
| platform | string | 是 | 发布平台（≤32 字符），如 `douyin` |
| styleId | number | 是 | 风格 ID，须可见（本租户或平台内置且启用），否则 `1002` |
| productName | string | 否 | 产品名（≤2000） |
| userInput | string | 否 | 用户补充说明（≤4000） |
| refImageUrls | string[] | 否 | 参考图 URL 列表（最多 10 条） |
| assetIds | number[] | 否 | 引用素材 ID（最多 20 个，须三层可见） |

请求示例：

```json
{
  "type": "IMAGE",
  "platform": "douyin",
  "styleId": 3,
  "productName": "秋季新品奶茶",
  "userInput": "突出温暖色调与手作感",
  "assetIds": [12, 15]
}
```

响应 `data` 为 Work 对象（见 1.6），此刻 `status="PENDING"`、`publishStatus="NONE"`。

可能的错误：类型不合法 / 素材不可见 `1001`；风格不存在或停用 `1002`；提示词命中 2 级违规词 `4001`（合规词见第 2 章）；模板未配置 `1000`；门店额度不足 `3001`。

#### 1.4.2 作品详情（轮询）

`GET /api/work/{id}`

- **仅作品本人可调**（管理员查看走审核列表），他人作品返回 `2003`。
- 响应 `data` 为 Work；建议轮询间隔 2~3 秒，直到 `status` 变为 `SUCCESS` / `FAILED`。
- `auditOpinion` 字段：有审核记录时返回**最新一次审核意见**，无记录为 `null`（驳回时前端展示它）。

#### 1.4.3 修改文案

`PUT /api/work/{id}/caption`（仅本人）

```json
{ "caption": "秋日第一杯，手作的温度☕" }
```

- `caption` 必填（≤2000）。
- 仅 `publishStatus ∈ {DRAFT, APPROVED, REJECTED}` 可改；其他状态 `1001`。
- **`REJECTED` 状态改稿成功后自动回到 `PENDING_AUDIT`（重新送审）**，前端改稿后应刷新审核状态提示。

#### 1.4.4 重新生成

`POST /api/work/{id}/regenerate`（仅本人）

- 使用原 prompt / 风格 / 参考图**原样重新生成**，按上次任务 `cost` 全价扣费（余额不足 `3001`）。
- 仅 `publishStatus ∈ {DRAFT, APPROVED, REJECTED}` 可用；作品 `status` 重置为 `PENDING`，继续轮询详情即可。
- 旧成品保留在生成任务历史中，不会丢失。
- 作品缺少生成任务时报 `1002`；状态并发变更报 `1001`。

### 1.5 管理端：生成任务与提示词模板

#### 1.5.1 生成任务列表

`GET /api/admin/media-task/list?limit=50`（HQ_ADMIN / REGION_ADMIN / VIEWER）

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| limit | 否 | 返回条数，默认 50，1~200 之外自动截断 |

响应 `data` 为 `MediaTask[]`（字段见 1.6），按时间倒序，用于排查生成任务状态/费用/失败原因。

#### 1.5.2 提示词模板

- `GET /api/admin/prompt-template`（读）：返回**全部版本**（含已停用的历史版本），按 `scene, version` 倒序；前端渲染"当前生效"时取 `status=1` 的记录（每个 `scene` 只有一条生效）。
- `PUT /api/admin/prompt-template`（仅 HQ_ADMIN）：

```json
{ "id": null, "scene": "IMAGE", "template": "为{{platform}}生成{{style}}风格图片，主题：{{productName}}。{{userInput}}" }
```

  - `scene` 仅支持 `IMAGE` / `VIDEO`（其他值 `1001`）；
  - `id` 传 `null` 即新增：同一 `scene` 旧版本全部停用，新版本号 = 历史最大版本 + 1；
  - 模板变量占位符：`{{platform}}`、`{{style}}`、`{{productName}}`、`{{userInput}}`。
- `PUT /api/admin/prompt-template/{id}/rollback`（仅 HQ_ADMIN）：将指定历史版本重新启用，该 `scene` 当前生效版本停用；模板不存在 `1002`。

### 1.6 模型字段表

**Work（作品）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 作品 ID |
| tenantId / userId | number | 归属租户 / 创建人 |
| type | string | `IMAGE` / `VIDEO` |
| mediaTaskId | number | 当前关联生成任务 ID |
| platform | string | 平台 |
| styleId / styleName | number / string | 风格 ID / 名称快照 |
| userInput | string | 用户输入 |
| refImageUrls | string | 参考图 URL，**换行符分隔的字符串**（回显时需 split） |
| promptTemplateId / promptTemplateVersion | number / number | 使用的模板及版本 |
| contentUrl | string | 成品地址（当前 `local://` 占位） |
| copywriting | string | 生成文案（预留） |
| status | string | `PENDING` / `SUCCESS` / `FAILED` |
| failReason | string | 失败原因（status=FAILED 时） |
| publishStatus | string | 见 1.2 状态机 |
| caption | string | 配套文案（异步补写，可手动改） |
| sourceAssetIds | string | 引用素材 ID 快照，**JSON 数组字符串**（如 `"[12,15]"`） |
| auditOpinion | string | 最新审核意见（服务层填充，可能 null） |
| createdAt / updatedAt | string | 时间 |

**MediaTask（生成任务，管理端）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id / tenantId / userId / workId / storeId | number | 归属与关联 |
| type / scene | string | `IMAGE` / `VIDEO` |
| status | string | `PENDING → PROCESSING → SUCCESS/FAILED`；视频额外经过 `SUBMITTED ⇄ POLLING` |
| prompt | string | 实际送模型的提示词 |
| providerTaskId | string | 模型侧任务 ID（视频） |
| resultUrl | string | 成品地址 |
| cost | number | 本任务扣费点数 |
| errorMessage | string | 失败原因 |
| refundStatus | number | `0` 未退款 / `1` 已自动退款 |
| startedAt / finishedAt | string | 开始 / 结束时间 |

**PromptTemplate（提示词模板）**：`id`、`scene`、`template`、`version`、`status`（1 启用）、`createdAt`、`updatedAt`。

### 1.7 发布上报（作品闭环最后一步）

`POST /api/publish-record`（仅作品本人；接口在任务域 `com.xiaoa.task`）

员工把生成好的作品发布到平台后上报，可选关联营销任务；服务端一次完成：写发布记录 + 完成任务记录 + 作品 `publishStatus` 流转到 `PUBLISHED`。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| workId | number | 是 | 作品 ID，必须是本人的（否则 `2003`） |
| taskId | number | 否 | 关联的营销任务；传了则触发任务校验与自动完成 |
| platform | string | 是 | 发布平台，与作品/任务平台一致 |
| proofUrl | string | 否 | 截图凭证；**关联 `judgeType=2`（需凭证）的任务时必填**，否则 `1001` |

```json
{ "workId": 101, "taskId": 5, "platform": "douyin", "proofUrl": "https://..." }
```

校验规则：

- 作品 `publishStatus` 必须是 `DRAFT` / `APPROVED`（已 `PUBLISHED` 允许重复上报多条记录，但状态不再流转），否则 `2003` "作品未通过审核，不可发布"；
- 关联任务时校验：任务启用中、在时间窗内、`platform` 与任务要求一致、当前用户在任务目标范围内（不满足分别报 `2003` / `1001`）。

响应 `data` 为发布记录：

| 字段 | 说明 |
| --- | --- |
| id | 发布记录 ID |
| workId / userId / taskId | 作品 / 上报人 / 关联任务（可为 null） |
| platform / proofUrl | 平台与凭证 |
| createdAt | 上报时间 |

> 关联任务时服务端会自动把对应周期的任务记录置为完成（`judgeType=2` 记录凭证），前端无需再调任务完成接口。

---

## 2. 企业管理域（com.xiaoa.admin）

### 2.1 数据范围模型（多个接口共用）

| 角色 | 数据范围 |
| --- | --- |
| HQ_ADMIN / VIEWER | 全租户 |
| REGION_ADMIN | 仅本区域（parentId = 自己 orgId 的门店） |
| OWNER / STAFF | 仅本店 |

- 写操作（创建/修改/删除）一般要求 `HQ_ADMIN` 或 `REGION_ADMIN`（`requiredWrite`），个别接口仅 `HQ_ADMIN`，下文逐个标注。
- VIEWER 只有读权，所有写接口返回 `2003`。

### 2.2 接口总览

| # | 方法 | 路径 | 用途 | 角色 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/admin/dashboard/overview` | 经营概览 | 读角色 |
| 2 | GET | `/api/admin/dashboard/trend` | 近 7 天趋势 | 读角色 |
| 3 | GET | `/api/admin/audit/works` | 待审作品列表 | HQ_ADMIN / REGION_ADMIN / OWNER |
| 4 | POST | `/api/admin/audit/works/{id}/approve` | 审核通过 | 同上 |
| 5 | POST | `/api/admin/audit/works/{id}/reject` | 审核驳回 | 同上 |
| 6 | GET | `/api/admin/compliance/words` | 合规词列表 | 读角色 |
| 7 | PUT | `/api/admin/compliance/words` | 新增/更新合规词 | HQ_ADMIN |
| 8 | DELETE | `/api/admin/compliance/words/{id}` | 禁用合规词 | HQ_ADMIN |
| 9 | GET | `/api/admin/compliance/audit-config/{orgId}` | 查审核开关 | 读角色 |
| 10 | PUT | `/api/admin/compliance/audit-config/{orgId}` | 改审核开关 | HQ_ADMIN 或本组织管理员 |
| 11 | POST | `/api/admin/exports` | 创建导出任务 | HQ_ADMIN / REGION_ADMIN |
| 12 | GET | `/api/admin/exports` | 导出任务列表 | 读角色 |
| 13 | GET | `/api/admin/exports/{id}` | 导出任务详情 | 读角色 |
| 14 | GET | `/api/admin/stores` | 门店列表 | 读角色（按数据范围过滤） |
| 15 | POST | `/api/admin/stores` | 创建门店 | HQ_ADMIN / REGION_ADMIN |
| 16 | PATCH | `/api/admin/stores/{storeId}/parent` | 调整门店归属 | HQ_ADMIN / REGION_ADMIN |
| 17 | GET | `/api/admin/styles` | 风格列表 | 读角色 |
| 18 | POST | `/api/admin/styles` | 创建风格 | HQ_ADMIN |
| 19 | PUT | `/api/admin/styles/{id}` | 更新风格 | HQ_ADMIN |
| 20 | DELETE | `/api/admin/styles/{id}` | 删除风格 | HQ_ADMIN |
| 21 | GET | `/api/admin/members` | 成员分页查询 | 读角色 |

### 2.3 经营看板

#### 2.3.1 概览

`GET /api/admin/dashboard/overview`

响应 `data`（REGION_ADMIN 自动只统计本区域）：

| 字段 | 说明 |
| --- | --- |
| activeStores | 近 7 天有产出作品的门店数 |
| weeklyWorks | 本周（周一起）新增作品数 |
| weeklyPublishes | 本周发布数 |
| totalQuota | 当前额度余额合计（本租户所有账户） |
| usedQuota | 本周消耗额度 |

> 服务端对看板数据做了缓存，短时间内的数据变动可能不立即反映，前端无需做特殊处理。

#### 2.3.2 趋势

`GET /api/admin/dashboard/trend`

- 固定返回**近 7 天（含今天）共 7 个点**，无数据的日期补 0，前端可直接画折线图。
- 元素：`{ "date": "2026-09-25", "works": 12, "publishes": 5 }`。

### 2.4 作品审核

#### 2.4.1 待审列表

`GET /api/admin/audit/works`（HQ_ADMIN / REGION_ADMIN / OWNER；STAFF/VIEWER 返回 `2003`）

- 返回本数据范围内（OWNER 本店 / REGION 本区域 / HQ 全域）`publishStatus=PENDING_AUDIT` 的作品，**最多 100 条**，无分页参数。
- 元素为 Work 结构（见 1.6）。OWNER 也能看到并审核本店员工作品。

#### 2.4.2 通过 / 驳回

- `POST /api/admin/audit/works/{id}/approve`：无 body。`PENDING_AUDIT → APPROVED`。
- `POST /api/admin/audit/works/{id}/reject`：

```json
{ "opinion": "画面出现竞品 logo，请调整后重提" }
```

  `opinion` 必填（≤512）。`PENDING_AUDIT → REJECTED`，并给作者发站内信（`type=WORK_AUDIT`，标题"作品审核驳回"）。

公共规则：作品不存在 `1002`；不在自己数据范围 `2003`；不在 `PENDING_AUDIT` 状态 `1001`；状态变更走条件更新，并发双审只会成功一次。

### 2.5 合规词与审核开关

#### 2.5.1 合规词 CRUD

- `GET /api/admin/compliance/words`（读）：启用中的合规词列表。
- `PUT /api/admin/compliance/words`（仅 HQ_ADMIN）：

```json
{ "id": null, "word": "最便宜", "level": 1, "replacement": "高性价比" }
```

  | 字段 | 必填 | 说明 |
  | --- | --- | --- |
  | id | 否 | 空=新增；有值=更新（不存在报 `1002`） |
  | word | 是 | 违规词 |
  | level | 是 | `1` 替换 / `2` 拒绝 |
  | replacement | 否 | level=1 时的替换文本；缺失按替换为空串处理 |

  **运行时语义**（生成接口的提示词检查）：命中 `level=1` 词 → 在提示词中替换为 `replacement`，生成继续；命中 `level=2` 词 → 直接报 `4001 COMPLIANCE_REJECTED`。
- `DELETE /api/admin/compliance/words/{id}`（仅 HQ_ADMIN）：软禁用（status 置 0），列表不再返回。

#### 2.5.2 作品审核开关

- `GET /api/admin/compliance/audit-config/{orgId}`（读）：`orgId` 传门店 ID 或 `0`（品牌级默认）。门店未配置时**自动回退品牌级**返回。
- `PUT /api/admin/compliance/audit-config/{orgId}`：

```json
{ "enabled": true }
```

  - `HQ_ADMIN` 可改任意 `orgId`；其他写角色只能改自己组织（否则 `2003`）。
  - 响应 `data`：`{ "orgId": 12, "enabled": true }`。
  - 开关只影响**之后生成成功**的作品初始 `publishStatus`（开→待审，关→草稿），对已在审核队列的作品无影响。

### 2.6 导出任务（异步）

#### 2.6.1 创建导出

`POST /api/admin/exports`（HQ_ADMIN / REGION_ADMIN）

```json
{ "exportType": 1, "queryParams": "{\"from\":\"2026-09-01\",\"to\":\"2026-09-30\"}" }
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| exportType | 是 | `1` 消耗明细 / `2` 充值记录 / `3` 任务 / `4` 产出 |
| queryParams | 否 | 查询条件 JSON **字符串**，原样存储供导出实现使用 |

- 同租户**同时进行中的任务最多 3 个**，超出返回 `1003`。
- 创建即异步执行，响应 `data.status=0`（处理中）。

#### 2.6.2 查询导出

- `GET /api/admin/exports`：本租户任务列表（不分页）。
- `GET /api/admin/exports/{id}`：单个任务；不存在 `1002`。

ExportTask 字段：

| 字段 | 说明 |
| --- | --- |
| id / tenantId / createdBy | 归属 |
| exportType | 1~4 |
| queryParams | 查询条件字符串 |
| status | `0` 处理中 / `1` 已完成 / `2` 失败 |
| fileUrl | 完成后的文件地址（当前 `local://export/...` 占位，接 OSS 后可直接下载） |
| failReason | 失败原因 |
| createdAt / finishedAt | 时间 |

> 前端建议：创建成功后轮询列表或详情，`status=1` 时展示下载入口。

### 2.7 门店管理

#### 2.7.1 门店列表

`GET /api/admin/stores`（读角色；REGION_ADMIN 只看本区域门店）

响应 `data` 为 `StoreAccountSummary[]`：

| 字段 | 说明 |
| --- | --- |
| store | Org 对象：`id`、`tenantId`、`parentId`、`type`（`1` 品牌 / `2` 区域 / `3` 门店）、`name`、`createdAt`、`updatedAt` |
| memberCount | 门店成员数 |
| ownerUserId | 店长用户 ID（可能 null） |

#### 2.7.2 创建门店

`POST /api/admin/stores`（HQ_ADMIN / REGION_ADMIN）

```json
{ "name": "望京店", "parentId": 2 }
```

- `parentId` 可选：缺省挂到当前管理员所在组织；`REGION_ADMIN` 只能在**本区域**下创建（否则 `2003`）。
- 父节点必须是品牌（type=1）或区域（type=2），否则 `1001`。
- 创建成功自动初始化门店额度账户（余额 0），响应 `data` 为新门店的 StoreAccountSummary（memberCount=0）。

#### 2.7.3 调整门店归属

`PATCH /api/admin/stores/{storeId}/parent`（HQ_ADMIN / REGION_ADMIN）

```json
{ "parentId": 3 }
```

- 新父节点必须是本租户的品牌/区域节点（门店不可作为父级，否则 `1001`）。
- `REGION_ADMIN` 只能调到本区域（`2003`）。
- 成功响应 `data: null`。

### 2.8 风格管理

- `GET /api/admin/styles`（读角色）：返回 `status=1` 的可见风格 = **本租户自建 + 平台内置（tenant_id 为空）**，按 `sortNo, id` 升序。创作页"风格选择"数据源即此接口。
- `POST /api/admin/styles`（仅 HQ_ADMIN）：

```json
{ "name": "日系清新", "description": "低饱和、自然光", "exampleUrl": "", "sortNo": 10, "packageId": null }
```

  `packageId` 传值表示关联行业包（平台风格场景），一般租户自建传 `null`。
- `PUT /api/admin/styles/{id}`（仅 HQ_ADMIN）：`name` 必填，其余可选；不存在 `1002`。
- `DELETE /api/admin/styles/{id}`（仅 HQ_ADMIN）：**物理删除**，删除后创作页立即不可选（历史作品保留 styleName 快照）。

StyleOption 字段：`id`、`tenantId`（平台内置为 null）、`packageId`、`name`、`description`、`exampleUrl`、`sortNo`、`status`、`version`、`createdAt`、`updatedAt`。

### 2.9 成员查询

`GET /api/admin/members?orgId=&pageNo=1&pageSize=20`（读角色）

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| orgId | 否 | 按门店过滤；不传=全租户 |
| pageNo / pageSize | 否 | 默认 1 / 20，pageSize 上限 100 |

响应 `data.list` 元素（UserAccount）：`id`、`phone`、`openid`、`nickname`、`status`（1 正常 / 0 禁用）、`createdAt`、`updatedAt`。

---

## 3. 前端对接注意事项汇总

1. **轮询生成结果**：`generate` 返回即扣费完成，`status=PENDING`；轮询 `GET /api/work/{id}` 至 `SUCCESS/FAILED`。失败时 `failReason` 可直接展示，额度已自动退回（提示语可写"费用已退回"）。
2. **视频生成较慢**：模型任务由后端约 30 秒一轮轮询，建议对 VIDEO 类型放宽轮询节奏（5~10 秒）并显示"生成中"动效；超 30 分钟未完成会被系统判超时并退款。
3. **改稿自动重提审**：`REJECTED` 状态下改文案成功 = 自动重新送审，前端应同步把作品状态刷新为 `PENDING_AUDIT`。
4. **两个字符串字段特殊格式**：`Work.refImageUrls`（换行分隔）、`Work.sourceAssetIds`（JSON 数组字符串），渲染前需自行解析。
5. **contentUrl / resultUrl / fileUrl** 均为 `local://` 占位地址，OSS 接入前不可直接预览/下载。
6. **审核入口角色**：OWNER 也可以审核本店作品（不止管理员）；审核列表无分页，最多 100 条。
7. **审核开关的生效时机**：只影响之后生成成功的作品，切换开关不会改变存量作品状态。
8. **合规词级别**：`level=1` 静默替换、`level=2` 直接拦截（`4001`），配置界面需要把两种行为向管理员解释清楚。
9. **导出并发上限 3**：前端在创建前可先查列表中 `status=0` 的数量，避免用户撞 `1003`。
10. **门店树**：`type` 1=品牌 / 2=区域 / 3=门店；创建门店和调整归属都要求父节点是品牌或区域。
