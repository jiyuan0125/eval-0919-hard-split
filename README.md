# friend-split — 记账 App 的朋友分摊模块

Java 21 · Spring Boot 3.3 · Maven · H2 内存库。在已有的「记流水」和「月度目标 +
进度条」之外,新增朋友分摊:一笔消费勾选参与的朋友 → 系统确定性地分钱 →
朋友确认后落到各自账上 → 谁还谁、结清 → 发现错误时以冲销记录修正,永不改写。

## 构建与测试

```bash
mvn test        # 38 个测试全绿,并在同一阶段执行 JaCoCo 80% 行覆盖门禁
```

测试配置固定了时钟(`2026-05`)、每个测试事务回滚,并开启了 SQL 级的
「只追加表守卫」(见下)。H2 为内存库,无需任何外部依赖。

依赖刻意保持最小:`spring-boot-starter-web / -validation / -data-jpa`、`h2`、
`spring-boot-starter-test`。不引入 Lombok、MapStruct、Security 等额外框架;
鉴权用最简的 `X-User-Id` 请求头表示当前用户。

## 分包结构(六个业务包)

| 包 | 职责 |
|---|---|
| `com.ledger.common` | 金额工具 `Money`、错误码/异常/全局异常处理 |
| `com.ledger.user` | 用户、好友关系、`X-User-Id` 当前用户解析 |
| `com.ledger.ledger` | 不可变流水(手工记账 + 分摊落账 + 冲销) |
| `com.ledger.goal` | 月度目标、本人/好友两套进度视图 |
| `com.ledger.split` | 分摊单、参与者份额、事件流、结清(核心新功能) |
| `com.ledger.config` | Clock、Web 配置、只追加表 SQL 守卫 |

## API 一览

所有接口以 `X-User-Id: <用户id>` 表示调用者。

- `POST /api/users`、`POST /api/users/me/friends`、`GET /api/users/me/friends`
- `POST /api/ledger/entries`、`GET /api/ledger/entries?month=2026-05`
- `POST /api/goals/me` 设置当月目标(可重复设置,upsert)
- `GET /api/goals/me/progress` —— **本人**:精确金额
- `GET /api/goals/users/{id}/progress` —— **好友**:仅整数百分比;非好友 `403`
- `POST /api/splits` —— 发起分摊,payer 为当前用户,body:
  `{"total":"100.00","participantIds":[2,3],"description":"dinner"}`
- `POST /api/splits/{id}/confirm` —— 参与者确认(payer 无需确认;最后一人确认时自动终态并落账)
- `POST /api/splits/{id}/withdraw` —— 未终态时非 payer 中途退出
- `POST /api/splits/{id}/cancel` —— 未终态时 payer 整笔取消
- `POST /api/splits/{id}/reverse` —— 终态后由 payer 发起冲销
- `POST /api/splits/{id}/settle` —— 非 payer 参与者把自己那份还给 payer(幂等)
- `GET /api/splits/{id}` —— 参与者查看分摊状态;`GET /api/splits/me/outstanding` —— 我还欠多少

## 精度规则:除不尽的一分钱去哪了

**全链路只用整数「分」(`long`),绝不使用 `double`/`float`。**
入参金额是元字符串,`BigDecimal.movePointRight(2)` + `HALF_UP` 转成分,
超过两位小数直接 `400`;API 输出再由分转回元。因此不存在浮点不确定性。

分钱算法(`Money.allocate`,等权分摊,但用加权整数运算把规则写明):

1. 每人先拿 `floor(总额 × 权重 / 总权重)` 分,纯整数除法;
2. 余下的分(一定少于人数)逐分发出,每一分给「整除时被舍掉的零头最大」的那个人;
3. 零头完全相同(如等分)时,**按用户 id 升序**把这一分给 id 最小的人。

因此结果完全确定、与 JVM/数据库无关,且份额之和与总额**一分不差**。例:

- 100.00 元 / 3 人 → `33.34, 33.33, 33.33`(id 最小的人吸收那 1 分)
- 0.01 元 / 3 人 → `0.01, 0.00, 0.00`

`MoneyTest.hundredOverThreeIsDeterministic...` 断言两次分配逐位相同且合计 10000 分。

## 状态流转、中途退出与"确认过的人怎么办"

分摊单**没有可更新的状态列**,状态完全由只追加的 `split_event` 事件流推导:

