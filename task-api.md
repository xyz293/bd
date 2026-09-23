# Task 模块接口文档

> 本文档根据当前 `src/main/java/com/xiaoa/task` 的 Controller、DTO、Service 和数据库迁移整理，覆盖任务下发、任务执行、发布核销、提醒和门店看板接口。
>
> 接口前缀统一为 `/api`。文档中的字段和业务行为以当前代码实现为准。

## 1. 基础约定

### 1.1 请求地址

```text
/api
```

本地开发地址示例：

```text
http://127.0.0.1:8080/api
```

### 1.2 登录认证

任务域接口均要求登录。请求头携带登录接口返回的 token：

```http
Authorization: Bearer <token>
```

认证过滤器会从 token 中解析以下身份信息：

| 字段 | 说明 |
|---|---|
| `userId` | 当前用户 ID |
| `tenantId` | 当前租户 ID |
| `orgId` | 当前组织/门店 ID |
| `role` | 当前角色 |
| `dataScope` | 数据范围 |

开发联调时也可以使用租户请求头：

```http
X-Tenant-Id: <tenantId>
```

但业务权限仍以登录 token 中的身份为准。

### 1.3 Content-Type

带 JSON 请求体的接口使用：

```http
Content-Type: application/json
```

### 1.4 统一响应结构

成功响应：

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `integer` | `0` 表示成功，非 0 表示失败 |
| `msg` | `string` | 响应说明 |
| `data` | `object/array/null` | 业务数据；无数据时不返回该字段 |

失败响应示例：

```json
{
  "code": 2003,
  "msg": "没有操作权限"
}
```

### 1.5 时间和日期格式

- `LocalDateTime` 字段：建议使用 `yyyy-MM-dd HH:mm:ss`。
- `LocalDate` 字段：使用 `yyyy-MM-dd`。
- `startAt`、`endAt` 可以为空，表示不限制对应一侧的时间边界。

## 2. 角色和权限

### 2.1 角色

| 角色 | 任务能力 |
|---|---|
| `HQ_ADMIN` | 创建全域任务；修改、启停任务；查看修改留痕；查看任意门店看板；提醒任务目标人员 |
| `REGION_ADMIN` | 创建区域及以下范围任务；修改、启停任务；查看修改留痕；查看任意门店看板；提醒任务目标人员 |
| `OWNER` | 创建本店任务；可以修改自己有权限范围内的上级任务，修改采用门店变体方式；可以查看本店看板并提醒本店员工 |
| `STAFF` | 查看自己命中的任务；上报自己的作品并触发任务核销 |
| `VIEWER` | 不能创建或修改任务；当前任务域不提供专门的查看型任务管理接口 |

### 2.2 下发范围

| `targetScope` | 含义 | `targetIds` 含义 |
|---:|---|---|
| `1` | 全员 | 可以为空；服务端会保存为空数组 `[]` |
| `2` | 指定区域 | 区域 ID 列表 |
| `3` | 指定门店 | 门店/组织 ID 列表 |
| `4` | 指定员工 | 用户 ID 列表 |

当前实现对店长创建任务强制要求 `targetScope=3`，且只能选择当前 `orgId`。

### 2.3 数据范围

| `dataScope` | 含义 |
|---:|---|
| `1` | 全域 |
| `2` | 本区域 |
| `3` | 本店 |
| `4` | 本人 |

