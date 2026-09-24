# 当前业务完成情况总览

> 本文档基于当前代码、数据库迁移脚本和开发种子数据整理，区分“已经有接口和业务逻辑”“有基础实现但仍不完整”“只有表结构或演示数据”三种状态。文档不把 `prd.md` 中的规划内容当作已完成能力。

## 1. 项目当前定位

当前项目已经形成一个“多租户门店营销内容平台”的后端基础版本，核心业务链路如下：

```text
租户开通
  → 品牌/区域/门店组织
  → 用户登录与邀请码入店
  → 角色和数据范围控制
  → 管理端配置素材、风格、内容包和合规规则
  → 创建营销任务
  → AI 生成图片/视频内容
  → 员工发布作品并完成任务
  → 门店看板、任务统计、排行榜和勋章
  → 额度扣减、失败退款和流水记录
```

当前后端以 Spring Boot + MyBatis + MySQL 为主，登录态支持 Redis，AI 任务和导出任务使用异步处理。

## 2. 总体完成度判断

| 业务域 | 当前状态 | 说明 |
|---|---|---|
| 租户、组织、账号、邀请码 | 已完成基础闭环 | 可开通租户、组织管理、登录、入店、角色授权和账号状态控制 |
| 企业管理端 | 已完成主要接口 | 有首页看板、门店、成员、素材、内容包、风格、合规和导出接口 |
| 任务管理 | 已完成主要闭环 | 支持创建、编辑、启停、分发、执行、发布完成和统计 |
| AI 图片/视频生成 | 已完成开发闭环 | 支持 Demo Provider；HTTP Provider 可配置，但真实模型和生产存储仍需接入 |
| 额度管理 | 已完成核心扣减闭环 | 支持总部充值、门店分配、AI 扣费、失败退款和流水查询 |
| 素材中心 | 部分完成 | 有素材查询和推荐审核，缺少完整上传、创建、编辑和分类管理接口 |
| 内容包与风格 | 已完成基础管理 | 内容包支持创建、查询、停用；风格支持查询、创建、修改、删除 |
| 合规和审核 | 部分完成 | 有敏感词替换/拦截和审核开关，完整作品审核流程尚未形成 |
| 导出 | 已完成任务状态流程 | 当前异步任务返回本地占位地址，不是真实 CSV 生成和文件下载 |
| 支付与续费 | 部分完成 | 有支付订单表和租户续费接口，但没有完整支付下单、回调和套餐流程 |
| 平台运营/平台财务 | 尚未完成 | 当前没有独立平台账号、平台角色和 `/platform/*` 接口 |
| 微信生产登录 | 尚未完成 | 当前直接接收 `openid`，未实现微信 `code` 换取和手机号授权解密 |

## 3. 已实现的核心业务

## 3.1 租户、组织与账号业务

当前 `tenant` 模块已经形成基础身份闭环：

### 租户

- 开通租户：`POST /api/tenants/open`
- 查询租户：`GET /api/tenants/{tenantId}`
- 租户续费：`PATCH /api/tenants/{tenantId}/renew`
- 定时标记到期租户：每天执行 `TenantExpireJob`
- 登录时校验租户停用和到期状态

开通租户时会在事务中创建：

1. `tenant` 租户记录；
2. 顶级品牌组织；
3. 管理员用户；
4. `HQ_ADMIN` 角色关系。

### 组织

- 创建品牌、区域、门店节点：`POST /api/orgs`
- 查询组织树：`GET /api/orgs/tree`
- 修改组织名称：`PATCH /api/orgs/{orgId}/name`

组织模型支持品牌 → 区域 → 门店的层级关系。另有管理端门店接口：

- 查询门店和店长汇总：`GET /api/admin/stores`
- 创建门店：`POST /api/admin/stores`
- 调整门店上级：`PATCH /api/admin/stores/{storeId}/parent`

### 登录和账号生命周期

- 微信标识登录：`POST /api/auth/login`
- 邀请码入店：`POST /api/auth/join`
- 手机号接管微信账号：`POST /api/auth/takeover`
- 退出登录：`POST /api/auth/logout`
- 查询当前身份：`GET /api/auth/me`
- 创建、更新成员角色：`PUT /api/users/{userId}/roles`、`PATCH /api/users/roles/{roleId}`
- 移除成员关系：`DELETE /api/users/roles/{roleId}`
- 禁用用户账号：`PATCH /api/users/{userId}/disable`

### 邀请码

