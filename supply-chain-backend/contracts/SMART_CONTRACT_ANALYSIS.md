# SupplyChainTraceability 智能合约分析

本文档针对仓库内 **当前唯一 Solidity 合约** `SupplyChainTraceability.sol` 做结构与集成说明，并与 Spring Boot 后端的链上调用方式对照。

---

## 1. 基本信息

| 项目 | 说明 |
|------|------|
| 文件路径 | `supply-chain-backend/contracts/SupplyChainTraceability.sol` |
| 语言 / 版本 | Solidity `^0.4.25` |
| 目标链 | 注释写明面向 **FISCO BCOS 2.11.0**（与后端 `fisco-bcos-java-sdk` 2.x 一致） |
| 合约名 | `SupplyChainTraceability` |
| 后端集成 | `FiscoBcosBlockchainAnchorService`（`scm.blockchain.mode=fisco` 时）调用合约中的 **`anchor(string,string)`**；`StubBlockchainAnchorService` 为本地模拟 |

---

## 2. 设计目标（合约内注释归纳）

合约试图覆盖供应链追溯相关能力：

1. 通用业务数据锚定（`anchor`）
2. 供应商资质审批 / 吊销
3. 设备（ECID）注册与状态更新
4. 组装记录（SN ↔ ECID）
5. 物流流转事件
6. 终端销售登记
7. 报废登记
8. 投诉召回与抽检结果

**重要结论：** 后端业务代码在正常运行路径上 **主要、且几乎仅** 通过 `BlockchainAnchorService.anchor(bizType, payloadHash)` 写入链上，对应合约的 **第 1 节「通用锚定」**。其余 `registerDevice`、`logTransfer` 等 **专用函数目前未被 `FiscoBcosBlockchainAnchorService` 在默认锚定流程中调用**（SDK 虽提供 `sendTransaction` / `callContract` 供扩展）。

---

## 3. 模块结构说明

### 3.1 通用锚定（与后端一致）

- **状态：** `anchorCount`；按序号存储 `bizType`、`payloadHash`、`operator`、`timestamp`。
- **写入：** `anchor(string bizType, string payloadHash)`：自增 id，记录 `msg.sender` 与时间，发出 `AnchorEvent`。
- **读取：** `getAnchor(uint256 id)` 返回四元组。

**与 Java 的对应关系：**  
`FiscoBcosBlockchainAnchorService.anchor(...)` 加载 ABI 后调用合约方法 **`anchor`**，参数为业务类型字符串与载荷哈希字符串；返回值在 Java 侧取 **交易回执中的 `transactionHash`** 作为业务里保存的「链上凭证」，而不是 Solidity 里返回的 `uint256` 锚点 id（若需链上 id，需扩展解析 `TransactionResponse`）。

### 3.2 供应商资质

- `approvedSuppliers[address]`、`approveSupplier` / `revokeSupplier` / `isSupplierApproved` / `getSupplierQualHash`。
- **权限：** 无 `onlyOwner` 或角色校验，任意地址可调用。

### 3.3 设备（ECID）

- 以 **字符串 `ecid`** 为键的多份 `mapping`：订单、批次、类型、制造商地址、生产时间、状态。
- `registerDevice`：写入并把状态设为 `"REGISTERED"`；`updateDeviceStatus` 任意覆盖状态。
- **重复注册：** 未校验 `ecid` 是否已存在，再次 `registerDevice` 会覆盖原记录。

### 3.4 组装记录

- `createAssemblyRecord`：按 SN 存 JSON 字符串形式的 ECID 列表、批次号、固件版本、报告哈希等。
- `bindEcidToSn`：ECID → SN 反向映射。
- `bindEcidToSn` 与 `createAssemblyRecord` 无原子绑定约束，需链下保证一致性。

### 3.5 物流流转

- `logTransfer`：递增 `transferCount`，更新 `_snToCurrentOwner[sn]`，发出 `TransferEventLogged`。
- 不校验 `msg.sender` 是否为上一任货主，**任意地址**可写。

### 3.6 销售登记

- `registerSale` **仅发出事件** `SaleRegistered`，无持久化 storage（链上可查日志，但合约 storage 无销售明细表）。

### 3.7 报废

- `_decommissioned[sn]` + `decommissionProduct` / `isDecommissioned`。

### 3.8 召回与抽检

- `requestRecall`、`publishRecallNotice`、`logInspectionResult` **均以事件为主**，无复杂状态机存储。

---

## 4. 事件（Events）一览

