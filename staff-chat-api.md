# 员工额度 & 对话模式 · 前端接口文档

> 对应后端模块:`com.xiaoa.quota`(员工额度三级账户)、`com.xiaoa.ai.chat`(对话模式)。
> 本文档描述这两个新功能的真实行为,供前端对接使用。
> 通用约定(认证头、响应包装、时间格式)与《quota-asset-api.md》一致,不再重复。

---

## 1. 员工额度:三级账户 + 店长划拨

### 1.1 业务模型

额度池从两级升级为三级:

```
租户总池(TENANT) → 门店账户(STORE) → 员工账户(STAFF)
```

| 规则 | 说明 |
| --- | --- |
| 扣费对象 | AI 生成 / 对话出稿**扣员工自己的账户**;员工账户不存在(老数据)回退扣门店账户 |
| 余额不足 | 返回 `3001`,前端提示文案:**"额度不足,请联系店长划拨"** |
| 划拨 | 店长把门店额度划给员工:一条事务双流水(门店 `ALLOCATE_OUT` / 员工 `ALLOCATE_IN`),不能超门店池余额 |
| 回收 | 店长把员工未用额度收回门店(`RECALL` 双流水),回收金额不能超员工当前余额 |
| 入店即建 | 邀请码入店成功时自动创建员工账户(余额 0),查询无需判空 |
| memberRoleId | 员工账户挂在**成员关系**上(`user_org_role.id`),一人多店各有账户;该 ID 从成员列表的 `userOrgRoleId` 字段获取 |

### 1.2 数据表变更(V9,已应用)

```sql
ALTER TABLE quota_account MODIFY COLUMN level VARCHAR(10) NOT NULL COMMENT 'TENANT/STORE/STAFF';
ALTER TABLE quota_account ADD UNIQUE INDEX uk_quota_account_owner (tenant_id, level, owner_id);
```

### 1.3 接口清单

#### `POST /api/quota/staff/allocate` — 店长向员工划拨(仅 OWNER)

请求体:

```json
{
  "memberRoleId": 12,        // 必填,成员关系 ID(成员列表的 userOrgRoleId)
  "amount": 100,             // 必填,≥1
  "bizId": "alloc-uuid-001", // 可选但强烈建议传,≤64 字符,幂等键
  "remark": "9月文案额度"     // 可选
}
```

响应:`data` 为空,`code=0` 即成功。

| code | 场景 |
| --- | --- |
| 2003 | 当前登录人不是 OWNER |
| 1001 | 员工不存在 / 不属于本店 / 不是 STAFF 角色 |
| 3001 | 门店池余额不足 |
| 1003 | 幂等键已被其他账务操作使用(同 key 但金额不同) |

**幂等语义**:相同 `bizId` 重复提交,金额一致时幂等返回成功(不会重复划拨);未传 `bizId` 时后端自动生成随机键(网络重试仍可能重复划拨,前端务必用 UUID 生成)。

#### `POST /api/quota/staff/recall` — 店长回收员工额度(仅 OWNER)

请求体同划拨(`memberRoleId` + `amount` + `bizId` + `remark`)。

| code | 场景 |
| --- | --- |
| 3001 | 员工当前余额 < 回收金额 |

响应同划拨。

#### `GET /api/quota/my` — 我的额度(已改造)

按角色返回:

| 角色 | 返回的账户 |
| --- | --- |
| STAFF | 自己的员工账户(老数据无员工账户时回退门店账户) |
| OWNER | 门店账户 |
| HQ_ADMIN / REGION_ADMIN / VIEWER | 租户池 |

响应结构不变(`QuotaSummary`:`account` + `recentFlows`),员工端"我的"Tab 余额数字直接改用本接口。

#### `GET /api/admin/members` — 成员列表(已扩展)

每条记录新增两个字段:

```json
{
  "id": 1001,
  "nickname": "小李",
  "quotaBalance": 80,     // 余额:STAFF→员工账户,OWNER→门店,管理层→租户池
  "userOrgRoleId": 12     // STAFF 才有:划拨/回收接口的 memberRoleId 直接取这里
}
```

### 1.4 前端对接要点

