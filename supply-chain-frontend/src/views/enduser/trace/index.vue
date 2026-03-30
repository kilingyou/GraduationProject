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
      <div class="hero-sub">无需强制登录，可进行公开溯源查询（后续可接入防刷验证码）。</div>
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

        <el-timeline class="timeline">
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
              <el-button size="small" type="warning" @click="handleVerifyPlaceholder">
                验证哈希（占位）
              </el-button>
              <span class="hint">当前公开查询仅展示链上记录，文件校验需要接入 IPFS 下载与 SHA-256 计算。</span>
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

          <el-timeline-item timestamp="物流阶段" placement="top">
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

          <el-timeline-item timestamp="销售/维权阶段" placement="top">
            <el-empty description="销售记录与维权信息展示（本版本为占位，可后续接入链上/链下数据）" />
          </el-timeline-item>
        </el-timeline>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { traceProduct } from '@/api/enduser'

const sn = ref('')
const loading = ref(false)
const searched = ref(false)
const trace = ref({})

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

async function handleTrace() {
  if (!sn.value) {
    ElMessage.warning('请输入 SN')
    return
  }

  loading.value = true
  searched.value = true
  trace.value = {}
  try {
    const res = await traceProduct(sn.value)
    trace.value = res.data || {}
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

function handleVerifyPlaceholder() {
  ElMessage.info('占位：需下载 IPFS 原件并计算 SHA-256 后与链上哈希比对。')
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
</style>