| 事件 | 用途 |
|------|------|
| `AnchorEvent` | 通用锚定 |
| `SupplierApproved` / `SupplierRevoked` | 供应商资质 |
| `DeviceRegistered` / `DeviceStatusUpdated` | 设备 |
| `AssemblyRecordCreated` | 组装 |
| `TransferEventLogged` | 物流 |
| `SaleRegistered` | 销售（仅事件） |
| `ProductDecommissioned` | 报废 |
| `RecallRequested` / `RecallNoticePublished` / `InspectionResultLogged` | 监管与抽检 |

事件便于链下索引器或浏览器解析；是否与后端落库字段一一对应，取决于是否单独做同步服务（当前仓库未见自动同步脚本）。

---

## 5. 后端实际使用的 `bizType` 示例

以下由 Java 代码中 `blockchainAnchorService.anchor(...)` 归纳（用于理解链上 `AnchorEvent` 的 `bizType` 字段含义），**不完全等于**必须调用合约专用函数：

- 供应商 / 监管：`SUPPLIER_LICENSE`、`SUPPLIER_CERT`、`SUPPLIER_AUDIT_SUBMIT`、`SUPPLIER_APPROVE`、`SUPPLIER_REJECT`
- 生产订单：`PRODUCTION_ORDER`、`PRODUCTION_ORDER_CANCEL`
- 制造：`MANUFACTURING_AGREEMENT`、`PRODUCTION_BATCH_CREATE`、`PRODUCTION_ORDER_COMPLETE`
- 设备：`DEVICE_REGISTER`
- 质检：`MFG_QC_PASS`、`MFG_REJECT`
- 组装：`ASSEMBLY_BATCH_CREATE`、`ASSEMBLY_CREATE`、`ASSEMBLY_RECORD`
- 分销：`TRANSFER_EVENT`、收货相关锚点
- 销售：`SALE_REGISTER`
- 终端：`USER_PRODUCT_BIND`、召回/报废/抽检等

`payloadHash` 一般为业务侧拼接字段后做 **SHA-256 十六进制** 等摘要（具体见各 `*ServiceImpl`）。

---

## 6. 安全与架构评价

### 6.1 优点

- **单一入口锚定**：与「链上仅存哈希、明细在链下 + IPFS」的常见毕业设计/POC 模型一致，实现成本低。
- **职责分块清晰**：注释与函数分区便于后续按模块扩展链上逻辑。
- **FISCO 兼容**：Solidity 0.4.25 与 FISCO BCOS 2.x 生态常见版本匹配。

### 6.2 风险与局限

1. **无访问控制**：所有 `public` 方法任意账户可调用，供应商审批、设备注册、物流等均可能被恶意写入（真实生产需角色、白名单或可验证签名）。
2. **字符串 key 的 gas 与规范**：大量 `string` 映射，gas 与链上查询成本较高；ECID/SN 格式需链下严格校验。
3. **专用函数与后端脱节**：合约中 ECID/组装/物流等 storage 丰富，但当前主流程只用 `anchor`；若不上线对应 `sendTransaction` 调用，则**链上状态与「锚定哈希」所代表的链下事实**可能不一致。
4. **Solidity 0.4.25 较老**：无现代编译器部分检查；长期建议评估升级路径（需与节点/编译器版本统一）。
5. **`registerSale` 无 storage**：仅靠事件，依赖链下或浏览器长期保存日志。

---

## 7. 相关文件（便于联调）

| 路径 | 说明 |
|------|------|
| `supply-chain-backend/contracts/SupplyChainTraceability.sol` | 合约源码 |
| `supply-chain-backend/src/main/java/com/scm/integration/blockchain/FiscoBcosBlockchainAnchorService.java` | FISCO 调用 `anchor` |
| `supply-chain-backend/src/main/java/com/scm/integration/blockchain/StubBlockchainAnchorService.java` | 非 fisco 模式占位 |
| `supply-chain-backend/conf/` | `config.toml`、ABI/BIN 路径等（见 `application.yml` 中 `scm.blockchain.fisco.*`） |

---

## 8. 小结

- **当前智能合约**在代码仓库中体现为 **一个** `SupplyChainTraceability` 合约，**通用锚定**与后端 **已对接**。
- **扩展能力**（供应商、ECID、组装、物流等）已在合约层 **预留**，是否启用取决于是否在 Java 侧对 `sendTransaction` / `callContract` 做业务封装并与数据库/IPFS 流程对齐。
- 若课程或论文需要「合约完整落地」，建议在文档中明确：**链上存储的是摘要与事件，全量业务数据仍以链下数据库与文件存储为准**，并说明访问控制与安全加固为后续工作。

---

*文档生成自对仓库源码的静态分析，部署参数与节点版本以实际环境为准。*
