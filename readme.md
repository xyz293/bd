# 小AI · 门店营销内容平台 — 一期后端技术方案（简化实现版）

> 原则同前端：**设计完整，实现从简**。中间件只有 **MySQL + Redis**，不引入 MQ、不做分布式部署、不上微服务。单体单实例起步，架构上留好二期升级口。

---

## 一、总体架构

```
Spring Boot 单体（一个 jar，一台服务器）
├── 模块按 domain 分包（不拆服务）
└── 内嵌：定时任务（@Scheduled）+ 线程池（视频异步）

中间件：MySQL（主存储）+ Redis（缓存/会话/简单队列）
AI 供应商：HTTP API 直调（适配器封装）
文件：OSS 直传（前端拿签名直传，后端只存 URL）
```

**明确不引入**：
- ❌ MQ → 用「DB 任务表 + 线程池 + 轮询」替代
- ❌ 分布式锁/分布式部署 → 单实例，Redis setnx 兜底防重复提交即可
- ❌ 微服务/注册中心/配置中心 → 一个 jar + application.yml
- ❌ ES → 统计查询走 MySQL 聚合 + Redis 缓存报表结果

## 二、工程结构（单模块按包分层）

```
com.xiaoa
├── common        # 统一响应、错误码、异常、日志切面、租户拦截器
├── user          # 登录绑定、邀请码、RBAC、组织树
├── tenant        # 租户、开通、资产包挂载
├── quota         # 三级账户、扣费退款、流水
├── chat          # 对话创作（组装提示词、调 LLM、出3版）
├── media         # 图片/视频生成（任务表 + 线程池）
├── work          # 作品、成套关联、发布记录
├── task          # 每日任务、核销
├── asset         # 素材/话术/风格/内容包
├── compliance    # 敏感词检测、审核流
├── payment       # 虚拟支付回调、对公登记
├── stats         # 看板统计、埋点入库
└── message       # 站内消息
```

## 三、核心表设计（约 15 张）

| 表 | 关键字段 | 备注 |
|---|---|---|
| tenant | type, industry, asset_package_id, status | 租户绑行业+资产包 |
| org | tenant_id, type(品牌/区域/门店), parent_id | 组织树一层区域 |
| user | phone, openid, status | 手机号=主体，微信=钥匙 |
| user_org_role | user_id, org_id, role, data_scope | RBAC 核心 |
| invite_code | code, store_id, expire_at, used | 一次性邀请码 |
| quota_account | level(总池/门店/员工), owner_id, balance | 三级账户 |
| quota_flow | account_id, biz_type, amount, biz_id | 流水，biz_id 唯一索引做幂等 |
| chat_session / chat_message | 会话与消息 | 对话上下文 |
| media_task | type(图/视频), status, prompt, result_url, cost | **异步任务表，替代 MQ 的核心** |
| work | user_id, type, media_task_id, copywriting, status | 成套作品 |
| publish_record | work_id, platform, created_at | 「我已发布」核销依据 |
| task / task_record | 任务定义；核销记录（含截图凭证 URL） | |
| asset / asset_package | 素材、提示词、风格、内容包（version 字段） | 行业=配置 |
| audit_record | work_id, status, opinion | 先审后发 |
| payment_order | order_no, amount, channel, status | 虚拟支付+对公登记 |
| track_log | user_id, event, props, created_at | 埋点，只追加 |

## 四、关键技术实现（简化版）

### 1. 视频异步：任务表 + 线程池（替代 MQ）

```java
// 提交：扣费 → 落 media_task(PENDING) → 线程池执行
@PostMapping("/media/generate")
public Result<Long> generate(req) {
    quotaService.deduct(bizId, cost);            // 幂等扣费
    MediaTask t = mediaTaskRepo.save(PENDING);
    videoExecutor.execute(() -> videoWorker.run(t.getId())); // 本地线程池
    return t.getId();  // 前端拿 taskId 轮询
}

// 兜底：@Scheduled 每 5 分钟扫一次
// 把 PENDING 超过 10 分钟 / RUNNING 超过 30 分钟的任务标 FAILED 并退款
@Scheduled(fixedDelay = 300_000)
public void recoverStuckTasks() { ... }
```

- 单实例重启丢任务？定时扫描兜底会把它标失败并**自动退款**，用户重新发起即可——一期可接受
- 二期升级口：`videoWorker.run(taskId)` 换成发 MQ 消息，接口签名不变

### 2. 扣费：调用即扣、失败即退（幂等）

```java
@Transactional
public void deduct(String bizId, long amount, Long accountId) {
    // 1. 幂等：quota_flow 的 biz_id 唯一索引，重复插入直接捕获异常返回
    // 2. 余额检查 + UPDATE quota_account SET balance = balance - ? 
    //    WHERE id = ? AND balance >= ?（乐观扣减，影响行数=0 即余额不足）
    // 3. 插入流水
}
// 生成失败 → refund(bizId + ":refund")，同样幂等
```

不搞 TCC/消息最终一致——**本地事务 + 唯一索引幂等 + 定时对账**足够，且天然不会重复扣。

### 3. 多租户隔离：MyBatis 拦截器

