# Tenant 模块接口文档

> 本文档根据 `src/main/java/com/xiaoa/tenant` 当前代码整理，覆盖租户、组织、认证、邀请码和用户角色接口。接口行为以当前实现为准，和 `prd.md` 中的规划存在差异的地方在文末单独说明。

## 1. 基础约定

### 1.1 接口前缀

```text
/api
```

例如：登录接口为 `POST /api/auth/login`。

### 1.2 统一响应结构

所有 Controller 正常返回统一包装对象：

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

- `code=0`：请求成功。
- `msg`：响应说明。
- `data`：业务数据。无业务数据时通常不返回 `data` 字段。
- 参数校验失败、业务异常和系统异常也使用相同结构。

错误示例：

```json
{
  "code": 2004,
  "msg": "账号未入店，请使用邀请码入店"
}
```

### 1.3 登录态

除明确标记为“无需登录”的接口外，使用登录接口返回的 `token` 请求：

```http
Authorization: Bearer <token>
```

服务端会从登录态中解析以下身份信息：`userId`、`tenantId`、`orgId`、`role`、`dataScope`。

当前配置还支持请求头 `X-Tenant-Id`（可通过 `xiaoa.tenant.header-name` 配置），主要用于租户上下文联调；业务登录后应以 token 中的租户身份为准。

### 1.4 通用枚举

#### 租户类型 `type`

| 值 | 含义 |
|---:|---|
| 1 | 企业版 |
| 2 | 个人版 |

#### 租户状态 `status`

| 值 | 含义 |
|---:|---|
| 1 | 正常 |
| 2 | 停用 |
| 3 | 到期（规划值；当前登录逻辑主要通过 `expireAt` 判断到期） |

#### 组织类型 `type`

| 值 | 含义 |
|---:|---|
| 1 | 品牌 |
| 2 | 区域 |
| 3 | 门店 |

#### 角色 `role`

| 角色 | 说明 | 当前是否可执行管理员操作 |
|---|---|:---:|
| `HQ_ADMIN` | 总部管理员，全域管理 | 是 |
| `REGION_ADMIN` | 区域管理员 | 是 |
| `OWNER` | 店长 | 是 |
| `VIEWER` | 查看型管理员，只读 | 否 |
| `STAFF` | 普通员工 | 否 |

#### 数据范围 `dataScope`

| 值 | 含义 |
|---:|---|
| 1 | 全域 |
| 2 | 本区域 |
| 3 | 本店 |
| 4 | 本人 |

## 2. 接口总览

| 模块 | 方法 | 路径 | 登录要求 | 说明 |
|---|---|---|---|---|
| 租户 | `POST` | `/api/tenants/open` | 无需登录 | 开通租户并初始化品牌、管理员账号 |
| 租户 | `GET` | `/api/tenants/{tenantId}` | 当前实现无需登录 | 查询租户详情 |
| 认证 | `POST` | `/api/auth/login` | 无需登录 | 根据微信标识登录 |
| 认证 | `POST` | `/api/auth/join` | 无需登录 | 使用邀请码入店并登录 |
| 认证 | `POST` | `/api/auth/takeover` | 无需登录 | 手机号验证后更换绑定微信 |
| 认证 | `POST` | `/api/auth/logout` | token 可选 | 删除当前登录态 |
| 认证 | `GET` | `/api/auth/me` | 需要登录 | 查询当前登录身份 |
| 邀请码 | `POST` | `/api/invites` | 管理员 | 创建门店员工邀请码 |
| 邀请码 | `GET` | `/api/invites/{code}` | 无需登录 | 校验邀请码 |
| 组织 | `POST` | `/api/orgs` | 管理员 | 创建组织节点 |
| 组织 | `GET` | `/api/orgs/tree` | 需要登录 | 查询当前租户组织树 |
| 组织 | `PATCH` | `/api/orgs/{orgId}/name` | 管理员 | 修改组织名称 |
| 用户角色 | `DELETE` | `/api/users/roles/{roleId}` | 管理员 | 将成员移出门店 |
| 用户账号 | `PATCH` | `/api/users/{userId}/disable` | 管理员 | 禁用用户账号 |
| 用户角色 | `PUT` | `/api/users/{userId}/roles` | 管理员 | 授予或更新用户角色 |
| 用户角色 | `PATCH` | `/api/users/roles/{roleId}` | 管理员 | 更新已有成员关系的角色 |