## 3. 任务字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `id` | `long` | 响应返回 | 任务 ID |
| `tenantId` | `long` | 服务端生成 | 租户 ID |
| `title` | `string` | 是 | 任务标题，不能为空 |
| `formType` | `integer` | 是 | `1` 固定动作；`2` 指定内容 |
| `contentPackageId` | `long` | 条件必填 | `formType=2` 时必须填写内容包 ID |
| `platform` | `string` | 否 | 要求发布的平台，如朋友圈、小红书 |
| `frequency` | `integer` | 是 | `1` 每日；`2` 每周；`3` 每月 |
| `targetScope` | `integer` | 是 | 任务下发范围，见上表 |
| `targetIds` | `array<long>` | 条件必填 | `targetScope != 1` 时不能为空 |
| `judgeType` | `integer` | 是 | `1` 点“我已发布”即完成；`2` 需要截图凭证 |
| `sourceTaskId` | `long` | 响应返回 | 门店变体关联的原任务 ID；普通任务为空 |
| `createdBy` | `long` | 服务端生成 | 创建人 ID |
| `createdLevel` | `integer` | 服务端生成 | `1` 总部；`2` 区域；`3` 门店 |
| `status` | `integer` | 响应返回 | `1` 生效；`2` 停用 |
| `startAt` | `datetime` | 否 | 生效开始时间 |
| `endAt` | `datetime` | 否 | 生效结束时间 |
| `createdAt` | `datetime` | 响应返回 | 创建时间 |
| `updatedAt` | `datetime` | 响应返回 | 更新时间 |

## 4. 接口总览

| 模块 | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 任务 | `POST` | `/api/task` | 总部/区域/店长 | 创建任务 |
| 任务 | `PUT` | `/api/task/{id}` | 有权管理该任务的管理员 | 修改任务；店长修改上级任务生成门店变体 |
| 任务 | `PATCH` | `/api/task/{id}/status` | 有权管理该任务的管理员 | 启用或停用任务 |
| 任务 | `GET` | `/api/task/{id}` | 已登录 | 查询任务详情 |
| 任务 | `GET` | `/api/task/my` | 已登录 | 查询当前用户命中的生效任务 |
| 任务 | `GET` | `/api/task/{id}/modify-logs` | 总部/区域管理员 | 查询任务修改留痕 |
| 任务 | `GET` | `/api/task/store-board` | 总部/区域/本店店长 | 查询门店完成率和未完成记录 |
| 任务 | `POST` | `/api/task/remind` | 总部/区域/目标门店店长 | 提醒未完成员工 |
| 发布核销 | `POST` | `/api/publish-record` | 已登录 | 上报作品，触发任务核销 |

## 5. 创建任务

### 5.1 请求

```http
POST /api/task
Authorization: Bearer <token>
Content-Type: application/json
```

### 5.2 请求参数

| 字段 | 类型 | 必填 | 约束 |
|---|---|:---:|---|
| `title` | `string` | 是 | 不能为空 |
| `formType` | `integer` | 是 | `1` 或 `2` |
| `contentPackageId` | `long` | 否 | `formType=2` 时必填 |
| `platform` | `string` | 否 | 发布平台 |
| `frequency` | `integer` | 是 | `1`、`2` 或 `3` |
| `targetScope` | `integer` | 是 | `1` 至 `4` |
| `targetIds` | `array<long>` | 条件必填 | 非全员任务必填 |
| `judgeType` | `integer` | 是 | `1` 或 `2`，默认 `1` |
| `startAt` | `datetime` | 否 | 开始时间 |
| `endAt` | `datetime` | 否 | 结束时间，不能早于 `startAt` |

### 5.3 请求示例：总部创建全员每日任务

```json
{
  "title": "每日发布一条朋友圈",
  "formType": 1,
  "platform": "朋友圈",
  "frequency": 1,
  "targetScope": 1,
  "targetIds": [],
  "judgeType": 1,
  "startAt": "2026-09-23 00:00:00",
  "endAt": "2026-10-31 23:59:59"
}
```

### 5.4 请求示例：店长创建本店任务

```json
{
  "title": "本店周末活动宣传",
  "formType": 2,
  "contentPackageId": 1001,
  "platform": "小红书",
  "frequency": 2,
  "targetScope": 3,
  "targetIds": [3001],
  "judgeType": 2,
  "startAt": "2026-09-23 00:00:00",
  "endAt": "2026-10-31 23:59:59"
}
```