- 管理员创建门店邀请码：`POST /api/invites`
- 公开校验邀请码：`GET /api/invites/{code}`
- 邀请码只能使用一次，且有过期时间；入店时通过条件更新完成原子核销。
- 当前创建邀请码支持 `STAFF` 和 `OWNER` 两种角色。

### 当前角色

| 角色 | 当前能力 |
|---|---|
| `HQ_ADMIN` | 当前租户总部级管理和配置 |
| `REGION_ADMIN` | 区域范围管理和任务查看/配置 |
| `VIEWER` | 管理端只读访问 |
| `OWNER` | 门店成员管理、任务和门店范围能力 |
| `STAFF` | 个人任务、作品和生成能力 |

登录态中的核心身份信息为：`userId`、`tenantId`、`orgId`、`role`、`dataScope`。

## 3.2 企业管理端业务

企业管理端统一使用 `/api/admin/*` 路径，读权限主要允许 `HQ_ADMIN`、`REGION_ADMIN`、`VIEWER`，写权限主要允许 `HQ_ADMIN`、`REGION_ADMIN`，部分总部配置只允许 `HQ_ADMIN`。

### 管理端首页和趋势

- `GET /api/admin/dashboard/overview`
- `GET /api/admin/dashboard/trend`

目前统计内容包括：

- 活跃门店数；
- 本周作品数；
- 本周发布数；
- 当前额度余额；
- 本周额度消耗；
- 近 7 天作品和发布趋势。

总部和查看型角色查看全租户数据，区域管理员按当前区域过滤。

### 门店与账号

- 门店列表包含门店、成员数量、店长信息；
- 支持创建门店；
- 支持调整门店所属品牌或区域；
- 支持成员分页查询：`GET /api/admin/members`；
- 成员列表支持 `orgId`、`pageNo`、`pageSize` 参数。

### 素材中心

当前接口：

- 查询可见素材：`GET /api/admin/assets`
- 审核/推荐素材：`PUT /api/admin/assets/{id}/review`

当前已具备：

- 按租户查询可见素材；
- 处理素材推荐状态；
- 资产包素材和租户素材的数据库模型。

当前未具备完整的素材上传、创建、编辑、删除、文件存储和分类维护接口。因此素材中心属于“查询和审核已实现，完整内容运营未完成”。

### 内容包

- 查询内容包：`GET /api/admin/content-packages`
- 创建内容包：`POST /api/admin/content-packages`
- 停用内容包：`DELETE /api/admin/content-packages/{id}`

内容包包含营销日期、发布时间、文案方向和任务模板。定时任务每分钟扫描到期内容包，并自动生成一个全域指定内容任务，然后将内容包置为停用。

### 风格选项

- 查询可见风格：`GET /api/admin/styles`
- 创建风格：`POST /api/admin/styles`
- 修改风格：`PUT /api/admin/styles/{id}`
- 删除风格：`DELETE /api/admin/styles/{id}`

风格数据会被 AI 生成流程使用，包含名称、说明、示例地址、排序和所属内容包。

### 合规词和审核配置

- 查询合规词：`GET /api/admin/compliance/words`
- 新增或更新合规词：`PUT /api/admin/compliance/words`
- 停用合规词：`DELETE /api/admin/compliance/words/{id}`
- 查询组织审核配置：`GET /api/admin/compliance/audit-config/{orgId}`
- 更新组织审核配置：`PUT /api/admin/compliance/audit-config/{orgId}`

合规词支持两种级别：

- `level=1`：命中后使用替换词替换；
- `level=2`：命中后直接拒绝生成。

审核配置支持品牌级默认配置和组织级覆盖配置。

### 导出任务

- 创建导出任务：`POST /api/admin/exports`
- 查询导出任务列表：`GET /api/admin/exports`
- 查询导出任务详情：`GET /api/admin/exports/{id}`

当前支持：

- 租户级导出任务记录；
- 同时最多 3 个运行中的导出任务；
- 异步更新任务状态；
- 成功或失败状态记录。

当前导出处理只生成 `local://export/...` 占位地址，没有接入真实查询组装、CSV 文件写入、OSS 上传和下载接口。

## 3.3 任务业务

任务模块已经形成较完整的“任务下发 → 员工执行 → 发布核销 → 管理统计”闭环。

### 任务创建和管理

- 创建任务：`POST /api/task`
- 查询任务：`GET /api/task/{id}`
- 编辑任务：`PUT /api/task/{id}`
- 启停任务：`PATCH /api/task/{id}/status`
- 查询修改日志：`GET /api/task/{id}/modify-logs`

