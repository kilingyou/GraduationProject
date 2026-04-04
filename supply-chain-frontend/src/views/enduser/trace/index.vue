<template>
  <div class="page-container">
    <el-card shadow="never" class="hero-card">
      <div class="hero-title">输入产品序列号(SN)查询真伪与溯源</div>
      <div class="hero-search">
        <el-input
          v-model="sn"
          placeholder="例如：SN-20260331-123456"
          clearable
          @keyup.enter="handleTrace"
        />
        <el-button type="primary" :loading="loading" @click="handleTrace">
          查询溯源
        </el-button>
      </div>
      <div class="hero-sub">无需登录即可查询；服务端已对公开接口做访问频率限制，防止恶意刷接口。</div>
    </el-card>

    <div v-if="searched" class="result-area">
      <el-alert
        v-if="!hasAssemblyRecord"
        title="未找到该 SN 的装配记录"
        type="error"
        show-icon
        :closable="false"
      />

      <template v-else>
        <div class="badge-row">
          <el-tag type="success">正品验证：记录存在</el-tag>
          <el-tag v-if="trace.assemblyRecord?.testResult" type="info">
            出厂检测：{{ trace.assemblyRecord.testResult }}
          </el-tag>
        </div>

        <el-alert
          v-for="(w, wi) in traceWarnings"
          :key="wi"
          :title="w"
          type="warning"
          show-icon
          :closable="false"
          class="warn-alert"
        />

        <el-timeline class="timeline">
          <el-timeline-item timestamp="部件溯源 · 设计/订单/BOM/制造商质检" placement="top">
            <el-empty v-if="!deviceTraces.length" description="暂无部件 ECID 或生产关联数据" />
            <div v-else class="device-trace-list">
              <el-collapse>
                <el-collapse-item
                  v-for="(dt, idx) in deviceTraces"
                  :key="dt.ecid || idx"
                  :title="'部件 ECID：' + (dt.ecid || '-') "
                >
                  <el-descriptions v-if="dt.deviceRecord" :column="2" border size="small" class="mb-8">
                    <el-descriptions-item label="批次">{{ dt.deviceRecord.batchId || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="状态">{{ dt.deviceRecord.status || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="生产订单">{{ dt.deviceRecord.orderId || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="设备上链 Tx">{{ dt.deviceRecord.txHash || '-' }}</el-descriptions-item>
                  </el-descriptions>
                  <div v-if="dt.productionChain" class="sub-block">
                    <div class="sub-title">订单与设计/BOM</div>
                    <el-descriptions :column="2" border size="small">
                      <el-descriptions-item label="订单">{{ dt.productionChain.orderId }}</el-descriptions-item>
                      <el-descriptions-item label="状态">{{ dt.productionChain.status }}</el-descriptions-item>
                      <el-descriptions-item label="订单 Tx">{{ dt.productionChain.txHash || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="设计哈希快照">{{ dt.productionChain.designDocHashSnapshot || '-' }}</el-descriptions-item>
                    </el-descriptions>
                    <div v-if="dt.productionChain.designDocument" class="sub-title mt-8">设计文档</div>
                    <el-descriptions
                      v-if="dt.productionChain.designDocument"
                      :column="2"
                      border
                      size="small"
                      class="mb-8"
                    >
                      <el-descriptions-item label="名称">{{ dt.productionChain.designDocument.docName }}</el-descriptions-item>
                      <el-descriptions-item label="版本">{{ dt.productionChain.designDocument.version || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="文件哈希">{{ dt.productionChain.designDocument.fileHash || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="IPFS CID">{{ dt.productionChain.designDocument.ipfsCid || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="链上状态">{{ dt.productionChain.designDocument.chainStatus || '-' }}</el-descriptions-item>
                    </el-descriptions>
                    <el-button
                      v-if="dt.productionChain.designDocument?.ipfsCid && dt.productionChain.designDocument?.fileHash"
                      size="small"
                      class="mt-8"
                      @click="verifyDesignDoc(dt.productionChain.designDocument)"
                    >
                      校验设计文档原件
                    </el-button>
                    <div v-if="dt.productionChain.bom" class="sub-title mt-8">BOM</div>
                    <el-descriptions
                      v-if="dt.productionChain.bom"
                      :column="2"
                      border
                      size="small"
                      class="mb-8"
                    >
                      <el-descriptions-item label="名称">{{ dt.productionChain.bom.bomName }}</el-descriptions-item>
                      <el-descriptions-item label="版本">{{ dt.productionChain.bom.version || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="清单哈希">{{ dt.productionChain.bom.fileHash || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="IPFS CID">{{ dt.productionChain.bom.ipfsCid || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="链上状态">{{ dt.productionChain.bom.chainStatus || '-' }}</el-descriptions-item>
                    </el-descriptions>
                    <el-button
                      v-if="dt.productionChain.bom?.ipfsCid && dt.productionChain.bom?.fileHash"
                      size="small"
                      class="mt-8"
                      @click="verifyBomManifest(dt.productionChain.bom)"
                    >
                      校验 BOM 清单 JSON
                    </el-button>
                  </div>
                  <div v-if="dt.qualityReports?.length" class="sub-block">
                    <div class="sub-title">制造商质检报告（摘要）</div>
                    <el-table :data="dt.qualityReports" border size="small">
                      <el-table-column prop="reportName" label="报告" min-width="120" show-overflow-tooltip />
                      <el-table-column prop="result" label="结果" width="90" />
                      <el-table-column prop="fileHash" label="文件哈希" min-width="160" show-overflow-tooltip />
                      <el-table-column prop="txHash" label="锚定 Tx" min-width="140" show-overflow-tooltip />
                      <el-table-column label="校验" width="100" align="center">
                        <template #default="{ row }">
                          <el-button
                            v-if="row.ipfsCid && row.fileHash"
                            link
                            type="primary"
                            size="small"
                            @click="verifyMfgReport(row)"
                          >
                            校验
                          </el-button>
                          <span v-else>-</span>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </el-timeline-item>

          <el-timeline-item timestamp="装配阶段" placement="top">
            <el-descriptions v-if="trace.assemblyRecord" :column="2" border size="small">
              <el-descriptions-item label="整机 SN">{{ trace.assemblyRecord.sn }}</el-descriptions-item>
              <el-descriptions-item label="组装批次">{{ trace.assemblyRecord.assemblyBatchNo }}</el-descriptions-item>
              <el-descriptions-item label="固件版本">{{ trace.assemblyRecord.firmwareVersion || '-' }}</el-descriptions-item>
              <el-descriptions-item label="测试报告哈希">{{ trace.assemblyRecord.testReportHash || '-' }}</el-descriptions-item>
              <el-descriptions-item label="链上状态">{{ trace.assemblyRecord.status || '-' }}</el-descriptions-item>
              <el-descriptions-item label="组装上链 TxHash">{{ trace.assemblyRecord.txHash || '-' }}</el-descriptions-item>
            </el-descriptions>

            <div class="hash-verify">
              <el-button
                size="small"
                type="warning"
                :loading="verifyingAsm"
                :disabled="!trace.assemblyRecord?.testReportCid || !trace.assemblyRecord?.testReportHash"
                @click="verifyAssemblyQc"
              >
                一键校验整机测试报告（IPFS + SHA-256）
              </el-button>
              <el-tag v-if="verifyAsmOk === true" type="success" effect="plain">校验通过</el-tag>
              <el-tag v-else-if="verifyAsmOk === false" type="danger" effect="plain">校验未通过</el-tag>
              <span class="hint">从存储拉取原件并计算哈希，与链上记录比对（与需求文档一致）。</span>
            </div>

            <div v-if="ecidList.length" class="ecid-block">
              <div class="ecid-title">部件 ECID 列表</div>
              <el-tag
                v-for="ecid in ecidList"
                :key="ecid"
                class="ecid-tag"
                type="primary"
                effect="plain"
              >
                {{ ecid }}
              </el-tag>
            </div>
            <div v-else class="ecid-block empty">暂无 ECID 数据</div>
          </el-timeline-item>

          <el-timeline-item timestamp="物流流转" placement="top">
            <el-empty v-if="transferEvents.length === 0" description="暂无物流流转记录" />
            <el-card v-else shadow="never" class="transfer-card">
              <el-table :data="transferEvents" border size="small">
                <el-table-column prop="transferType" label="类型" width="120" />
                <el-table-column prop="logisticsCompany" label="物流公司" width="150" />
                <el-table-column prop="trackingNumber" label="物流单号" min-width="160" />
                <el-table-column prop="senderId" label="发送方ID" width="120" />
                <el-table-column prop="receiverId" label="接收方ID" width="120" />
                <el-table-column prop="shipTime" label="发货时间" width="180" />
                <el-table-column prop="actualArrival" label="到达时间" width="180" />
              </el-table>
            </el-card>
          </el-timeline-item>

          <el-timeline-item timestamp="销售阶段" placement="top">
            <el-empty v-if="!salesRecord" description="暂无该 SN 的销售登记记录" />
            <el-descriptions v-else :column="2" border size="small">
              <el-descriptions-item label="销售时间">{{ salesRecord.saleTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="客户类型">{{ salesRecord.customerSegment || '-' }}</el-descriptions-item>
              <el-descriptions-item label="匿名销售">
                <el-tag v-if="salesRecord.customerAnonymous === 1" size="small" type="warning">是（链上仅摘要）</el-tag>
                <span v-else>否</span>
              </el-descriptions-item>
              <el-descriptions-item label="客户哈希">{{ salesRecord.customerHash || '-' }}</el-descriptions-item>
              <el-descriptions-item label="发票哈希">{{ salesRecord.invoiceHash || '-' }}</el-descriptions-item>
              <el-descriptions-item label="发票 CID">{{ salesRecord.invoiceCid || '-' }}</el-descriptions-item>
              <el-descriptions-item label="销售锚定 Tx">{{ salesRecord.txHash || '-' }}</el-descriptions-item>
            </el-descriptions>
            <el-button
              v-if="salesRecord?.invoiceCid && salesRecord?.invoiceHash"
              size="small"
              class="mt-8"
              @click="verifyInvoice(salesRecord)"
            >
              校验销售发票附件
            </el-button>
          </el-timeline-item>
        </el-timeline>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { traceProduct, verifyTraceFile } from '@/api/enduser'

const sn = ref('')
const loading = ref(false)
const searched = ref(false)
const trace = ref({})
const verifyingAsm = ref(false)
const verifyAsmOk = ref(null)

const hasAssemblyRecord = computed(() => {
  return !!trace.value?.assemblyRecord
})

const ecidList = computed(() => {
  const raw = trace.value?.ecidList ?? trace.value?.assemblyRecord?.ecidList
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})

const transferEvents = computed(() => {
  return Array.isArray(trace.value?.transferEvents) ? trace.value.transferEvents : []
})

const salesRecord = computed(() => trace.value?.salesRecord || null)

const deviceTraces = computed(() => {
  return Array.isArray(trace.value?.deviceTraces) ? trace.value.deviceTraces : []
})

const traceWarnings = computed(() => {
  const w = trace.value?.warnings
  return Array.isArray(w) ? w : []
})

async function handleTrace() {
  if (!sn.value) {
    ElMessage.warning('请输入 SN')
    return
  }

  loading.value = true
  searched.value = true
  trace.value = {}
  verifyAsmOk.value = null
  try {
    const res = await traceProduct(sn.value)
    trace.value = res.data || {}
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

async function runVerify(ipfsCid, expectedHash, okMsg, failMsg) {
  if (!ipfsCid || !expectedHash) {
    ElMessage.warning('缺少 CID 或哈希')
    return
  }
  try {
    const res = await verifyTraceFile(ipfsCid, expectedHash)
    const ok = res.data === true
    ElMessage[ok ? 'success' : 'error'](ok ? okMsg : failMsg)
    return ok
  } catch {
    ElMessage.error('校验请求失败')
    return false
  }
}

async function verifyAssemblyQc() {
  const ar = trace.value?.assemblyRecord
  verifyingAsm.value = true
  verifyAsmOk.value = null
  try {
    const ok = await runVerify(ar?.testReportCid, ar?.testReportHash, '整机测试报告与链上哈希一致', '哈希不一致或无法拉取文件')
    verifyAsmOk.value = ok
  } finally {
    verifyingAsm.value = false
  }
}

function verifyDesignDoc(doc) {
  runVerify(doc.ipfsCid, doc.fileHash, '设计文档校验通过', '设计文档校验未通过')
}

function verifyBomManifest(bom) {
  runVerify(bom.ipfsCid, bom.fileHash, 'BOM 清单校验通过', 'BOM 清单校验未通过')
}

function verifyMfgReport(row) {
  runVerify(row.ipfsCid, row.fileHash, '制造商质检报告校验通过', '制造商质检报告校验未通过')
}

function verifyInvoice(sr) {
  runVerify(sr.invoiceCid, sr.invoiceHash, '发票附件校验通过', '发票附件校验未通过')
}
</script>

<style scoped lang="scss">
.page-container {
  padding: 10px 20px 30px;
}

.hero-card {
  border-radius: 12px;
  background: linear-gradient(135deg, #f5f7ff 0%, #e8f3ff 45%, #f0f9ff 100%);
}

.hero-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
}

.hero-search {
  display: flex;
  gap: 12px;
  align-items: center;
}

.hero-sub {
  margin-top: 10px;
  font-size: 13px;
  color: #666;
}

.result-area {
  margin-top: 16px;
}

.badge-row {
  display: flex;
  gap: 10px;
  margin: 10px 0;
  flex-wrap: wrap;
}

.warn-alert {
  margin-bottom: 10px;
}

.timeline {
  background: transparent;
}

.hash-verify {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.hint {
  color: #909399;
  font-size: 13px;
}

.ecid-block {
  margin-top: 14px;
}

.ecid-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.ecid-tag {
  margin: 6px 8px 0 0;
}

.ecid-block.empty {
  color: #909399;
  font-size: 13px;
}

.transfer-card {
  border-radius: 8px;
}

.mt-8 {
  margin-top: 8px;
}

.device-trace-list {
  margin-top: 4px;
}

.sub-block {
  margin-top: 12px;
}

.sub-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.mb-8 {
  margin-bottom: 8px;
}
</style>