登录态解析出 `tenantId` 放 ThreadLocal，MyBatis 拦截器自动给 SQL 拼 `tenant_id = ?`。所有业务表带 tenant_id 字段。**资产包/平台配置表不带**（平台全局共享）。

### 4. Redis 的使用范围（就这几件事）

| 用途 | Key 设计 | 说明 |
|---|---|---|
| 登录态 | `session:{token}` → userId/tenantId | TTL 7 天，替代分布式会话 |
| 防重复提交 | `lock:gen:{userId}:{scene}` setnx | 生成接口 3 秒内防连点 |
| 资产包缓存 | `asset:{packageId}:{version}` | 提示词/风格/词库，改配置后删 key |
| 看板报表缓存 | `report:{tenantId}:{date}` | TTL 5 分钟，管理端看板不用每次聚合 |
| 简单限流 | `rl:{userId}` INCR+EXPIRE | 单用户每分钟生成次数上限 |

**不用** Redis 做队列、不做分布式锁集群、不做多级缓存。

### 5. AI 适配器（一个接口，供应商可换）

```java
public interface AiClient {
    ChatResult chat(List<Message> messages, String prompt);   // LLM
    String generateImage(ImageReq req);
    String generateVideo(VideoReq req);   // 返回供应商 taskId
}
// 实现类：OpenAiClient / DoubaoClient / ...，@ConditionalOnProperty 按配置切换
```

- 提示词组装全在服务端：行业包模板 + 品牌覆盖 + 对话上下文，**响应里绝不带 prompt**
- 每次调用写 track_log（token 用量、成本），供成本监控

### 6. 对话创作（核心链路）

```
POST /chat {sessionId, content}
→ 存 chat_message
→ 组装提示词（行业包 + 上下文最近 10 轮）
→ AiClient.chat()
→ 敏感词检测（DFA 算法，词库从 Redis 读）
→ 返回回复；若引导完成则一次生成 3 版文案（数组返回）
```

同步调用，超时 30 秒；LLM 失败直接报错不扣费（先生成后扣费，文案场景扣费金额小）。

### 7. 支付

- **虚拟支付**：微信回调 → 验签 → `payment_order` 状态机（待支付→成功）→ 幂等入账总池（order_no 唯一索引）
- **对公登记**：超级后台表单录入 + 凭证附件 URL，人工确认后入账
- 对账：@Scheduled 每日拉微信账单比对，差异写告警消息给平台超管

### 8. 任务核销与审核

- 「我已发布」→ 插 publish_record → 找到匹配的进行中 task_record 标完成（需凭证的任务校验截图已传）
- 审核流就是 audit_record 状态机：待审→通过/驳回，驳回带意见，站内消息通知
- 店长改上级任务：task 表存 `source_task_id` + 修改留痕表，不级联影响总部原任务

## 五、接口约定

- 统一 `{code, msg, data}`；错误码：0 成功 / 1xxx 系统 / 2xxx 权限 / 3xxx 额度 / 4xxxx 合规
- 生成类：文案图片同步、视频异步（taskId + `GET /media/task/{id}` 轮询）
- 分页 `{pageNo, pageSize}` → `{list, total}`
- OSS：`POST /oss/sign` 发上传签名，前端直传
- 埋点：`POST /track` 批量上报，异步写库（线程池），失败静默

## 六、部署与运维（从简）

- 一台 4C8G 云服务器：`java -jar` + systemd 拉起；MySQL/Redis 用云托管实例
- 日志：logback 滚动文件 + 简单接一个日志平台（或先只留本地）
- 监控：Spring Boot Actuator + 一个告警脚本（进程挂了/磁盘满发通知）
- 备份：MySQL 云实例自动备份，够用
- 二期量起来了再考虑：多实例 + Nginx、任务表换 MQ、统计读分离

## 七、排期（后端 2 人，约 12 周）

| 周 | 内容 |
|---|---|
| W1-2 | 框架、租户/RBAC/邀请入店、登录态 |
| W3-4 | 三级账户 + 幂等扣费退款 + 流水 |
| W5-6 | 对话创作 + 提示词组装 + 敏感词 + 3版文案 |
| W7 | 图片生成（同步）+ OSS 签名直传 |
| W8 | 视频异步（任务表+线程池+兜底扫描） |
| W9 | 任务系统 + 核销 + 审核流 |
| W10 | 素材/资产包 CRUD + 埋点入库 |
| W11 | 虚拟支付 + 对公登记 + 对账定时任务 |
| W12 | 看板统计、联调、压测（生成接口限流验证） |

## 八、一期明确不做（后端）

- 不做 MQ、分布式锁、多实例部署、读写分离
- 不做 ES/数仓（统计 = MySQL 聚合 + Redis 缓存）
- 不做抖音 API 对接、第三方数据回流
- 不做自动化 A/B 提示词实验（埋点数据先攒着，人工分析）
- 不做按资产包计费（授权表先建着，计费二期）

**一句话总结**：一个 Spring Boot 单体 + MySQL + Redis，视频异步用「任务表+线程池+定时兜底」，扣费用「本地事务+唯一索引幂等+失败退款」，多租户靠 MyBatis 拦截器，AI 靠适配器接口——零分布式组件，但每处都留了二期升级口（换 MQ、加实例、拆服务都不用改接口）。