## 3. 租户接口

### 3.1 开通租户

**请求**

```http
POST /api/tenants/open
Content-Type: application/json
```

无需登录。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `name` | `string` | 是 | 租户名称 |
| `type` | `integer` | 是 | 租户类型，`1` 企业版，`2` 个人版 |
| `industry` | `string` | 是 | 行业标识，例如 `JEWELRY`、`BEAUTY` |
| `assetPackageId` | `long` | 否 | 行业资产包 ID |
| `adminPhone` | `string` | 是 | 管理员手机号，格式为中国大陆 11 位手机号 |
| `adminOpenid` | `string` | 否 | 管理员微信标识 |
| `adminNickname` | `string` | 否 | 管理员昵称 |
| `expireAt` | `datetime` | 否 | 租户到期时间，格式建议为 `yyyy-MM-dd HH:mm:ss` |

**请求示例**

```json
{
  "name": "小AI珠宝品牌",
  "type": 1,
  "industry": "JEWELRY",
  "assetPackageId": 1001,
  "adminPhone": "13800138000",
  "adminOpenid": "wx-openid-001",
  "adminNickname": "管理员",
  "expireAt": "2027-09-23 23:59:59"
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "tenantId": 1,
    "orgId": 10,
    "userId": 100
  }
}
```

**处理逻辑**

1. 校验管理员手机号不存在。
2. 创建租户，初始状态为正常（`status=1`）。
3. 创建一个顶级品牌节点（`parentId=0`、`type=1`）。
4. 创建管理员用户。
5. 创建管理员与品牌节点的关系，角色为 `HQ_ADMIN`，数据范围为全域（`dataScope=1`）。
6. 整个过程在一个事务中执行。

**主要异常**

- `1003`：管理员手机号已存在。
- `1001`：请求参数校验失败。

### 3.2 查询租户详情

**请求**

```http
GET /api/tenants/{tenantId}
```