- 店长"账号管理"Tab:员工列表加**余额**列 → 点员工进额度抽屉(余额 + 流水 + 划拨/回收按钮)。
- 划拨弹窗:金额上限 = 门店池余额(`/api/quota/my` 的门店账户);回收弹窗:金额上限 = 该员工 `quotaBalance`。
- `bizId` 复用额度域三板斧:UUID 生成、提交前存本地、失败不清除。

---

## 2. 对话模式(引导式聊天创作)

### 2.1 业务模型（5-Agent 极简编排）

员工说人话，AI 自动判断信息够不够：**够** → 查资料（技能）后直接出 3 版文案；**不够** → 返回选项卡让员工点选，选完自动复判，循环直到信息足够（3 轮封顶后 AI 代选兜底）。**提示词对员工全隐藏**。

主流程（每个节点是一个独立 Agent，记忆互相隔离）：

```
Gate（意图识别+内容形态(文案/图片/视频)/主题判定+槽位抽取+判断信息够不够，一次 LLM 完成）
  ├─ 足够 → 收集知识（ReAct：思考→行动→观察，只信技能返回）
  │          → 预扣额度 → 生成 → 合规过滤 → 整合出稿
  ├─ 不足 → 选项卡挂起（30s 倒计时）→ 员工点选 → 再次判断（循环，≤3 轮）
  └─ 闲聊/咨询/微调意图 → 不进创作循环，直接回复/改写
```

五个 Agent 的职责：

| Agent | 节点（WS stage.node） | 职责 |
| --- | --- | --- |
| GateAgent | `gateAssess` | 判断信息够不够（必查内容形态与主题，兼意图识别/槽位抽取/营销日历装配） |
| SkillAgent | `skillInvoke` | 收集知识（ReAct 循环调技能：商品资料/营销日历） |
| OptionAgent | `presentOptionCard` / `applyOption` | 给出选项卡（一次只问一个维度）+ 应用选择回 Gate 复判 |
| GenerateAgent | `generateContent` | 生成/改写（兼额度预扣与合规过滤） |
| ComposerAgent | `compose` | 整合收口：咨询回复/三段式包装 + AI 消息落库 + 回复 VO |

**无上下文模式**：每次创作独立运行，LLM 不携带历史对话、不继承上一轮槽位；
本轮输入 = 员工当前提示词 + 选项卡选择（经后端挂起恢复在同一编排内传递）。

**任务 id 与会话 id 分离**：每次创作编排生成一个独立任务 id（Agent 隔离记忆/checkpoint 都挂它上面），
工作流走到最后节点即任务完成——该任务 id 在 Redis 中的记忆立即删除；同一会话的下一次创作
用全新任务 id，互不残留。会话 id 只负责消息落库与会话管理。

要素收集状态机（仅本轮编排内存在）：

```json
{
  "contentType": "文案",           // 内容形态：文案/图片/视频（必查，缺失则 ready=false）
  "theme": "节日氛围",              // 主题方向：节日氛围/爱情婚嫁/日常种草等（必查，缺失则 ready=false）
  "product": "新款对戒",            // 首轮从用户话里提取
  "platform": "朋友圈",             // 选项卡点选后回填
  "scene": "吸引到店",
  "clarification": "文案；节日氛围；朋友圈"  // 员工在选项卡里点选的方向，累积合并
}
```

### 2.2 计费规则

| 动作 | 扣费 | 幂等键 |
| --- | --- | --- |
| 每轮追问/选项卡 | 0(免费) | — |
| 首次出 3 版文案 | 1 点(文案价) | `chat:{sessionId}:gen` |
| 微调 / 换版 | 1 点 / 次 | `chat:{sessionId}:rev:{n}`(n 递增) |

- 扣费账户与 AI 生成一致:员工扣员工账户。
- **额度不足时不生成、不计费**：本轮回复 `action=ASK` 的充值提示，正常落库。
- 微调链路 LLM 失败时整体失败(本轮消息不落库)，员工重试即可。

### 2.3 会话生命周期