`INITIATED →(全部非 payer 确认)→ FINALIZED →(冲销)→ REVERSED`
`INITIATED →(退出/取消)→ VOIDED`

- **确认**:payer 作为发起人不确认;每个非 payer 一条 `CONFIRMED` 事件,重复确认幂等。
  所有人确认的同一事务内写 `FINALIZED` 并把每人份额落账(本人记
  `SPLIT_EXPENSE`,payer 记对应 `SPLIT_RECEIVABLE`)。
- **中途退出(非 payer,仅限 INITIATED)**:旧单**不做任何修改**,追加一条
  `VOIDED` 事件整体作废——旧单上此前的确认记录仍然保留、可审计,但不再有效;
  随后按剩余参与者**重新确定性分钱**,生成一张全新的「后继单」
  (`predecessor_id` 指向旧单),**所有人(包括之前已确认的人)必须在新单上重新确认**。
  终态之前没有任何人的账被动过,因此退出不需要账务更正。
- **payer 退出 = 整笔取消**:payer 不能 withdraw,只能 `cancel`,同样只是追加
  `VOIDED` 事件。
- 终态之后不允许 withdraw/cancel/再确认,只能走冲销。

## 冲销思路:只追加,不 UPDATE

`split_bill / split_participant / split_event / settlement / ledger_entry`
五张表在结构上就不可变:JPA 实体字段全部 `updatable = false`(目标表除外,
那是本人改自己的月度目标),仓储只暴露新增与查询,服务层不调用任何 merge/delete。

终态后发现金额错了(`reverse`,仅 payer 可发起):

1. 读取该单终态时生成的**原始流水行**(按 `split_id` 查,不重算金额);
2. 为每行在当月追加一条金额取负的 `REVERSAL` 流水(`ref_entry_id` 指向原行);
   若已有结清,把结清对应的两条收/付款流水也各自追加负数行;
3. 追加 `REVERSED` 事件。原单、原确认、原落账、原结清记录一字不改。

冲销后净影响为 0;需要正确版本就**新开一张正确的分摊单**走正常流程,
而不是改旧单。`ImmutabilityGuardTest` 注册了 Hibernate `StatementInspector`,
把针对这五张表的任何 `UPDATE/DELETE` 直接变成异常,在 SQL 层证明全生命周期
(发起→确认→终态→结清→冲销、退出、取消)只发生 INSERT。

## 结清模型

每个非 payer 参与者欠 payer 自己那一份(一人付款给同一人,避免多角债)。
`settle` 幂等:写一对 `SETTLEMENT_PAID`(债务人)/`SETTLEMENT_RECEIVED`(payer)
流水和一条不可变 `settlement` 记录。`GET /api/splits/me/outstanding` 汇总
当前用户在所有「已终态、未冲销、未结清」单上尚欠金额,逐单列出 payer。
结清与收付款不计入目标进度的"花费"(那只是搬钱),冲销净额同样不产生新花费。

## 目标进度隐私:本人看金额,好友只看百分比

- `GET /api/goals/me/progress` 返回 `target / spent / remaining / percentPrecise`
  (百分比精确到 0.01%),且 `/me` 永远以调用者为准,无法借它查别人。
- `GET /api/goals/users/{id}/progress` 只返回 **3 个字段**:`userId、month、percent`,
  其中 `percent` 是 **0–100 的整数**(`floor(spent/target×100)`,封顶 100),
  没有目标额、已花、剩余、小数百分比中的任何一个。非好友直接 `403`。

`GoalPrivacyTest` 专门反制"组合接口反推金额":

- 反射式遍历好友响应 JSON,断言字段集合恰为 `{userId, month, percent}`,
  并断言不含 `target/spent/remaining/amount*/*cents/percentPrecise` 等键,原始
  JSON 文本里连小数点都不出现;
- **同百分比、不同金额**的两个目标(1000 花 300 = 30%;900 花 270 = 30%)好友侧
  响应除公开 id 外不可区分,重复轮询结果逐字节相同——无法用差分或多次采样
  反推分母/分子;
- 非好友拿到的 `403` 错误体同样不含任何金额/百分比字段;
- 好友无法访问他人流水接口(没有跨用户路径,`/api/ledger` 永远是调用者自己),
  探测不存在的月份得到 `404` 而非带金额的空负载。