当前 Controller 和 Service 未强制校验登录态及当前用户是否属于该租户。

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `tenantId` | `long` | 租户 ID |

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "小AI珠宝品牌",
    "type": 1,
    "industry": "JEWELRY",
    "assetPackageId": 1001,
    "status": 1,
    "expireAt": "2027-09-23 23:59:59",
    "createdAt": "2026-09-23 10:00:00",
    "updatedAt": "2026-09-23 10:00:00"
  }
}
```

**主要异常**

- `1002`：租户不存在。

## 4. 认证接口

### 4.1 微信登录

**请求**

```http
POST /api/auth/login
Content-Type: application/json
```

无需登录。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `openid` | `string` | 是 | 微信用户标识。当前后端直接接收该字段，未在本模块实现 `wx.login code` 换取 openid |

**请求示例**

```json
{
  "openid": "wx-openid-001"
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "token": "9d0f...",
    "userId": 100,
    "tenantId": 1,
    "orgId": 10,
    "role": "HQ_ADMIN",
    "dataScope": 1,
    "tenantStatus": 1,
    "tenantName": "小AI珠宝品牌",
    "orgName": "小AI珠宝品牌"
  }
}
```

**登录校验**

- 按 `openid` 查询用户。
- 用户不存在：返回 `2004 NEED_JOIN`，引导用户使用邀请码入店。
- 用户已禁用：返回 `2006 USER_DISABLED`。
- 用户没有有效成员关系：返回 `2004 NEED_JOIN`。
- 租户停用：返回 `2003 FORBIDDEN`。
- 租户到期：返回 `2007 TENANT_EXPIRED`。
- 一个用户存在多条有效成员关系时，当前实现取查询结果的第一条关系生成登录态。

### 4.2 邀请码入店

**请求**

```http
POST /api/auth/join
Content-Type: application/json
```

无需登录。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `code` | `string` | 是 | 邀请码 |
| `phone` | `string` | 是 | 手机号，格式为中国大陆 11 位手机号 |
| `openid` | `string` | 是 | 微信用户标识 |
| `nickname` | `string` | 否 | 用户昵称 |

**请求示例**

```json
{
  "code": "A1B2C3D4E5F6",
  "phone": "13900139000",
  "openid": "wx-openid-002",
  "nickname": "员工A"
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "login": {
      "token": "ab12...",
      "userId": 101,
      "tenantId": 1,
      "orgId": 20,
      "role": "STAFF",
      "dataScope": 4,
      "tenantStatus": 1,
      "tenantName": "小AI珠宝品牌",
      "orgName": "城南门店"
    },
    "storeId": 20
  }
}
```

**处理逻辑**

1. 校验邀请码并尝试核销，邀请码只能使用一次且必须未过期。
2. 按微信标识查找用户；不存在时再按手机号查找或创建用户。
3. 手机号已绑定其他微信时，不自动覆盖，要求走接管流程。
4. 用户不能已有有效成员关系。
5. 创建门店成员关系，角色取邀请码角色，当前创建邀请码接口实际固定为 `STAFF`，数据范围固定为 `4`（本人）。
6. 签发登录态。

**主要异常**

- `2008`：邀请码无效。
- `2009`：邀请码已使用。
- `2010`：邀请码已过期。
- `2011`：当前微信已加入门店。
- `2012`：手机号已绑定其他微信。
- `2006`：账号已被禁用。

### 4.3 更换微信接管账号

**请求**

```http
POST /api/auth/takeover
Content-Type: application/json
```

无需登录。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `phone` | `string` | 是 | 已有账号绑定的手机号 |
| `openid` | `string` | 是 | 新微信标识 |

**请求示例**

```json
{
  "phone": "13900139000",
  "openid": "wx-openid-new"
}
```

**成功响应**

响应结构与“微信登录”相同，为 `TokenResponse`。

**处理逻辑**

- 手机号对应账号不存在：返回 `1002`。
- 新微信已绑定其他账号：返回 `1003`。
- 新微信已是当前账号绑定的微信：直接重新登录，不新增接管记录。
- 更换微信时写入 `user_wechat_bind`，`action=3`，然后更新用户当前 `openid`。

### 4.4 退出登录

**请求**

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

当前接口不强制要求 token；没有 token 时也返回成功。

**成功响应**

```json
{
  "code": 0,
  "msg": "success"
}
```

服务端会删除 Redis 中的 `session:{token}`；Redis 不可用时同时清理本地降级登录态。

### 4.5 查询当前登录身份

**请求**

```http
GET /api/auth/me
Authorization: Bearer <token>
```

需要登录。

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "userId": 100,
    "tenantId": 1,
    "orgId": 10,
    "role": "HQ_ADMIN",
    "dataScope": 1
  }
}
```

## 5. 邀请码接口

### 5.1 创建邀请码

**请求**

```http
POST /api/invites
Authorization: Bearer <token>
Content-Type: application/json
```

需要管理员角色：`HQ_ADMIN`、`REGION_ADMIN` 或 `OWNER`。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `storeId` | `long` | 是 | 门店组织 ID，必须对应 `type=3` 的组织 |
| `expireAt` | `datetime` | 是 | 未来时间，邀请码过期时间 |
| `role` | `string` | 否 | DTO 默认值为 `STAFF`；当前 Service 创建时固定写入 `STAFF`，该字段暂未实际生效 |

**请求示例**