| 状态 | 说明 |
| --- | --- |
| `ACTIVE` | 可继续对话 |
| `CLOSED` | 已关闭:发消息返回 1001 "会话已关闭,请新建会话" |

**7 天不活跃的会话在下一次发消息时自动关闭**,并返回 1001 提示新建会话。

### 2.4 数据表变更(V10,已应用)

```sql
ALTER TABLE chat_session ADD COLUMN scene VARCHAR(32) NULL COMMENT '创作场景';
ALTER TABLE chat_session ADD COLUMN context JSON NULL COMMENT '要素收集状态';
ALTER TABLE chat_session ADD COLUMN revise_count INT NOT NULL DEFAULT 0 COMMENT '微调次数';
ALTER TABLE work ADD COLUMN chat_session_id BIGINT NULL COMMENT '关联对话会话';
```

### 2.5 接口清单

#### `POST /api/chat/sessions` — 创建会话

```json
{ "scene": "朋友圈" }   // 必填,≤32 字符:朋友圈/小红书/视频号等
```

响应 `data`(ChatSession):

```json
{
  "id": 5, "tenantId": 1, "userId": 1001,
  "title": "朋友圈 · 创作对话", "scene": "朋友圈",
  "status": "ACTIVE", "context": "{}",
  "reviseCount": 0,
  "createdAt": "2026-09-25T10:00:00", "updatedAt": "2026-09-25T10:00:00"
}
```

#### `GET /api/chat/sessions` — 会话列表

返回本人最近 20 条会话,按活跃时间倒序。

#### `GET /api/chat/sessions/{id}` — 全量历史(重进页面恢复)

响应 `data`:

```json
{
  "session": { "...": "同上" },
  "messages": [
    { "id": 101, "role": "USER", "content": "帮我写条朋友圈,推一下新款对戒", "createdAt": "..." },
    { "id": 102, "role": "AI",   "content": "{\"action\":\"ASK\",\"question\":\"好的～想突出什么卖点？\"}", "createdAt": "..." }
  ]
}
```

> **AI 消息 content 是 JSON 字符串**,前端渲染历史时需解析:
> - 追问:`{"action":"ASK","question":"..."}`
> - 出稿:`{"action":"GENERATE","versions":["v1","v2","v3"],"revisedFrom":2}`(`revisedFrom` 仅微调消息有,表示基于第几版改写)

#### `POST /api/chat/sessions/{id}/messages` — 发一句话(核心对话接口)

```json
{ "text": "爱情寓意" }   // 必填,≤2000 字符
```

响应 `data`(ChatReplyVO):

```json
{
  "sessionId": 5,
  "action": "ASK",              // 见下方 action 枚举表
  "question": "目标客群是谁？",  // action=ASK/OPTION_CARD 时有值
  "versions": [                  // action=GENERATE 时有值,3 版(微调为 1 版)
    "【朋友圈 · 情感版】...",
    "【朋友圈 · 种草版】...",
    "【朋友圈 · 简洁版】..."
  ],
  "context": "{\"product\":\"对戒\",\"platform\":\"朋友圈\"}",   // 本轮要素,无上下文模式下仅回显本轮槽位,不累积历史
  "messageId": 102
}
```

action 枚举:

| action | 含义 | 关键字段 | 扣费 |
| --- | --- | --- | --- |
| `ASK` | 追问/咨询回复/额度不足提示 | `question` | 0 |
| `OPTION_CARD` | **信息不足,渲染选项卡等员工点选**(可连续多轮) | `question` + `optionCard` | 0 |
| `GENERATE` | 出稿(已扣费) | `question`(三段式引导语) + `versions` | 1 点 |

> 旧版的 `QUESTIONNAIRE`(问卷)与 `PENDING_MEDIA`(图/视频任务)action 已下线：
> 信息澄清统一走 `OPTION_CARD`；图/视频生成暂未接入对话链路（二期恢复）。

`optionCard` 结构(label 短句通俗、hint 写易懂后果；`slotKey` 为该选项归属槽位，前端可忽略):

