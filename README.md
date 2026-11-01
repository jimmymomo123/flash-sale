# Flash Sale

高併發搶購與庫存扣減系統。從單機核心邏輯與 TDD 開始，逐步加入安全、快取、訊息佇列、網關、壓測、容器化與雲端 CI/CD。

## 技術棧

| 類別 | 技術 | 狀態 |
|---|---|---|
| 語言 / 框架 | Java 21、Spring Boot 4.1、Spring Data JPA | ✅ |
| 測試 | JUnit 5、Mockito（TDD） | 🚧 Phase 1 |
| 資料庫 | H2（開發）→ MySQL 8（Phase 3） | ✅ / ⏳ |
| 安全 | Spring Security、JWT、Filter、Interceptor、AOP | ⏳ Phase 2 |
| 快取 / 鎖 | Redis、Lua、Redisson | ⏳ Phase 3 |
| 非同步 | Kafka（手動 commit、冪等消費、DLQ） | ⏳ Phase 3 |
| 網關 / 壓測 | Nginx（負載均衡、限流）、JMeter | ⏳ Phase 4 |
| 部署 | Docker、docker-compose、AWS EC2、Cloudflare、GitHub Actions | ⏳ Phase 5–7 |

## 開發路線

- [ ] **Phase 1**：核心業務與 TDD
- [ ] **Phase 2**：Spring Security + JWT、Filter / Interceptor（TraceId、限流）、`@RequireLock` / `@Idempotent`（AOP）
- [ ] **Phase 3**：MySQL 100 萬筆資料 + `EXPLAIN` 索引調優、Redis 快取防護與 Lua 預扣庫存、Kafka 非同步落庫
- [ ] **Phase 4**：Nginx 反向代理與限流、JMeter 壓測（直接寫 DB vs Kafka 非同步）
- [ ] **Phase 5**：Multi-stage Dockerfile、docker-compose 一鍵啟動
- [ ] **Phase 6**：AWS EC2 + 自訂網域 HTTPS
- [ ] **Phase 7**：GitHub Actions CI/CD

## 業務規則

- 只能在活動的 `startTime` 到 `endTime` 之間購買。
- 一筆訂單只包含一種商品，可以購買多件（`quantity`）。
- **限購由營運端按商品設定**（`stock.limit_per_user`）：
  - `null`：不限購
  - `N`：每位使用者在此活動中，此商品累計最多購買 N 件
- 同一個活動中，使用者可以購買不同的商品，也可以分多次購買，只要不超過限購。

## 資料模型

| 表 | 說明 | 重點欄位 / 約束 |
|---|---|---|
| `activity` | 搶購活動 | `start_time`、`end_time`、`status` |
| `product` | 商品 | `price DECIMAL(10,2)` |
| `stock` | 某活動中某商品的庫存 | `total_quantity`、`available_quantity`、`limit_per_user`、`version`（樂觀鎖）；唯一鍵 `(activity_id, product_id)` |
| `orders` | 訂單 | `quantity`、`status`、`request_id`；唯一鍵 `(user_id, request_id)` |

## 設計決策

- **只存 ID，不用 JPA 關聯**：Entity 之間用 `activityId` / `productId` 關聯，不用 `@ManyToOne`，避免高併發下不小心觸發 N+1 查詢和 lazy loading。
- **Entity 不用 Lombok `@Data`**：`@Data` 產生的 `equals` / `hashCode` 會用到所有欄位，`id` 從 `null` 變成有值後 `hashCode` 就變了；`toString` 也可能觸發 lazy loading。Entity 改用 `@Getter` / `@Setter` / `@Builder`；DTO 才用 `@Data` 或 `record`。
- **金額用 `BigDecimal`**：避免浮點數誤差。
- **表名 `orders`**：`ORDER` 是 SQL 保留字（`ORDER BY`）。
- **表名、欄位一律小寫**：MySQL 在 Linux 上表名區分大小寫，小寫也是 MySQL 社群的慣例。
- **字串用 `VARCHAR`，不用 `NVARCHAR`**：MySQL 的 `VARCHAR(n)` 以「字元」計算長度（Oracle 的 `VARCHAR2(n)` 預設以 byte 計算，這點不同）。MySQL 的 `NVARCHAR` 其實是 `utf8mb3`，存不了 emoji，所以改用 `VARCHAR` + `utf8mb4`。
- **enum 存字串**（`@Enumerated(EnumType.STRING)`）：避免在 enum 中間插入新值後，舊資料的順序編號意思全錯。
- **限購可設定，而不是寫死在唯一鍵裡**：唯一鍵只能表達「最多 1 筆」，表達不了「最多 N 件」，所以限購改由應用層用 `SUM(quantity)` 檢查。
- **`@Version` 和冪等鍵各管各的**：`@Version` 防止同一筆庫存被同時 UPDATE（超賣）；冪等鍵 `(user_id, request_id)` 防止同一次購買被 INSERT 兩次。
- **Redis 防重之外，仍保留資料庫唯一鍵**：Redis key 會過期、主從切換可能掉資料、也無法和 DB 交易同步，所以資料庫唯一鍵作為最後一道防線。

## 已知限制與後續規劃

- **冪等下單**：Phase 2 隨 JWT 登入一起實作。後端在進入結帳頁時發放一次性 purchase token，綁定使用者、活動與過期時間；下單時原子消費 token，重送則回傳原訂單。`orders.request_id` 欄位與唯一鍵已預留（目前允許 `NULL`）。
- **併發下的限購檢查**：目前是「先查已購數量再寫入」，兩個請求同時進來可能都通過檢查而超過限購。Phase 2 以 Redisson 分散式鎖處理，Phase 3 改用 Redis 原子計數。
- **兩層限購**：目前只有單品限購。如有需要，可以在 `activity` 加上整個活動的總限購。
- **時區**：目前使用 `LocalDateTime`；部署到 AWS（預設 UTC）時要統一時區，或改用 `Instant` 存 UTC。
- **稽核欄位**：`create_by` / `update_by` 目前允許 `NULL`，Phase 2 有登入後改用 JPA Auditing 自動填入。

## 本機執行

需求：JDK 21

```bash
./mvnw spring-boot:run
```

- H2 Console：<http://localhost:8080/h2-console>（JDBC URL `jdbc:h2:mem:flashsale`，User `sa`，密碼留空）

```bash
./mvnw test
```