任务支持：

- 固定动作任务和指定内容任务；
- 每日、每周、每月频率；
- 全域、区域、门店、员工四种目标范围；
- 指定发布平台；
- 直接完成或需要截图凭证两种判定方式；
- 开始时间和结束时间；
- 任务创建者和创建层级记录。

权限规则已经在 `TaskPermissionService` 中实现：

- `HQ_ADMIN` 可创建全域、区域、门店和员工范围任务；
- `REGION_ADMIN` 不能创建全域任务，只能操作本区域及下属范围；
- `OWNER` 只能创建本店任务；
- `VIEWER` 和 `STAFF` 不能创建任务。

### 员工任务执行

- 查询我的任务：`GET /api/task/my`
- 查询门店看板：`GET /api/task/store-board`
- 发送任务提醒：`POST /api/task/remind`

系统会根据任务频率计算周期日期，并自动创建当前周期的 `task_record`。员工无需直接创建任务记录。

### 发布作品并完成任务

- 提交发布记录：`POST /api/publish-record`

提交时会校验：

1. 作品属于当前用户；
2. 任务属于当前租户；
3. 任务处于启用状态；
4. 当前时间在任务有效期内；
5. 发布平台与任务要求一致；
6. 当前用户在任务目标范围内；
7. `judgeType=2` 时必须提供凭证地址。

校验通过后：

1. 保存 `publish_record`；
2. 创建或获取当前周期的 `task_record`；
3. 将任务记录标记为完成；
4. 记录发布凭证和完成时间。

### 任务统计和激励

- 任务总看板：`GET /api/task/board`
- 门店汇总：`GET /api/task/board/stores`
- 员工明细：`GET /api/task/board/records`
- 管理端任务报表：`GET /api/admin/task/{taskId}/report`
- 排行榜：`GET /api/task/ranking`
- 勋章：`GET /api/task/badges`

当前统计支持：

- 期望完成数；
- 已完成数；
- 完成率；
- 门店和员工明细；
- 周榜、月榜和门店榜；
- 连续 7 天、周任务全勤、月度任务之星、门店完成率第一等勋章。

### 门店副本

店长编辑总部或区域创建的任务时，不直接覆盖上级任务，而是生成当前门店的任务副本：

- `sourceTaskId` 指向原任务；
- `targetScope=3`；
- `targetIds` 为当前门店；
- 原任务保留，当前门店优先使用副本。

### 任务提醒

手动提醒已经实现：系统查询目标范围内未完成的员工和店长，并写入 `message` 表。

定时提醒入口虽然保留了 `@Scheduled` 方法，但当前方法体为空，仅作为后续租户遍历调度点。因此“管理员手动提醒已完成”，“全租户每日自动提醒未完成”。

## 3.4 AI 内容生成

AI 业务入口：

- 生成作品：`POST /api/work/generate`
- 查询作品：`GET /api/work/{id}`
- 管理端查询媒体任务：`GET /api/admin/media-task/list`

### 生成流程

1. 校验当前登录用户；
2. 校验生成类型，仅支持 `IMAGE`、`VIDEO`；
3. 根据租户、平台、风格、产品名和用户输入组装提示词；
4. 执行合规词替换或拦截；
5. 创建 `work`；
6. 扣减门店额度；
7. 创建 `media_task`；
8. 事务提交后异步派发模型任务；
9. 生成成功后转存结果并更新作品；
10. 失败后更新失败原因并自动退款。

### 图片生成

当前支持：

- 同步调用图片 Provider；
- 成功后保存内容地址；
- 失败后更新任务和作品状态；
- 结果转存失败时最多重试 3 次。

### 视频生成

当前支持：

- 提交异步视频任务；
- 保存外部模型任务 ID；
- 定时轮询视频结果；
- 处理中恢复任务状态；
- 成功转存，失败退款；
- 超过 30 分钟的任务由超时扫描处理。

### Provider 实现

当前有两种 Provider：

- `DemoAiProvider`：默认启用，返回 `demo://image/...` 或 `demo://video/...` 地址，用于开发联调；
- `HttpAiProvider`：配置 `xiaoa.ai.provider.type=http` 后启用，按配置的图片、视频提交和视频查询地址调用外部模型。

因此当前 AI 流程代码已经完成，但默认环境使用的是演示模型，不代表已经接入真实生产模型。

### 提示词模板

- 查询模板：`GET /api/admin/prompt-template`
- 保存新版本：`PUT /api/admin/prompt-template`
- 回滚版本：`PUT /api/admin/prompt-template/{id}/rollback`