```json
{
  "options": [
    { "key": "A", "label": "文案", "hint": "纯文字，发圈最快，配现成图就能发", "slotKey": "contentType" },
    { "key": "B", "label": "图片配文", "hint": "实拍图+种草文，适合展示款式细节", "slotKey": "contentType" },
    { "key": "C", "label": "视频脚本", "hint": "口播词+分镜提示，照着拍就能发", "slotKey": "contentType" },
    { "key": "D", "label": "你帮我定", "hint": "AI 按推荐代选并标注，生成后可再改", "slotKey": null }
  ],
  "deadlineSeconds": 30
}
```

> **选项卡语义**：一次只问当前最有用的**一个维度**
> (优先级:内容形态(文案/图片/视频) > 主题 > 发布平台 > 内容目的 > 商品/素材 > 节日 > 风格；
> 其中**内容形态与主题是必查项**，Gate 缺任一项一律判定信息不足，选项卡 `question` 文案即当前维度题干)。
> 点选 A/B/C 后若信息仍不够会**再收到一张新卡**(整体替换，问下一个维度)，直到信息足够或 3 轮封顶
> (AI 代选直接生成，出稿带 `aiDecidedSlots` 标注哪些槽位是 AI 代选)。
> 员工也可以不点选项，直接在输入框自由补充；超时不选由后端按「你帮我定」兑底。

错误:

| code | 场景 |
| --- | --- |
| 1001 | 会话不存在 / 已关闭 / 7 天未活跃;参数校验失败 |
| 2003 | 访问他人的会话 |

> 额度不足不再报 `3001`:本轮回复 `action=ASK` 的充值提示并正常落库；
> 文案命中违规词不再报 `4001`:输出警示草稿正常落库（员工人工检查后可微调）。

#### `POST /api/chat/sessions/{id}/option` — 选项卡点选（挂起恢复）

```json
{ "key": "A" }   // 选项卡里的 key:A/B/C=选定方向,D=直接生成
```

响应同 `messages` 接口:

- 信息已足够 → `action=GENERATE`(直接出稿,正常扣费);
- 仍不足 → `action=OPTION_CARD`(新的一张卡,重复上一轮交互)。

错误:

| code | 场景 |
| --- | --- |
| 1001 | 当前没有待选择的选项卡 / 选项卡已失效(超时被兑底放行) |

> 旧版问卷作答接口 `POST /api/chat/sessions/{id}/answer` 与 WS `chat.answer` 帧已**删除**(问卷链路下线)。

#### `POST /api/chat/sessions/{id}/revise` — 微调指定版本

```json
{ "versionNo": 2, "instruction": "开头改得浪漫一点" }   // versionNo 对应最近一次出稿的第几版
```

响应同 `messages` 接口(`action=GENERATE`,`versions` 只有一版新文案)。

错误:

| code | 场景 |
| --- | --- |
| 1001 | 还没有可微调的文案 / `versionNo` 超出范围 |
| 1000 | 改写失败(LLM 异常),稍后重试 |

### 2.6 出稿后"去配图"衔接

对话出稿 → 员工选定某版 → 点"去配图":

1. 前端调 `POST /api/work/generate`,请求体**新增可选字段** `chatSessionId`(传当前会话 ID)。
2. 带 `chatSessionId` 的作品**不再自动生成成套文案**(避免覆盖对话产出);选定文案通过 `PUT /api/work/{id}/caption` 回填到作品。
3. 作品列表 / 详情会返回 `chatSessionId` 字段,可跳回对话溯源。

### 2.7 WebSocket 实时通道(/ws/chat,推荐)

与 REST 同一套事务与编排,额外推送节点级进度。帧协议(JSON,均带 `type`):

客户端 → 服务端:

```json
{"type":"chat.send","sessionId":5,"text":"帮我写条朋友圈文案"}
{"type":"chat.option","sessionId":5,"key":"A"}
{"type":"ping"}
```

服务端 → 客户端:

```json
{"type":"connected"}
{"type":"stage","sessionId":5,"node":"gateAssess","label":"正在理解需求并判断信息是否足够…"}
{"type":"message","sessionId":5,"reply":{...ChatReplyVO,同 REST...}}
{"type":"done","sessionId":5}
{"type":"error","sessionId":5,"code":1000,"message":"..."}   // 编排异常;额度不足已改为 ASK 提示不再走 error
```

