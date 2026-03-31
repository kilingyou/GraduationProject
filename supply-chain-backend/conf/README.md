# FISCO BCOS SDK 证书与配置

## 1. 从 CentOS 虚拟机复制 SDK 证书

在虚拟机上执行（假设节点部署在 ~/fisco/nodes/）：

```bash
# 查看节点 IP
cat ~/fisco/nodes/127.0.0.1/node0/config.ini | grep channel_listen_port

# 打包 SDK 证书
cd ~/fisco/nodes/127.0.0.1/sdk
tar czf ~/sdk-certs.tar.gz ca.crt sdk.crt sdk.key
```

将 `sdk-certs.tar.gz` 复制到 Windows 开发机并解压到本目录（`conf/`）：

```
conf/
  ca.crt        ← CA 根证书
  sdk.crt       ← SDK 通信证书
  sdk.key       ← SDK 私钥
  config.toml   ← 连接配置（已创建，需改 IP）
```

## 2. 修改 config.toml

编辑 `config.toml`，将 `peers` 改为虚拟机实际 IP 和 channel 端口：

```toml
[network]
peers=["192.168.1.100:20200", "192.168.1.100:20201"]
```

端口默认 20200，如有多个节点可配多个。

## 3. 部署智能合约

在虚拟机上使用 FISCO BCOS 控制台部署合约：

```bash
cd ~/fisco/console

# 将合约文件放入 contracts 目录
cp /path/to/SupplyChainTraceability.sol contracts/solidity/

# 启动控制台
bash start.sh

# 部署合约
[group:1]> deploy SupplyChainTraceability
```

部署成功后会输出合约地址，如：
```
contract address: 0x1234567890abcdef1234567890abcdef12345678
```

## 4. 配置合约地址

将合约地址填入后端 `application.yml`：

```yaml
scm:
  blockchain:
    mode: fisco
    fisco:
      contract-address: 0x1234567890abcdef1234567890abcdef12345678
```

## 5. 验证连接

启动 Spring Boot 后端，查看日志：
```
FISCO BCOS SDK initialized — group=1, peers=[192.168.x.x:20200]
```