### 5.5 成功响应

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 20001,
    "tenantId": 1,
    "title": "每日发布一条朋友圈",
    "formType": 1,
    "contentPackageId": null,
    "platform": "朋友圈",
    "frequency": 1,
    "targetScope": 1,
    "targetIds": "[]",
    "judgeType": 1,
    "sourceTaskId": null,
    "createdBy": 10001,
    "createdLevel": 1,
    "status": 1,
    "startAt": "2026-09-23 00:00:00",
    "endAt": "2026-10-31 23:59:59"
  }
}
```

### 5.6 服务端行为

1. 校验当前用户角色，只允许 `HQ_ADMIN`、`REGION_ADMIN`、`OWNER` 创建。
2. 校验当前角色允许的任务范围。
3. 校验指定内容任务是否填写 `contentPackageId`。
4. 校验时间范围。
5. 创建任务并设置 `status=1`。
6. 对当前目标员工预生成当前周期的 `task_record`。

## 6. 修改任务

### 6.1 请求

```http
PUT /api/task/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

路径参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | `long` | 被修改任务 ID |

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `title` | `string` | 是 | 新标题 |
| `contentPackageId` | `long` | 条件必填 | 原任务为指定内容任务时必填 |
| `platform` | `string` | 否 | 新发布平台 |
| `startAt` | `datetime` | 否 | 新开始时间 |
| `endAt` | `datetime` | 否 | 新结束时间 |

示例：

```json
{
  "title": "每日发布一条节日主题朋友圈",
  "contentPackageId": 1002,
  "platform": "朋友圈",
  "startAt": "2026-09-24 00:00:00",
  "endAt": "2026-10-31 23:59:59"
}
```

### 6.2 修改规则

- `HQ_ADMIN` 修改任务：直接更新原任务。
- `REGION_ADMIN` 修改任务：直接更新原任务。
- `OWNER` 修改总部或区域任务：创建门店变体，不污染其他门店。
- 门店变体字段特征：
  - `sourceTaskId` 指向原任务。
  - `targetScope=3`。
  - `targetIds` 为当前店长的门店 ID。
  - `createdLevel=3`。
- 修改记录写入 `task_modify_log`，包括修改前后的字段快照。

### 6.3 成功响应

成功响应的 `data` 为最终任务对象：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 20002,
    "title": "每日发布一条节日主题朋友圈",
    "sourceTaskId": 20001,
    "targetScope": 3,
    "createdLevel": 3,
    "status": 1
  }
}
```

## 7. 启用或停用任务

### 7.1 请求

```http
PATCH /api/task/{id}/status
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "status": 2
}
```

| `status` | 含义 |
|---:|---|
| `1` | 启用 |
| `2` | 停用 |

成功响应：

```json
{
  "code": 0,
  "msg": "success"
}
```

停用任务后：

- 已完成的 `task_record` 保留。
- 未完成记录不再出现在生效任务查询和提醒逻辑中。
- 任务历史数据不删除。

## 8. 查询任务详情

### 8.1 请求

```http
GET /api/task/{id}
Authorization: Bearer <token>
```

### 8.2 成功响应

`data` 为 `Task` 对象，字段见“任务字段说明”。

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 20001,
    "tenantId": 1,
    "title": "每日发布一条朋友圈",
    "formType": 1,
    "frequency": 1,
    "targetScope": 1,
    "targetIds": "[]",
    "judgeType": 1,
    "createdBy": 10001,
    "createdLevel": 1,
    "status": 1,
    "createdAt": "2026-09-23 10:00:00",
    "updatedAt": "2026-09-23 10:00:00"
  }
}
```

## 9. 查询我的任务

### 9.1 请求

```http
GET /api/task/my
Authorization: Bearer <token>
```

无需请求参数。服务端根据当前用户的租户、门店、用户 ID 和任务目标范围过滤任务。

### 9.2 周期归属