模板场景支持 `IMAGE` 和 `VIDEO`，保存时会自动递增版本并停用旧版本。

## 3.5 额度业务

额度接口：

- 总部充值：`POST /api/admin/quota/credit`
- 总部向门店分配：`POST /api/admin/quota/allocate`
- 查询门店额度：`GET /api/admin/quota/store/{storeId}`
- 查询额度流水：`GET /api/admin/quota/account/{accountId}/flows`

已实现的额度链路：

```text
总部额度池
  → 分配额度到门店账户
  → AI 生成前扣减门店额度
  → AI 失败后自动退款
  → quota_flow 记录每次变动
```

当前实现包含：

- 余额更新；
- 额度不足拦截；
- 事务处理；
- 业务幂等标识；
- AI 扣费和退款流水；
- 账户级流水查询。

额度仍属于租户内部的预充值/分配模型，尚未与真实支付订单和套餐购买打通。

## 4. 数据库和开发演示能力

当前数据库迁移包含以下业务表：

### 租户与权限

- `tenant`
- `org`
- `user`
- `user_wechat_bind`
- `user_org_role`
- `invite_code`

### 额度与 AI

- `quota_account`
- `quota_flow`
- `media_task`
- `work`
- `prompt_template`

### 任务与内容

- `task`
- `task_modify_log`
- `task_record`
- `publish_record`
- `message`
- `content_package`
- `style_option`

### 素材、审核和管理

- `asset_package`
- `asset`
- `compliance_word`
- `audit_config`
- `audit_record`
- `export_task`

### 其他基础表

- `chat_session`
- `chat_message`
- `payment_order`
- `track_log`

`V5__dev_seed_data.sql` 已提供可联调的演示数据，包括：

- 演示租户、品牌、区域和两家门店；
- 总部管理员、区域管理员、店长和员工账号；
- 邀请码；
- 租户和门店额度；
- 内容包、风格、素材和合规词；
- 任务、任务记录、作品、媒体任务和发布记录；
- 审核记录、支付订单、埋点、消息和导出记录。

当前开发环境默认使用 `sys` 数据库，`application-dev.yml` 中关闭 Flyway，种子数据需要按当前开发环境方式手动执行或确认已经执行。

## 5. 当前可以完整演示的业务流程

### 流程一：管理员登录并查看管理端

```text
使用演示 openid 调用 /api/auth/login
  → 获取 token
  → 调用 /api/auth/me
  → 调用 /api/admin/dashboard/overview
  → 调用 /api/admin/stores
  → 调用 /api/admin/members
```

### 流程二：创建门店并邀请员工

```text
创建区域/门店
  → 生成邀请码
  → 员工调用 /api/auth/join
  → 创建 user_org_role
  → 返回员工 token
  → 员工调用 /api/task/my
```

### 流程三：AI 生成并扣额度

```text
选择风格和产品信息
  → /api/work/generate
  → 生成 work 和 media_task
  → 扣减门店额度
  → Demo 或 HTTP Provider 处理
  → 更新作品状态和内容地址
  → 失败自动退款
```

### 流程四：员工发布作品并完成任务

```text
查询我的任务
  → 生成或选择作品
  → 发布到目标平台
  → /api/publish-record
  → 写入发布记录
  → task_record 标记完成
  → 管理端看板更新
```

### 流程五：总部配置内容并自动下发任务

```text
创建内容包
  → 配置发布时间和任务模板
  → 定时任务扫描到期内容包
  → 自动创建全域内容任务
  → 内容包停用
  → 员工在任务列表中看到任务
```

## 6. 尚未完成或仅有基础的业务

## 6.1 平台运营和平台财务

当前代码中未发现以下能力：

- `PLATFORM_OPS`、`PLATFORM_FINANCE` 独立角色；
- 平台用户或平台管理员专用数据表；
- `/platform/*` 路由；
- 平台级租户列表、租户开通审核、停用和全局数据范围；
- 平台级充值、支付订单审核、退款和对账接口；
- 平台财务报表和资金流水管理。

现有 `HQ_ADMIN` 是租户内部的总部管理员，不等同于平台运营或平台财务角色。

## 6.2 微信生产认证

当前接口直接接收 `openid`，尚未实现：

- `wx.login` 的临时 `code` 换取 `openid`；
- 微信手机号授权解密；
- 短信验证码；
- 账号接管的二次身份认证。

当前账号接管只按手机号和新 `openid` 判断，不能直接作为生产级身份核验方案。

