pragma solidity ^0.4.25;

contract SupplyChainTraceability {
    address public owner;
    uint8 constant ROLE_SUPPLIER = 1;
    uint8 constant ROLE_MANUFACTURER = 2;
    uint8 constant ROLE_ASSEMBLER = 3;
    uint8 constant ROLE_DISTRIBUTOR = 4;
    uint8 constant ROLE_REGULATOR = 5;
    uint8 constant ROLE_INSPECTOR = 6;

    mapping(address => uint8) public roleOf;

    modifier onlyOwner() {
        require(msg.sender == owner, "only owner");
        _;
    }

    modifier onlyRole(uint8 role) {
        require(roleOf[msg.sender] == role, "role denied");
        _;
    }

    constructor() public {
        owner = msg.sender;
        roleOf[msg.sender] = ROLE_REGULATOR;
    }

    event RoleAssigned(address indexed user, uint8 indexed role, address operator, uint256 ts);

    function setRole(address user, uint8 role) public onlyOwner {
        require(role <= ROLE_INSPECTOR, "invalid role");
        roleOf[user] = role;
        emit RoleAssigned(user, role, msg.sender, now);
    }

    // 1) 通用锚定与审计索引
    uint256 public anchorCount;
    mapping(uint256 => string) internal _anchorBizTypes;
    mapping(uint256 => string) internal _anchorPayloads;
    mapping(uint256 => address) internal _anchorOperators;
    mapping(uint256 => uint256) internal _anchorTimestamps;

    event AnchorEvent(
        uint256 indexed id,
        string bizType,
        string payloadHash,
        address operator,
        uint256 timestamp
    );

    function anchor(string bizType, string payloadHash) public returns (uint256) {
        anchorCount++;
        _anchorBizTypes[anchorCount] = bizType;
        _anchorPayloads[anchorCount] = payloadHash;
        _anchorOperators[anchorCount] = msg.sender;
        _anchorTimestamps[anchorCount] = now;
        emit AnchorEvent(anchorCount, bizType, payloadHash, msg.sender, now);
        return anchorCount;
    }

    function getAnchor(uint256 id) public view returns (string, string, address, uint256) {
        return (_anchorBizTypes[id], _anchorPayloads[id], _anchorOperators[id], _anchorTimestamps[id]);
    }

    // 2) 供应商资质审核
    mapping(address => bool) public approvedSuppliers;
    mapping(address => string) internal _supplierQualHash;

    event SupplierApproved(address indexed supplier, string qualHash, address approver, uint256 ts);
    event SupplierRevoked(address indexed supplier, address revoker, uint256 ts);

    //监管商通过后，供应商在链上获得合法地址
    function approveSupplier(address supplier, string qualHash) public onlyRole(ROLE_REGULATOR) {
        approvedSuppliers[supplier] = true;
        _supplierQualHash[supplier] = qualHash;
        emit SupplierApproved(supplier, qualHash, msg.sender, now);
    }

    function revokeSupplier(address supplier) public onlyRole(ROLE_REGULATOR) {
        approvedSuppliers[supplier] = false;
        emit SupplierRevoked(supplier, msg.sender, now);
    }

    function isSupplierApproved(address supplier) public view returns (bool) {
        return approvedSuppliers[supplier];
    }

    function getSupplierQualHash(address supplier) public view returns (string) {
        return _supplierQualHash[supplier];
    }

    // 3) 供应商发布生产订单 + 制造商协议签署
    struct ProductionRequest {
        bool exists;
        address supplier;
        address targetManufacturer;
        string bomHash;
        uint256 quantity;
        string designDocHash;
        uint256 expectedDeliveryTs;
        string qualityReqHash;
        string status;
        uint256 createdAt;
    }

    struct ManufacturingAgreement {
        bool exists;
        string orderId;
        address supplier;
        address manufacturer;
        string agreementHash;
        string priceClause;
        uint256 deliveryTs;
        uint256 signedAt;
    }

    mapping(string => ProductionRequest) internal _productionRequests;
    mapping(string => ManufacturingAgreement) internal _agreements;

    event ProductionRequestCreated(
        string indexed orderId,
        address indexed supplier,
        address indexed targetManufacturer,
        string bomHash,
        uint256 quantity,
        string designDocHash,
        uint256 expectedDeliveryTs,
        string qualityReqHash,
        uint256 ts
    );

    event ProductionRequestStatusChanged(string indexed orderId, string status, address operator, uint256 ts);

    event ManufacturingAgreementSigned(
        string indexed orderId,
        string agreementHash,
        address indexed supplier,
        address indexed manufacturer,
        string priceClause,
        uint256 deliveryTs,
        uint256 ts
    );

    //把图纸的哈希、BOM单哈希上链，指定目标制造商
    function createProductionRequest(
        string orderId,
        address targetManufacturer,
        string bomHash,
        uint256 quantity,
        string designDocHash,
        uint256 expectedDeliveryTs,
        string qualityReqHash
    ) public onlyRole(ROLE_SUPPLIER) {
        require(bytes(orderId).length > 0, "empty orderId");
        require(!_productionRequests[orderId].exists, "order exists");

        _productionRequests[orderId] = ProductionRequest(
            true,
            msg.sender,
            targetManufacturer,
            bomHash,
            quantity,
            designDocHash,
            expectedDeliveryTs,
            qualityReqHash,
            "CREATED",
            now
        );

        emit ProductionRequestCreated(
            orderId,
            msg.sender,
            targetManufacturer,
            bomHash,
            quantity,
            designDocHash,
            expectedDeliveryTs,
            qualityReqHash,
            now
        );
    }

    function updateProductionRequestStatus(string orderId, string status) public {
        require(_productionRequests[orderId].exists, "order missing");
        address supplier = _productionRequests[orderId].supplier;
        address mfg = _productionRequests[orderId].targetManufacturer;
        require(msg.sender == supplier || msg.sender == mfg || roleOf[msg.sender] == ROLE_REGULATOR, "forbidden");
        _productionRequests[orderId].status = status;
        emit ProductionRequestStatusChanged(orderId, status, msg.sender, now);
    }

    //制造商收到后，调用 signManufacturingAgreement 签署协议。此时链上记录了双方达成合作的铁证
    function signManufacturingAgreement(
        string orderId,
        string agreementHash,
        string priceClause,
        uint256 deliveryTs
    ) public onlyRole(ROLE_MANUFACTURER) {
        require(_productionRequests[orderId].exists, "order missing");
        require(_productionRequests[orderId].targetManufacturer == msg.sender, "not target manufacturer");
        _agreements[orderId] = ManufacturingAgreement(
            true,
            orderId,
            _productionRequests[orderId].supplier,
            msg.sender,
            agreementHash,
            priceClause,
            deliveryTs,
            now
        );
        _productionRequests[orderId].status = "AGREED";
        emit ManufacturingAgreementSigned(
            orderId,
            agreementHash,
            _productionRequests[orderId].supplier,
            msg.sender,
            priceClause,
            deliveryTs,
            now
        );
    }

    function getProductionRequest(string orderId)
        public
        view
        returns (address, address, string, uint256, string, uint256, string, string, uint256)
    {
        ProductionRequest storage req = _productionRequests[orderId];
        return (
            req.supplier,
            req.targetManufacturer,
            req.bomHash,
            req.quantity,
            req.designDocHash,
            req.expectedDeliveryTs,
            req.qualityReqHash,
            req.status,
            req.createdAt
        );
    }

    function getManufacturingAgreement(string orderId)
        public
        view
        returns (string, address, address, string, string, uint256, uint256)
    {
        ManufacturingAgreement storage ag = _agreements[orderId];
        return (ag.orderId, ag.supplier, ag.manufacturer, ag.agreementHash, ag.priceClause, ag.deliveryTs, ag.signedAt);
    }

    // 4) 生产登记/完工/不合格（ECID）
    struct DeviceRecord {
        bool exists;
        string orderId;
        string batchId;
        uint256 manufactureTimestamp;
        address manufacturer;
        string deviceType;
        string testReportHash;
        string status;
    }

    mapping(string => DeviceRecord) internal _devices;
    mapping(string => bool) internal _batchRejected;

    event DeviceRegistered(
        string ecid,
        string orderId,
        string batchId,
        string devType,
        string testReportHash,
        address mfg,
        uint256 ts
    );
    event DeviceStatusUpdated(string ecid, string newStatus, address operator, uint256 ts);
    event ProductionComplete(
        string indexed orderId,
        string indexed batchId,
        bool passed,
        string testReportHash,
        string signatureHash,
        address manufacturer,
        uint256 ts
    );
    event RejectRecord(
        string indexed orderId,
        string indexed batchId,
        string rejectRefId,
        string reason,
        string disposition,
        address operator,
        uint256 ts
    );
//制造商调用 registerDeviceRecord，把这个ECID连同“质检报告的哈希”一起写到区块链上。如果是残次品，就调用 recordReject 记录报废原因
    function registerDeviceRecord(
        string ecid,
        string orderId,
        string batchId,
        string devType,
        string testReportHash,
        string status
    ) public onlyRole(ROLE_MANUFACTURER) {
        require(bytes(ecid).length > 0, "empty ecid");
        require(!_devices[ecid].exists, "ecid exists");
        _devices[ecid] = DeviceRecord(
            true,
            orderId,
            batchId,
            now,
            msg.sender,
            devType,
            testReportHash,
            status
        );
        emit DeviceRegistered(ecid, orderId, batchId, devType, testReportHash, msg.sender, now);
    }

    // 兼容旧接口：默认状态 REGISTERED，测试报告为空
    function registerDevice(string ecid, string orderId, string batchId, string devType) public {
        registerDeviceRecord(ecid, orderId, batchId, devType, "", "REGISTERED");
    }

    function updateDeviceStatus(string ecid, string newStatus) public onlyRole(ROLE_MANUFACTURER) {
        require(_devices[ecid].exists, "ecid missing");
        _devices[ecid].status = newStatus;
        emit DeviceStatusUpdated(ecid, newStatus, msg.sender, now);
    }

    function recordProductionComplete(
        string orderId,
        string batchId,
        bool passed,
        string testReportHash,
        string signatureHash
    ) public onlyRole(ROLE_MANUFACTURER) {
        emit ProductionComplete(orderId, batchId, passed, testReportHash, signatureHash, msg.sender, now);
        if (!passed) {
            _batchRejected[batchId] = true;
        }
        _productionRequests[orderId].status = passed ? "PRODUCED_PASS" : "PRODUCED_REJECT";
    }

    function recordReject(
        string orderId,
        string batchId,
        string rejectRefId,
        string reason,
        string disposition
    ) public onlyRole(ROLE_MANUFACTURER) {
        _batchRejected[batchId] = true;
        emit RejectRecord(orderId, batchId, rejectRefId, reason, disposition, msg.sender, now);
    }

    function isBatchRejected(string batchId) public view returns (bool) {
        return _batchRejected[batchId];
    }

    function getDeviceStatus(string ecid) public view returns (string) {
        return _devices[ecid].status;
    }

    // 兼容旧接口：返回 6 元组
    function getDeviceInfo(string ecid) public view returns (string, string, string, address, uint256, string) {
        DeviceRecord storage d = _devices[ecid];
        return (d.orderId, d.batchId, d.deviceType, d.manufacturer, d.manufactureTimestamp, d.status);
    }

    function getDeviceInfoV2(string ecid) public view returns (string, string, string, address, uint256, string, string) {
        DeviceRecord storage d = _devices[ecid];
        return (d.orderId, d.batchId, d.deviceType, d.manufacturer, d.manufactureTimestamp, d.status, d.testReportHash);
    }

    // 5) 组装记录（部件 -> 整机映射）
    struct AssemblyRecord {
        bool exists;
        string sn;
        string ecidListJson;
        string batchNo;
        string fwVersion;
        string reportHash;
        address assembler;
        uint256 ts;
    }

    mapping(string => AssemblyRecord) internal _assemblies;
    mapping(string => string) internal _ecidToSn;
    mapping(string => string) internal _snToEcidListJson;

    event AssemblyRecordCreated(string sn, string batchNo, string ecidListJson, address assembler, uint256 ts);
//组装商调用 createAssemblyRecord 和 bindEcidToSn。这是溯源的核心！ 合约把 部件(ECID) -> 整机(SN) 的映射关系死死地绑定在了一起
    function createAssemblyRecord(
        string sn,
        string ecidListJson,
        string batchNo,
        string fwVersion,
        string reportHash
    ) public onlyRole(ROLE_ASSEMBLER) {
        _assemblies[sn] = AssemblyRecord(true, sn, ecidListJson, batchNo, fwVersion, reportHash, msg.sender, now);
        _snToEcidListJson[sn] = ecidListJson;
        emit AssemblyRecordCreated(sn, batchNo, ecidListJson, msg.sender, now);
    }

    function bindEcidToSn(string ecid, string sn) public onlyRole(ROLE_ASSEMBLER) {
        _ecidToSn[ecid] = sn;
    }

    function getSnEcidList(string sn) public view returns (string) {
        return _snToEcidListJson[sn];
    }

    function getEcidSn(string ecid) public view returns (string) {
        return _ecidToSn[ecid];
    }

    function getAssemblyInfo(string sn) public view returns (string, string, string, string, address, uint256) {
        AssemblyRecord storage a = _assemblies[sn];
        return (a.ecidListJson, a.batchNo, a.fwVersion, a.reportHash, a.assembler, a.ts);
    }

    // 6) 分销与流通
    uint256 public transferCount;
    mapping(string => address) internal _snToCurrentOwner;

    event TransferEventLogged(
        uint256 indexed id,
        string sn,
        string trackingNo,
        address sender,
        address receiver,
        string transferType,
        uint256 ts
    );
//手机交给顺丰，分销商调用 logTransfer 记录物流单号
    function logTransfer(string sn, string trackingNo, address receiver, string transferType)
        public
        onlyRole(ROLE_DISTRIBUTOR)
    {
        transferCount++;
        _snToCurrentOwner[sn] = receiver;
        emit TransferEventLogged(transferCount, sn, trackingNo, msg.sender, receiver, transferType, now);
    }

    function getSnOwner(string sn) public view returns (address) {
        return _snToCurrentOwner[sn];
    }

    // 7) 销售记录（支持摘要上链）
    struct SaleRecord {
        bool exists;
        string sn;
        string customerHash;
        string invoiceHash;
        address seller;
        uint256 ts;
    }

    mapping(string => SaleRecord) internal _sales;

    event SaleRegistered(string sn, string customerHash, string invoiceHash, address seller, uint256 ts);
//手机卖给顾客，分销商调用 registerSale，将发票摘要上链
    function registerSale(string sn, string customerHash, string invoiceHash) public onlyRole(ROLE_DISTRIBUTOR) {
        _sales[sn] = SaleRecord(true, sn, customerHash, invoiceHash, msg.sender, now);
        emit SaleRegistered(sn, customerHash, invoiceHash, msg.sender, now);
    }

    function getSaleInfo(string sn) public view returns (string, string, address, uint256) {
        SaleRecord storage s = _sales[sn];
        return (s.customerHash, s.invoiceHash, s.seller, s.ts);
    }

    // 8) 使用/保修/召回
    event RecallRequested(string sn, string faultType, string faultDesc, address requester, uint256 ts);
    event RecallNoticePublished(string noticeNo, string affectedSns, address regulator, uint256 ts);
    event BatchRecallTriggered(string noticeNo, string batchId, string reason, address regulator, uint256 ts);
    event InspectionResultLogged(string targetId, string result, string reportHash, address inspector, uint256 ts);

    mapping(string => bool) internal _recalledBatch;

    function requestRecall(string sn, string faultType, string faultDesc) public {
        emit RecallRequested(sn, faultType, faultDesc, msg.sender, now);
    }

    function publishRecallNotice(string noticeNo, string affectedSns) public onlyRole(ROLE_REGULATOR) {
        emit RecallNoticePublished(noticeNo, affectedSns, msg.sender, now);
    }
//如果发现这批主板有安全隐患，监管机构调用 triggerBatchRecall，系统通过链上数据，瞬间就能反查出这批主板被组装到了哪些SN序列号的手机上，精准发布召回通知
    function triggerBatchRecall(string noticeNo, string batchId, string reason) public onlyRole(ROLE_REGULATOR) {
        _recalledBatch[batchId] = true;
        emit BatchRecallTriggered(noticeNo, batchId, reason, msg.sender, now);
    }

    function isBatchRecalled(string batchId) public view returns (bool) {
        return _recalledBatch[batchId];
    }

    function logInspectionResult(string targetId, string result, string reportHash) public {
        require(roleOf[msg.sender] == ROLE_INSPECTOR || roleOf[msg.sender] == ROLE_REGULATOR, "role denied");
        emit InspectionResultLogged(targetId, result, reportHash, msg.sender, now);
    }

    // 9) 报废与销毁闭环
    struct DecommissionRecord {
        bool exists;
        string disposalMethod;
        address operator;
        uint256 ts;
        string agency;
    }

    mapping(string => bool) internal _decommissioned;
    mapping(string => DecommissionRecord) internal _decommissionInfo;

    event ProductDecommissioned(string sn, string disposalMethod, address operator, uint256 ts);

//寿命终结，调用 decommissionProduct 记录报废，完成生命周期闭环
    function decommissionProduct(string sn, string disposalMethod) public {
        _decommissioned[sn] = true;
        _decommissionInfo[sn] = DecommissionRecord(true, disposalMethod, msg.sender, now, "");
        emit ProductDecommissioned(sn, disposalMethod, msg.sender, now);
    }

    function decommissionWithAgency(string sn, string disposalMethod, string agency) public {
        _decommissioned[sn] = true;
        _decommissionInfo[sn] = DecommissionRecord(true, disposalMethod, msg.sender, now, agency);
        emit ProductDecommissioned(sn, disposalMethod, msg.sender, now);
    }

    function isDecommissioned(string sn) public view returns (bool) {
        return _decommissioned[sn];
    }

    function getDecommissionInfo(string sn) public view returns (string, address, uint256, string) {
        DecommissionRecord storage d = _decommissionInfo[sn];
        return (d.disposalMethod, d.operator, d.ts, d.agency);
    }
}