```json
{
  "storeId": 20,
  "expireAt": "2026-10-01 23:59:59",
  "role": "STAFF"
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 500,
    "storeId": 20,
    "code": "A1B2C3D4E5F6",
    "role": "STAFF",
    "expireAt": "2026-10-01 23:59:59"
  }
}
```

**权限规则**

- `HQ_ADMIN` 可以为当前租户的任意门店创建邀请码。
- `OWNER` 只能为自己所属门店创建邀请码。
- `REGION_ADMIN` 当前实现同样要求目标门店 ID 等于自身 `orgId`，没有自动放开到区域子树。
- 目标组织不存在或不是门店：`1002`。

### 5.2 校验邀请码

**请求**

```http
GET /api/invites/{code}
```

无需登录。

**成功响应**

响应结构与“创建邀请码”相同，返回 `InviteResponse`。校验只读取邀请码，不会将其标记为已使用。

**校验规则**

- 邀请码不存在：`2008 INVITE_INVALID`。
- `used=1`：`2009 INVITE_USED`。
- `expireAt` 早于当前时间：`2010 INVITE_EXPIRED`。

真正入店时由 `/api/auth/join` 执行原子核销。

## 6. 组织接口

### 6.1 创建组织节点

**请求**

```http
POST /api/orgs
Authorization: Bearer <token>
Content-Type: application/json
```

需要管理员角色。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `type` | `integer` | 是 | `1` 品牌、`2` 区域、`3` 门店 |
| `name` | `string` | 是 | 组织名称，不能为空白 |
| `parentId` | `long` | 否 | 上级组织 ID；不传时默认为 `0`，表示顶级节点 |

**请求示例**

```json
{
  "type": 3,
  "name": "城南门店",
  "parentId": 11
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 20,
    "tenantId": 1,
    "parentId": 11,
    "type": 3,
    "name": "城南门店",
    "createdAt": "2026-09-23 11:00:00",
    "updatedAt": "2026-09-23 11:00:00"
  }
}
```

**权限规则**

- 上级组织不属于当前租户或不存在：`1002`。
- `OWNER` 只能创建门店节点（`type=3`）。
- `HQ_ADMIN` 和 `REGION_ADMIN` 可以创建 `type=1/2/3` 节点，当前实现未进一步校验组织层级合法性。

### 6.2 查询组织树

**请求**

```http
GET /api/orgs/tree
Authorization: Bearer <token>
```

需要登录。

