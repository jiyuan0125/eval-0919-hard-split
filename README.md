# splitbill — 记账 App 的朋友分摊模块

Java 21 · Spring Boot 3.3 · Maven 3.9 · H2 内存库。依赖克制：web / data-jpa / validation / h2 / test。

## 运行

```bash
mvn test          # 全部测试 + JaCoCo 覆盖率校验（行覆盖 >= 80%，当前约 94%）
mvn spring-boot:run
```

## 分包

| 包 | 职责 |
|---|---|
| `split` | 分摊单、参与者、确定性分账算法、确认/退出/结清/整单冲销 |
| `ledger` | append-only 账本流水与单笔冲销 |
| `goal` | 月度目标与进度视图（本人精确 / 好友粗粒度） |
| `user` / `common` | 用户、统一异常处理 |

## 分摊的精度规则

**全程整数分（cents），不碰浮点。** 规则只取决于输入 `(totalCents, userIds)`，与求值顺序、平台无关：

1. 付款人自己的份额在建单时固定为 `floor(total / (朋友数 + 1))`，之后永不改变。
2. 剩余池 `total - payerShare` 在朋友间均分：`base = pool / n`，余数 `r = pool % n`。
3. 朋友按 **userId 升序** 排序，前 `r` 人各多分 1 分。例：100.00 三人分（付款人+两友），
   付款人 33.33，朋友池 66.67 → userId 较小者 33.34，另一人 33.33。
   差的一分钱永远落在排序最前的人身上，不存在"随浮点心情"的分配。

**中途退出**：仅 `PENDING` 状态的参与者可退出。退出后——

- 已确认（CONFIRMED）的份额和付款人份额**冻结不变**；
- 剩余池 `total - payerShare - 已确认份额合计` 在仍处 PENDING 的朋友间按同一规则重分；
- 已确认者想退出不允许，只能走冲销修正（见下）。

**结清**：所有未退出者确认完毕 → 账单 `SETTLED`，为每位已确认朋友生成一条
`SETTLEMENT` 流水（谁付给付款人、多少分），即"谁还谁"的结清依据。
全部退出且无人确认 → 账单 `CANCELLED`。

## 冲销（reversal）思路

**确认过的记录一律不改、不删，修正只追加新记录。**

- 账本 `LedgerEntry` 是 append-only：没有 update/delete 路径，实体上也没有
  对应的写方法。
- 单笔修正：`POST /api/ledger/entries/{id}/reverse` 追加一条 `REVERSAL`
  流水，金额为原记录的相反数，`relatedEntryId` 指回原记录；原记录原样保留，
  净效果为零。同一记录只能被冲销一次，冲销记录本身不可再冲销。
- 整单修正：`POST /api/splits/{id}/reverse` 为该分摊单的所有流水逐条生成
  冲销记录，账单置为 `CANCELLED`。要改正金额，冲销后重新建一单即可。

## 目标进度条的隐私边界

`GET /api/goals/{id}/progress?viewerId=...`

- **本人**（viewerId == 目标属主）：返回 `targetCents / spentCents /
  remainingCents / percentExact` 精确数字。
- **好友**（其他任何人）：只返回一个整数字段 `{"percent": 25}`——
  向下取整并钳制在 [0,100] 的整数百分比，响应中**不存在任何金额字段**。

防反推由测试专门证明（`GoalProgressApiTest`）：

- 金额完全不同但整数百分比相同的两个目标，好友侧响应**逐字节相等**
  （`{"percent":25}`），无法区分目标额量级；
- 同一整数百分比桶内继续花钱，好友侧响应不变，无法推断花费增量；
- 99.996% 向下取整为 99，不会进位成 100 泄露"刚好达标"的信息。

## 主要接口

```
POST   /api/splits                      建分摊单 {payerId, title, totalCents, friendIds}
POST   /api/splits/{id}/confirm         确认 {userId}
POST   /api/splits/{id}/withdraw        退出 {userId}
POST   /api/splits/{id}/reverse         整单冲销 {reason}
GET    /api/splits/{id}                 查询账单
POST   /api/ledger/expenses             记一笔支出
POST   /api/ledger/entries/{id}/reverse 单笔冲销
POST   /api/goals                       建月度目标
GET    /api/goals/{id}/progress         进度（本人精确 / 好友整数百分比）
```
