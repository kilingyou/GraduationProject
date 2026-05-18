# 全流程可追溯的电子产品供应链管理系统

本项目是一个面向电子产品供应链场景的全流程追溯管理系统，覆盖供应商、制造商、组装商、经销商、监管方和终端用户等角色。系统通过业务数据、区块链存证和 IPFS 文件存储能力，实现从设计/BOM、生产、质检、组装、物流、销售、投诉、召回到报废的生命周期管理。

## 项目结构

```text
GraduationProjectFinall
├── supply-chain-backend          # 后端服务
│   ├── src/main/java/com/scm
│   │   ├── common                # 通用响应、异常、工具类等
│   │   ├── config                # Spring、安全、跨域等配置
│   │   ├── integration           # 区块链、IPFS 等外部集成
│   │   └── module                # 业务模块
│   ├── src/main/resources
│   │   ├── db                    # 数据库建表和补丁脚本
│   │   ├── mapper                # MyBatis XML 映射文件
│   │   └── application.yml       # 应用配置
│   └── pom.xml
├── supply-chain-frontend         # 前端项目
│   ├── src
│   │   ├── api                   # 接口请求封装
│   │   ├── router                # 路由配置
│   │   ├── store                 # Pinia 状态管理
│   │   ├── utils                 # 工具函数
│   │   └── views                 # 页面模块
│   └── package.json
└── conf                          # FISCO BCOS SDK、ABI、BIN、证书等配置
```

## 技术栈

### 后端

- Java 8
- Spring Boot 2.7.14
- Spring Security
- MyBatis-Plus
- MySQL 8.0
- Druid 连接池
- JWT 认证
- FISCO BCOS Java SDK
- IPFS 集成
- EasyExcel、iTextPDF

### 前端

- Vue 3
- Vite
- Vue Router
- Pinia
- Element Plus
- Axios
- ECharts / vue-echarts

## 核心功能

- 用户认证与权限管理：支持登录、注册、JWT 鉴权、RBAC 角色菜单权限。
- 供应商管理：资质审核、设计文档、BOM 管理、生产订单和不合格处置。
- 制造商管理：订单接收、生产批次、设备记录、质量检测和制造数据看板。
- 组装商管理：扫码验证、组装批次、组装记录、渠道流通和组装数据看板。
- 经销商管理：物流流转、库存管理、销售记录和流通追踪。
- 监管方管理：资质审核、抽检任务、召回管理、串货监控和审计日志。
- 终端用户：公开溯源查询、产品绑定、投诉反馈和报废登记。
- 可信追溯：支持将关键业务事件锚定到 FISCO BCOS，并结合 IPFS 保存文件或证明材料。

## 环境要求

- JDK 1.8+
- Maven 3.6+
- Node.js 16+
- npm 8+
- MySQL 8.0+
- 可选：FISCO BCOS 2.x 节点
- 可选：IPFS 节点

## 数据库初始化

1. 创建并初始化数据库：

```sql
source supply-chain-backend/src/main/resources/db/schema.sql;
```

2. 如需同步菜单、字段或历史补丁，可按需要执行 `supply-chain-backend/src/main/resources/db/` 下的补丁脚本。

3. 默认数据库配置位于：

```yaml
spring:
  datasource:
    druid:
      url: jdbc:mysql://localhost:3306/supply_chain_db
      username: root
      password: root
```

如本地账号密码不同，请修改 `supply-chain-backend/src/main/resources/application.yml`。

## 后端启动

进入后端目录：

```bash
cd supply-chain-backend
```

编译并运行：

```bash
mvn clean package
mvn spring-boot:run
```

后端默认启动地址：

```text
http://localhost:8088
```

## 前端启动

进入前端目录：

```bash
cd supply-chain-frontend
```

安装依赖并启动开发服务：

```bash
npm install
npm run dev
```

前端默认启动地址：

```text
http://localhost:5173
```

前端开发环境已配置代理，`/api` 请求会转发到：

```text
http://localhost:8088
```

## 区块链与 IPFS 配置

后端相关配置位于 `supply-chain-backend/src/main/resources/application.yml`：

```yaml
scm:
  ipfs:
    mode: node
    api-url: http://127.0.0.1:5001
    gateway: http://127.0.0.1:8080/ipfs/
  blockchain:
    mode: fisco
    fisco:
      config-file: conf/config.toml
      group-id: 1
      contract-address: "0xb628b9140bbd1d4ecccc991434db7025d18a7836"
      abi-path: conf/abi
      bin-path: conf/bin
```

如需连接真实链环境，请确认 `conf/` 下的 `config.toml`、证书、ABI、BIN 文件与本地 FISCO BCOS 节点配置一致。

## 常用命令

后端测试：

```bash
cd supply-chain-backend
mvn test
```

后端打包：

```bash
cd supply-chain-backend
mvn clean package
```

前端构建：

```bash
cd supply-chain-frontend
npm run build
```

前端预览：

```bash
cd supply-chain-frontend
npm run preview
```

## 访问说明

- 登录页：`/login`
- 注册页：`/register`
- 公开溯源查询：`/trace`
- 系统首页：`/dashboard`

不同角色登录后会看到对应菜单，例如供应商、制造商、组装商、经销商、监管方、终端用户和管理员菜单。

## 注意事项

- 当前配置中的数据库账号、JWT 默认密钥、合约地址等适合本地开发，生产环境请通过环境变量或安全配置替换。
- `target/`、`node_modules/` 等目录属于构建产物或依赖目录，不建议提交到版本库。
- 如果不启用真实 FISCO BCOS 或 IPFS 节点，需要根据后端集成实现调整对应模式或服务可用性。

## 项目声明

- 项目名称：基于区块链完整性保证的电子供应链系统
- 项目作者：庞可竣
- 作者单位：暨南大学网络空间安全学院
- 开发语言：Java
- 框架：SpringBoot
- 核心技术：区块链、智能合约、供应链