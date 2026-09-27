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

### 2.1 业务模型

员工说人话,AI 追问补齐要素,一次出 3 版文案,可微调换版。**提示词对员工全隐藏**。

要素收集状态机(存在 `chat_session.context` JSON 里):

```json
{
  "product": "新款对戒",      // 首轮从用户话里提取
  "sellingPoint": "爱情寓意", // AI 追问补
  "audience": "新婚",         // AI 追问补
  "rounds": 2
}
```

### 2.2 计费规则

| 动作 | 扣费 | 幂等键 |
| --- | --- | --- |
| 每轮追问 | 0(免费) | — |
| 首次出 3 版文案 | 1 点(文案价) | `chat:{sessionId}:gen` |
| 微调 / 换版 | 1 点 / 次 | `chat:{sessionId}:rev:{n}`(n 递增) |

- 扣费账户与 AI 生成一致:员工扣员工账户,余额不足返回 `3001`("请联系店长划拨")。
- 出稿扣费失败时**整个回复失败**(本轮消息不落库),员工重发即可。

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
  "action": "ASK",              // ASK=追问(不扣费) / GENERATE=出稿(已扣费)
  "question": "目标客群是谁？",  // action=ASK 时有值
  "versions": [                  // action=GENERATE 时有值,3 版(微调为 1 版)
    "【朋友圈 · 情感版】...",
    "【朋友圈 · 种草版】...",
    "【朋友圈 · 简洁版】..."
  ],
  "context": "{\"product\":\"对戒\",\"sellingPoint\":\"爱情寓意\"}",
  "messageId": 102
}
```

错误:

| code | 场景 |
| --- | --- |
| 1001 | 会话不存在 / 已关闭 / 7 天未活跃;参数校验失败 |
| 2003 | 访问他人的会话 |
| 3001 | 出稿扣费时员工额度不足(本轮不落库,可直接重发) |
| 4001 | 产出文案命中 level2 违规词(本轮不落库,员工换说法重发) |

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
| 3001 / 4001 | 同上 |

### 2.6 出稿后"去配图"衔接

对话出稿 → 员工选定某版 → 点"去配图":

1. 前端调 `POST /api/work/generate`,请求体**新增可选字段** `chatSessionId`(传当前会话 ID)。
2. 带 `chatSessionId` 的作品**不再自动生成成套文案**(避免覆盖对话产出);选定文案通过 `PUT /api/work/{id}/caption` 回填到作品。
3. 作品列表 / 详情会返回 `chatSessionId` 字段,可跳回对话溯源。

### 2.7 后端配置项(联调相关)

```yaml
xiaoa:
  ai:
    llm:
      type: demo    # demo(默认,内置演示模型,返回模板文案)/ http(真实模型)
      url: ""       # type=http 时的 LLM 服务地址,POST JSON
    cost:
      copy: 1       # 对话出稿/微调单价(点)
```

### 2.8 边界场景

| 场景 | 后端行为 | 前端处理 |
| --- | --- | --- |
| LLM 返回非法/失败 | 自动重试 1 次,仍失败返回兜底追问"能再具体一点吗?" | 正常渲染追问气泡 |
| 出稿扣费失败(3001) | 整体回滚,消息不落 | toast"额度不足,请联系店长划拨",保留输入框内容 |
| 文案命中违规词(4001) | 整体回滚,消息不落 | toast 具体违规提示,引导换说法 |
| 会话超 7 天未活跃 | 发消息时置 CLOSED 并报 1001 | 提示后引导新建会话 |
| 会话超长 | LLM 只携带最近 10 轮 + 要素上下文 | 无感 |

---

## 3. 变更记录(相对旧文档)

| 文档 | 受影响内容 |
| --- | --- |
| `quota-asset-api.md` | `GET /api/quota/my` 行为变更:STAFF 优先返回员工账户 |
| `ai-admin-api.md` | `GET /api/admin/members` 新增 `quotaBalance`、`userOrgRoleId` 字段;`POST /api/work/generate` 请求体新增可选 `chatSessionId` |