| `frequency` | `periodDate` |
|---:|---|
| `1` | 当天日期，例如 `2026-09-23` |
| `2` | 当前周周一，例如 `2026-09-21` |
| `3` | 当前月第一天，例如 `2026-09-01` |

### 9.3 成功响应

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "id": 20001,
      "title": "每日发布一条朋友圈",
      "formType": 1,
      "contentPackageId": null,
      "platform": "朋友圈",
      "frequency": 1,
      "targetScope": 1,
      "judgeType": 1,
      "sourceTaskId": null,
      "createdLevel": 1,
      "status": 1,
      "periodDate": "2026-09-23",
      "recordStatus": 0,
      "publishRecordId": null
    }
  ]
}
```

`recordStatus`：

| 值 | 含义 |
|---:|---|
| `0` | 当前周期未完成 |
| `1` | 当前周期已完成 |

## 10. 查询修改留痕

### 10.1 请求

```http
GET /api/task/{id}/modify-logs
Authorization: Bearer <token>
```

仅 `HQ_ADMIN` 和 `REGION_ADMIN` 可以查询。

### 10.2 成功响应

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "id": 90001,
      "tenantId": 1,
      "taskId": 20002,
      "modifiedBy": 30001,
      "changeDetail": "{\"before\":{...},\"after\":{...}}",
      "createdAt": "2026-09-23 11:00:00"
    }
  ]
}
```

说明：`changeDetail` 当前以 JSON 字符串形式返回，包含 `before` 和 `after` 两个对象。

## 11. 门店任务看板

### 11.1 请求

```http
GET /api/task/store-board?storeId=3001&periodDate=2026-09-23
Authorization: Bearer <token>
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `storeId` | `long` | 是 | 门店组织 ID |
| `periodDate` | `date` | 是 | 周期统计日期，格式 `yyyy-MM-dd` |

权限：

- `HQ_ADMIN`、`REGION_ADMIN` 可以查询任意门店。
- `OWNER` 只能查询自己的门店。

### 11.2 成功响应

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "storeId": 3001,
    "periodDate": "2026-09-23",
    "expected": 12,
    "finished": 9,
    "completionRate": 0.75,
    "unfinished": [
      {
        "id": 80001,
        "tenantId": 1,
        "taskId": 20001,
        "userId": 40001,
        "storeId": 3001,
        "periodDate": "2026-09-23",
        "status": 0,
        "publishRecordId": null,
        "finishedAt": null
      }
    ]
  }
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `expected` | 当前门店当前周期应完成的任务记录数 |
| `finished` | 已完成任务记录数 |
| `completionRate` | `finished / expected`；`expected=0` 时返回 `0` |
| `unfinished` | 状态为 `0` 的未完成记录列表 |

查询看板时服务端会为当前门店有效任务和在职员工补齐当前周期的 `task_record`，保证分母可统计。

## 12. 一键提醒未完成员工

### 12.1 请求

```http
POST /api/task/remind
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "taskId": 20001,
  "periodDate": "2026-09-23"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `taskId` | `long` | 是 | 要提醒的任务 ID |
| `periodDate` | `date` | 否 | 周期日期；不传时根据任务频率使用当前周期 |

### 12.2 权限和行为

- `HQ_ADMIN`、`REGION_ADMIN` 可以提醒任务目标员工。
- `OWNER` 只能提醒目标范围包含自己门店的任务。
- 仅对当前周期未完成的员工写入 `message`。
- 普通员工和店长角色之外的组织关系不会被提醒。
- 返回实际写入的消息数量。

### 12.3 成功响应

```json
{
  "code": 0,
  "msg": "success",
  "data": 3
}
```

## 13. 发布上报与任务核销

### 13.1 请求