**成功响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "id": 10,
      "parentId": 0,
      "type": 1,
      "name": "小AI珠宝品牌",
      "children": [
        {
          "id": 11,
          "parentId": 10,
          "type": 2,
          "name": "华东区域",
          "children": [
            {
              "id": 20,
              "parentId": 11,
              "type": 3,
              "name": "城南门店",
              "children": []
            }
          ]
        }
      ]
    }
  ]
}
```

当前 Service 查询当前登录租户下全部组织并组装树，未在 Service 层按 `dataScope` 裁剪返回节点。

### 6.3 修改组织名称

**请求**

```http
PATCH /api/orgs/{orgId}/name
Authorization: Bearer <token>
Content-Type: application/json
```

需要管理员角色。

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `orgId` | `long` | 待修改的组织 ID |

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `name` | `string` | 是 | 新组织名称，不能为空白 |

**请求示例**

```json
{
  "name": "城南旗舰店"
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success"
}
```

**权限规则**

- `HQ_ADMIN`：可以修改当前租户组织。
- `REGION_ADMIN`：当前实现可通过 `canManageOrg` 校验。
- `OWNER`：只能修改自身 `orgId` 对应的组织。
- 组织不存在：`1002`；无权限：`2003`。

## 7. 用户和角色接口

### 7.1 移除门店成员

**请求**

```http
DELETE /api/users/roles/{roleId}
Authorization: Bearer <token>
```

需要管理员角色。

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `roleId` | `long` | `user_org_role` 关系 ID，不是用户 ID |

**成功响应**

```json
{
  "code": 0,
  "msg": "success"
}
```

接口将该成员关系的 `status` 更新为 `2`（已移除），不删除用户及历史数据。成员下次登录时不会再按该关系生成登录态。

**权限规则**

- `HQ_ADMIN`：可移除当前租户成员关系。
- `REGION_ADMIN`：可通过管理员校验，当前 Service 未额外限制组织范围。
- `OWNER`：只能移除自己门店的成员关系。
- `STAFF`、`VIEWER`：无权操作。

### 7.2 禁用用户账号

**请求**

```http
PATCH /api/users/{userId}/disable
Authorization: Bearer <token>
```

需要管理员角色。

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `userId` | `long` | 用户 ID |

**成功响应**

```json
{
  "code": 0,
  "msg": "success"
}
```

接口将用户的 `status` 更新为 `2`（禁用）。用户后续登录或通过接管流程登录时会被拦截。

**主要异常**

- `1002`：用户不存在。
- `2003`：当前用户不是管理员。

> 注意：当前实现只校验调用者是否为管理员，没有进一步限制 `REGION_ADMIN` 或 `OWNER` 的目标用户范围。

### 7.3 授予或更新用户角色

**请求**

```http
PUT /api/users/{userId}/roles
Authorization: Bearer <token>
Content-Type: application/json
```

需要管理员角色。

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `userId` | `long` | 被授权用户 ID |

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `orgId` | `long` | 是 | 授权所属组织 ID |
| `role` | `string` | 是 | 角色名称，如 `OWNER`、`REGION_ADMIN`、`VIEWER`、`STAFF` |
| `dataScope` | `integer` | 是 | 数据范围：`1` 全域、`2` 本区域、`3` 本店、`4` 本人 |

**请求示例**

```json
{
  "orgId": 20,
  "role": "OWNER",
  "dataScope": 3
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success"
}
```

**处理逻辑和权限**

- 用户或组织不存在：`1002`。
- 目标组织必须属于当前登录租户。
- `HQ_ADMIN` 可为当前租户用户授权。
- 非 `HQ_ADMIN` 只能为自身 `orgId` 对应的组织授权。
- 如果相同用户、组织、角色关系已存在，则更新该关系的 `dataScope`；否则新增关系。

### 7.4 更新已有成员关系角色

**请求**

```http
PATCH /api/users/roles/{roleId}
Authorization: Bearer <token>
Content-Type: application/json
```

需要管理员角色。

**路径参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| `roleId` | `long` | `user_org_role` 关系 ID |

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `orgId` | `long` | 是 | DTO 要求传入的组织 ID；当前更新逻辑不使用该字段 |
| `role` | `string` | 是 | 新角色 |
| `dataScope` | `integer` | 是 | 新数据范围 |

**请求示例**

```json
{
  "orgId": 20,
  "role": "VIEWER",
  "dataScope": 1
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "success"
}
```

**权限规则**

- 关系不存在或不属于当前租户：`1002`。
- `HQ_ADMIN` 可更新当前租户关系。
- 其他管理员只能更新自身 `orgId` 对应的关系。
- 无权限：`2003`。

## 8. 错误码

| 错误码 | 常量 | 含义 |
|---:|---|---|
| 0 | `SUCCESS` | 成功 |
| 1000 | `SYSTEM_ERROR` | 系统繁忙，请稍后重试 |
| 1001 | `INVALID_PARAMETER` | 请求参数错误 |
| 1002 | `NOT_FOUND` | 数据不存在 |
| 1003 | `DUPLICATE` | 数据已存在 |
| 2001 | `UNAUTHORIZED` | 未登录或登录已过期 |
| 2003 | `FORBIDDEN` | 没有操作权限 |
| 2004 | `NEED_JOIN` | 账号未入店，请使用邀请码入店 |
| 2005 | `USER_REMOVED` | 你已被移出门店 |
| 2006 | `USER_DISABLED` | 账号已被禁用 |
| 2007 | `TENANT_EXPIRED` | 租户已到期，请续费 |
| 2008 | `INVITE_INVALID` | 邀请码无效 |
| 2009 | `INVITE_USED` | 邀请码已被使用 |
| 2010 | `INVITE_EXPIRED` | 邀请码已过期 |
| 2011 | `ALREADY_JOINED` | 当前微信已加入门店 |
| 2012 | `PHONE_ACCOUNT_EXISTS` | 手机号已绑定其他微信，请使用接管流程 |

## 9. 典型业务流程

### 9.1 新用户入店

```text
管理员创建门店
    ↓