`stage.node` 取值(5-Agent 编排,共 6 个节点;前端按 node 过滤时参考,只展示 label 可忽略):

| node | label | Agent |
| --- | --- | --- |
| gateAssess | 正在理解需求并判断信息是否足够… | GateAgent |
| skillInvoke | 正在查询创作资料… | SkillAgent |
| presentOptionCard | 正在为你准备选项… | OptionAgent |
| applyOption | 正在应用你的选择… | OptionAgent |
| generateContent | 正在生成内容… | GenerateAgent |
| compose | 正在组织回复… | ComposerAgent |

> 旧节点名 `fetchContext`/`understandIntent`/`verifyAndCharge`/`mediaSubmit`/`reviewAndRespond`/
> `billingConfirm`/`respondConsult`/`respondQuota`/`mergeAnswers`/`reviseValidate`/`reviseCallLlm`/
> `reviseCompliance`/`reviseBilling`/`responseComposer`/`persist` 已全部移除。
> 同一条连接同一时刻只允许一次进行中的编排,重复发送回 error 帧。

### 2.8 后端配置项(联调相关)

```yaml
xiaoa:
  ai:
    llm:
      type: demo    # demo(默认,内置演示模型,返回模板文案)/ openai(真实模型,OpenAI 兼容协议)
      base-url: ""  # type=openai 时的网关地址
      api-key: ""   # type=openai 时的密钥(建议放本地 application-local.yml,不进 git)
      model: ""     # 模型名,如 gpt-6-luna
    cost:
      copy: 1       # 对话出稿/微调单价(点)
```

### 2.9 边界场景

| 场景 | 后端行为 | 前端处理 |
| --- | --- | --- |
| LLM 返回非法/失败 | Gate 重试 1 次,仍失败按必填槽位兑底判断;出稿失败用模板兑底 | 正常渲染 |
| 额度不足 | 不生成、不计费,回复 `ASK` 充值提示并正常落库 | 渲染提示气泡,员工找店长充值 |
| 文案命中违规词(level2 拦截) | 输出警示草稿正常落库(预扣不退) | 渲染警示草稿,引导员工微调 |
| 会话超 7 天未活跃 | 发消息时置 CLOSED 并报 1001 | 提示后引导新建会话 |
| 会话超长 / 跨轮上下文 | 无上下文模式:LLM 不携带历史对话,每轮独立 | 无感 |
| 选项卡超时(前端 30s 倒计时,后端 60s 兑底) | 按「你帮我定」放行,缺失项 AI 代选,结果正常落会话 | 员工回来直接看到出稿结果,无需处理 |

---

## 3. 变更记录(相对旧文档)

| 文档 | 受影响内容 |
| --- | --- |
| `quota-asset-api.md` | `GET /api/quota/my` 行为变更:STAFF 优先返回员工账户 |
| `ai-admin-api.md` | `GET /api/admin/members` 新增 `quotaBalance`、`userOrgRoleId` 字段;`POST /api/work/generate` 请求体新增可选 `chatSessionId` |
| 本文档(2026-09-29) | 对话模式 Agent 化:问卷(QUESTIONNAIRE)下线,信息澄清统一走选项卡 OPTION_CARD(**可多轮循环**);新增 Gate 判断/技能取数阶段(WS `stage.node` 名变更);新增 `/option` 接口与 WebSocket 通道文档;REST 路径/入参/返回结构无变化 |
| 本文档(2026-09-30) | 编排精简为 **5-Agent**(Gate/收集知识/选项卡/生成/整合)；旧问卷接口 `/answer` 与 WS `chat.answer` **删除**;`stage.node` 收敛为 6 个值;选项卡 D 改为「你帮我定」且选项新增 `slotKey` 字段;额度不足/违规词改为**正常落库提示**(不再报 3001/4001);Gate 新增**内容形态(文案/图片/视频)与主题**两项必查维度，选项卡按此出对应维度题；**任务 id 与会话 id 分离**：每次编排独立任务 id，走到最后节点即删除该任务在 Redis 的记忆 |