```http
POST /api/publish-record
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `workId` | `long` | 是 | 作品 ID，必须属于当前用户和租户 |
| `taskId` | `long` | 否 | 关联任务 ID；不传时只记录发布，不进行任务核销 |
| `platform` | `string` | 是 | 实际发布平台 |
| `proofUrl` | `string` | 否 | 截图凭证地址；任务 `judgeType=2` 时必填 |

示例：

```json
{
  "workId": 70001,
  "taskId": 20001,
  "platform": "朋友圈",
  "proofUrl": "https://cdn.example.com/proof/70001.png"
}
```

### 13.2 核销规则

1. 校验作品属于当前用户。
2. 校验任务存在且属于当前租户。
3. 校验任务为生效状态。
4. 校验当前时间处于任务有效期内。
5. 如果任务指定平台，则请求平台必须一致。
6. 校验当前用户在任务目标范围内。
7. `judgeType=2` 时必须提交 `proofUrl`。
8. 写入 `publish_record`。
9. 按任务频率计算当前 `periodDate`。
10. 使用唯一键 `(task_id, user_id, period_date)` 幂等完成 `task_record`。

### 13.3 成功响应

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 91001,
    "tenantId": 1,
    "workId": 70001,
    "userId": 40001,
    "taskId": 20001,
    "platform": "朋友圈",
    "proofUrl": "https://cdn.example.com/proof/70001.png",
    "createdAt": "2026-09-23 15:30:00"
  }
}
```

重复提交说明：

- 发布记录本身会按请求写入新的 `publish_record`。
- 对同一任务、同一用户、同一周期的 `task_record` 只允许从未完成变为已完成一次。
- 重复请求不会回退已完成状态。

## 14. 错误码

| 错误码 | 含义 | 常见场景 |
|---:|---|---|
| `0` | 成功 | 请求成功 |
| `1000` | 系统繁忙，请稍后重试 | 未处理的系统异常 |
| `1001` | 请求参数错误 | 参数校验失败、日期范围错误、枚举值错误 |
| `1002` | 数据不存在 | 任务不存在、租户不存在 |
| `2001` | 未登录或登录已过期 | 缺少或失效的 `Authorization` |
| `2003` | 没有操作权限 | 角色、租户、门店或任务目标范围不匹配 |
| `3001` | 额度不足 | 当前任务域暂不使用 |

参数校验失败示例：

```json
{
  "code": 1001,
  "msg": "title:任务标题不能为空；frequency:必须小于或等于3"
}
```

## 15. 业务流程

### 15.1 下发流程

```text
管理员创建任务
  ↓
校验角色和 targetScope
  ↓
写入 task
  ↓
为目标员工生成当前周期 task_record
  ↓
员工通过 GET /api/task/my 获取任务
```

### 15.2 店长修改上级任务

```text
店长修改总部/区域任务
  ↓
复制生成门店变体
  ↓
sourceTaskId 指向原任务
  ↓
记录 task_modify_log
  ↓
该门店优先展示自己的变体，其他门店继续使用原任务
```

### 15.3 发布核销流程

```text
员工创作并发布作品
  ↓
POST /api/publish-record
  ↓
校验作品、任务、平台、目标范围和凭证
  ↓
写入 publish_record
  ↓
按 frequency 计算 periodDate
  ↓
task_record 从 0 更新为 1
```

## 16. 当前实现注意事项

1. `V2__task_domain.sql` 已用于本地 `sys` 数据库创建任务域相关表。
2. 当前开发配置中的 Flyway 处于关闭状态，因为本机 MySQL 服务端版本为 `26.7.0`，项目使用的 Flyway `8.5.13` 无法识别该版本；本地迁移已手动执行。
3. `GET /api/task/{id}/modify-logs` 当前仅允许总部和区域管理员查看。
4. `TaskModifyLog.changeDetail` 当前以 JSON 字符串保存和返回。
5. `TaskBoardResponse.unfinished` 当前返回 `TaskRecord` 明细，不额外关联用户昵称、任务标题和门店名称。
6. 当前已实现任务发布核销和基础提醒，排行榜、荣誉徽章、全国多级下钻看板尚未提供独立接口。
