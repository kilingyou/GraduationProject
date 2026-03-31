pragma solidity ^0.4.25;

/**
 * 全流程可追溯的电子产品供应链管理智能合约
 *
 * 部署在 FISCO BCOS 2.11.0 上，覆盖：
 *   1) 通用业务数据锚定（anchor）
 *   2) 供应商资质审批/吊销
 *   3) 设备（ECID）注册与状态更新
 *   4) 组装记录（SN ↔ ECID 映射）
 *   5) 物流流转事件
 *   6) 终端销售登记
 *   7) 报废登记
 *   8) 投诉召回 & 抽检结果
 *
 * 注意：Solidity 0.4.25 不支持 string key 的 public mapping 自动 getter，
 *       所有 string key mapping 均声明为 internal，通过显式函数访问。
 */
contract SupplyChainTraceability {

    // ================================================================
    //  1. 通用锚定 —— 现有后端 anchor(bizType, payloadHash) 的链上落点
    // ================================================================
    uint256 public anchorCount;

    mapping(uint256 => string)  internal _anchorBizTypes;
    mapping(uint256 => string)  internal _anchorPayloads;
    mapping(uint256 => address) internal _anchorOperators;
    mapping(uint256 => uint256) internal _anchorTimestamps;

    event AnchorEvent(
        uint256 indexed id,
        string  bizType,
        string  payloadHash,
        address operator,
        uint256 timestamp
    );

    function anchor(string bizType, string payloadHash) public returns (uint256) {
        anchorCount++;
        _anchorBizTypes[anchorCount]   = bizType;
        _anchorPayloads[anchorCount]   = payloadHash;
        _anchorOperators[anchorCount]  = msg.sender;
        _anchorTimestamps[anchorCount] = now;
        emit AnchorEvent(anchorCount, bizType, payloadHash, msg.sender, now);
        return anchorCount;
    }

    function getAnchor(uint256 id)
        public view
        returns (string, string, address, uint256)
    {
        return (
            _anchorBizTypes[id],
            _anchorPayloads[id],
            _anchorOperators[id],
            _anchorTimestamps[id]
        );
    }

    // ================================================================
    //  2. 供应商资质
    // ================================================================
    mapping(address => bool)   public approvedSuppliers;
    mapping(address => string) internal _supplierQualHash;

    event SupplierApproved(address indexed supplier, string qualHash, address approver, uint256 ts);
    event SupplierRevoked (address indexed supplier, address revoker, uint256 ts);

    function approveSupplier(address supplier, string qualHash) public {
        approvedSuppliers[supplier] = true;
        _supplierQualHash[supplier] = qualHash;
        emit SupplierApproved(supplier, qualHash, msg.sender, now);
    }

    function revokeSupplier(address supplier) public {
        approvedSuppliers[supplier] = false;
        emit SupplierRevoked(supplier, msg.sender, now);
    }

    function isSupplierApproved(address supplier) public view returns (bool) {
        return approvedSuppliers[supplier];
    }

    function getSupplierQualHash(address supplier) public view returns (string) {
        return _supplierQualHash[supplier];
    }

    // ================================================================
    //  3. 设备 (ECID) 注册
    // ================================================================
    mapping(string => string)  internal _deviceOrderId;
    mapping(string => string)  internal _deviceBatchId;
    mapping(string => string)  internal _deviceType;
    mapping(string => address) internal _deviceManufacturer;
    mapping(string => uint256) internal _deviceMfgTime;
    mapping(string => string)  internal _deviceStatus;

    event DeviceRegistered   (string ecid, string orderId, string batchId, string devType, address mfg, uint256 ts);
    event DeviceStatusUpdated(string ecid, string newStatus, address operator, uint256 ts);

    function registerDevice(string ecid, string orderId, string batchId, string devType) public {
        _deviceOrderId[ecid]      = orderId;
        _deviceBatchId[ecid]      = batchId;
        _deviceType[ecid]         = devType;
        _deviceManufacturer[ecid] = msg.sender;
        _deviceMfgTime[ecid]      = now;
        _deviceStatus[ecid]       = "REGISTERED";
        emit DeviceRegistered(ecid, orderId, batchId, devType, msg.sender, now);
    }

    function updateDeviceStatus(string ecid, string newStatus) public {
        _deviceStatus[ecid] = newStatus;
        emit DeviceStatusUpdated(ecid, newStatus, msg.sender, now);
    }

    function getDeviceStatus(string ecid) public view returns (string) {
        return _deviceStatus[ecid];
    }

    function getDeviceInfo(string ecid)
        public view
        returns (string, string, string, address, uint256, string)
    {
        return (
            _deviceOrderId[ecid],
            _deviceBatchId[ecid],
            _deviceType[ecid],
            _deviceManufacturer[ecid],
            _deviceMfgTime[ecid],
            _deviceStatus[ecid]
        );
    }

    // ================================================================
    //  4. 组装记录（SN ↔ ECID 映射）
    // ================================================================
    mapping(string => string)  internal _snToEcidListJson;
    mapping(string => string)  internal _ecidToSn;
    mapping(string => string)  internal _assemblyBatchNo;
    mapping(string => string)  internal _assemblyFwVer;
    mapping(string => string)  internal _assemblyReportHash;
    mapping(string => address) internal _assemblyOperator;
    mapping(string => uint256) internal _assemblyTime;

    event AssemblyRecordCreated(string sn, string batchNo, string ecidListJson, address assembler, uint256 ts);

    function createAssemblyRecord(
        string sn,
        string ecidListJson,
        string batchNo,
        string fwVersion,
        string reportHash
    ) public {
        _snToEcidListJson[sn]  = ecidListJson;
        _assemblyBatchNo[sn]   = batchNo;
        _assemblyFwVer[sn]     = fwVersion;
        _assemblyReportHash[sn]= reportHash;
        _assemblyOperator[sn]  = msg.sender;
        _assemblyTime[sn]      = now;
        emit AssemblyRecordCreated(sn, batchNo, ecidListJson, msg.sender, now);
    }

    function bindEcidToSn(string ecid, string sn) public {
        _ecidToSn[ecid] = sn;
    }

    function getSnEcidList(string sn) public view returns (string) {
        return _snToEcidListJson[sn];
    }

    function getEcidSn(string ecid) public view returns (string) {
        return _ecidToSn[ecid];
    }

    function getAssemblyInfo(string sn)
        public view
        returns (string, string, string, string, address, uint256)
    {
        return (
            _snToEcidListJson[sn],
            _assemblyBatchNo[sn],
            _assemblyFwVer[sn],
            _assemblyReportHash[sn],
            _assemblyOperator[sn],
            _assemblyTime[sn]
        );
    }

    // ================================================================
    //  5. 物流流转
    // ================================================================
    uint256 public transferCount;
    mapping(string => address) internal _snToCurrentOwner;

    event TransferEventLogged(
        uint256 indexed id,
        string  sn,
        string  trackingNo,
        address sender,
        address receiver,
        string  transferType,
        uint256 ts
    );

    function logTransfer(
        string  sn,
        string  trackingNo,
        address receiver,
        string  transferType
    ) public {
        transferCount++;
        _snToCurrentOwner[sn] = receiver;
        emit TransferEventLogged(transferCount, sn, trackingNo, msg.sender, receiver, transferType, now);
    }

    function getSnOwner(string sn) public view returns (address) {
        return _snToCurrentOwner[sn];
    }

    // ================================================================
    //  6. 销售登记
    // ================================================================
    event SaleRegistered(string sn, string customerHash, string invoiceHash, address seller, uint256 ts);

    function registerSale(string sn, string customerHash, string invoiceHash) public {
        emit SaleRegistered(sn, customerHash, invoiceHash, msg.sender, now);
    }

    // ================================================================
    //  7. 报废登记
    // ================================================================
    mapping(string => bool) internal _decommissioned;

    event ProductDecommissioned(string sn, string disposalMethod, address operator, uint256 ts);

    function decommissionProduct(string sn, string disposalMethod) public {
        _decommissioned[sn] = true;
        emit ProductDecommissioned(sn, disposalMethod, msg.sender, now);
    }

    function isDecommissioned(string sn) public view returns (bool) {
        return _decommissioned[sn];
    }

    // ================================================================
    //  8. 投诉召回 & 抽检
    // ================================================================
    event RecallRequested     (string sn, string faultType, string faultDesc, address requester, uint256 ts);
    event RecallNoticePublished(string noticeNo, string affectedSns, address regulator, uint256 ts);
    event InspectionResultLogged(string targetId, string result, string reportHash, address inspector, uint256 ts);

    function requestRecall(string sn, string faultType, string faultDesc) public {
        emit RecallRequested(sn, faultType, faultDesc, msg.sender, now);
    }

    function publishRecallNotice(string noticeNo, string affectedSns) public {
        emit RecallNoticePublished(noticeNo, affectedSns, msg.sender, now);
    }

    function logInspectionResult(string targetId, string result, string reportHash) public {
        emit InspectionResultLogged(targetId, result, reportHash, msg.sender, now);
    }
}