## 6.3 支付、套餐和正式续费

数据库中已有 `payment_order`，种子数据中也有演示支付订单，但当前没有：

- 支付下单接口；
- 微信支付或其他支付渠道调用；
- 支付回调验签；
- 套餐和价格配置；
- 支付后自动开通租户；
- 支付后自动初始化额度；
- 退款、对账和财务审核。

当前租户续费接口只更新 `expireAt` 和租户状态，属于管理端基础接口。

## 6.4 文件存储和导出文件

当前对象存储使用 `LocalObjectStorageService`，返回 `local://...` 地址。导出任务也返回 `local://export/...` 地址。

尚未完成：

- OSS 或对象存储上传；
- 图片、视频和截图真实访问地址；
- 文件权限和临时签名；
- CSV 实际生成；
- 文件下载接口。

## 6.5 聊天业务

数据库已有 `chat_session` 和 `chat_message`，种子数据也写入了演示聊天记录，但当前未发现对应的 Chat Controller、Service 和接口。因此聊天业务目前属于数据模型和演示数据阶段。

## 6.6 完整审核业务

当前已实现：

- 合规词替换和拦截；
- 组织级审核开关；
- 素材推荐状态审核；
- `audit_record` 表和演示数据。

当前未形成完整的作品审核接口链路，例如：

- 待审核作品列表；
- 审核通过/驳回接口；
- 审核意见管理；
- 审核状态影响作品发布的完整规则。

## 6.7 自动提醒

管理员主动调用 `/api/task/remind` 的提醒已经实现，写入 `message` 表。

每日 17:00 的定时入口目前为空，没有遍历所有租户并自动发送提醒的逻辑。

## 6.8 数据范围和权限细节

任务统计模块已经使用 `AdminDataScopeService` 进行总部、区域、门店和员工范围判断，但全项目的数据范围还没有完全统一：

- `tenant` 的组织树接口当前返回当前租户全部组织；
- 部分管理端查询只校验“有管理端读权限”，没有进一步按区域或门店裁剪；
- 部分用户和角色管理接口的目标范围校验仍需收紧；
- 一个用户存在多条有效成员关系时，登录默认取第一条；
- 当前没有租户/门店切换接口。

## 7. 建议的后续开发顺序

### 第一阶段：补齐平台管理域

1. 新增平台用户和平台角色模型；
2. 增加 `PLATFORM_OPS`、`PLATFORM_FINANCE` 权限；
3. 增加平台租户管理接口；
4. 增加支付订单、充值、退款和对账接口；
5. 明确平台数据范围与租户数据范围的边界。

### 第二阶段：补齐生产基础设施

1. 接入微信登录和手机号认证；
2. 接入真实 AI Provider；
3. 接入 OSS；
4. 实现导出文件生成和下载；
5. 增加任务、AI、支付等关键异步流程的监控和重试告警。

### 第三阶段：补齐运营闭环

1. 完整素材上传和分类管理；
2. 完整作品审核流程；
3. 聊天接口；
4. 自动任务提醒；
5. 成员、组织和数据范围统一收敛；
6. 多租户/多门店切换。

## 8. 代码和文档索引

### 业务文档

- 租户模块：`tenant-api.md`
- 任务模块：`task-api.md`
- 产品规划：`prd.md`

### 核心代码目录

- 租户与认证：`src/main/java/com/xiaoa/tenant`
- 管理端：`src/main/java/com/xiaoa/admin`
- 任务：`src/main/java/com/xiaoa/task`
- AI：`src/main/java/com/xiaoa/ai`
- 额度：`src/main/java/com/xiaoa/quota`
- 公共认证：`src/main/java/com/xiaoa/common/auth`

### 数据库迁移

- 基础租户和业务表：`V1__init_schema.sql`
- 任务和作品：`V2__task_domain.sql`
- 企业管理端：`V3__enterprise_admin.sql`
- AI 生成扩展：`V4__ai_generation.sql`
- 开发演示数据：`V5__dev_seed_data.sql`

## 9. 结论

当前项目已经完成的是一个可用于开发联调的后端业务骨架，并且以下链路具备较完整实现：

```text
多租户身份
  + 组织和成员
  + 管理端配置
  + 任务下发与完成
  + AI 生成与额度结算
  + 统计看板和激励
```

但它还不是完整的生产商业系统。平台运营/平台财务、支付闭环、真实微信认证、OSS、真实导出、聊天接口、完整审核和自动提醒仍需要继续建设。