管理员创建邀请码 POST /api/invites
    ↓
员工校验邀请码 GET /api/invites/{code}（可选）
    ↓
员工提交手机号、openid POST /api/auth/join
    ↓
服务端创建 user_org_role（STAFF、本人范围）
    ↓
返回 token，员工进入系统
```

### 9.2 已入店用户登录

```text
微信端获得 openid
    ↓
POST /api/auth/login
    ↓
按 openid 查用户和有效成员关系
    ↓
校验用户状态、租户状态和到期时间
    ↓
返回 token、租户、组织、角色和数据范围
```

### 9.3 员工更换微信

```text
新微信携带手机号 POST /api/auth/takeover
    ↓
按手机号定位原账号
    ↓
写入 user_wechat_bind（action=3）
    ↓
更新 user.openid
    ↓
返回新的登录态，原用户数据和成员关系保留
```

## 10. 当前实现与 PRD 的差异及建议

1. **开通租户流程较简化**：当前 `/api/tenants/open` 无论租户类型都创建“顶级品牌 + HQ_ADMIN”，尚未实现 PRD 中个人版自动创建门店、支付回调和额度账户。
2. **微信登录链路未接入微信 API**：当前请求直接传 `openid`，没有实现 `wx.login code` 换取 openid 和手机号授权解密。
3. **邀请码角色暂时固定**：`CreateInviteRequest` 虽然有 `role` 字段，但 `InviteService` 创建时固定写入 `STAFF`。
4. **组织树未按数据范围裁剪**：`GET /api/orgs/tree` 查询当前租户全部组织，尚未实现 PRD 中按 `dataScope` 自动过滤区域、门店或个人数据。
5. **到期状态判断不完全一致**：PRD 规划了 `tenant.status=3` 到期状态；当前登录逻辑明确拦截 `status=2`，并根据 `expireAt` 判断到期，没有单独按 `status=3` 拦截。
6. **租户详情缺少访问控制**：`GET /api/tenants/{tenantId}` 当前未调用权限服务，建议至少校验登录用户属于目标租户，或明确该接口仅供平台侧调用。
7. **用户管理的范围校验仍需收紧**：禁用用户、移除成员和区域管理员的可操作范围，当前实现没有完全按照 PRD 的组织树和数据范围约束。
8. **角色和数据范围缺少枚举校验**：角色字符串及 `dataScope` 数值目前只做非空校验，建议增加白名单校验，避免写入非法权限组合。
9. **错误响应存在特殊情况**：非法 `X-Tenant-Id` 会由过滤器直接返回 HTTP 400，可能不是统一的 `{code,msg,data}` 格式。
10. **当前未提供成员查询接口**：用户控制器只提供移除、禁用、授权和角色更新，尚未提供成员列表、成员详情和成员关系查询接口。

## 11. 相关代码位置

- Controller：`src/main/java/com/xiaoa/tenant/controller`
- DTO：`src/main/java/com/xiaoa/tenant/dto`
- Service：`src/main/java/com/xiaoa/tenant/service`
- Model：`src/main/java/com/xiaoa/tenant/model`
- 统一响应：`src/main/java/com/xiaoa/common/api/Result.java`
- 认证上下文：`src/main/java/com/xiaoa/common/auth`
- 错误码：`src/main/java/com/xiaoa/common/exception/ErrorCode.java`
