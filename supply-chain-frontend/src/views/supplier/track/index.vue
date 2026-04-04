<template>
  <div class="page-container">
    <div class="table-toolbar">
      <div class="search-bar">
        <el-select
          v-model="queryParams.status"
          placeholder="状态筛选"
          clearable
          style="width: 160px"
          @change="handleSearch"
        >
          <el-option label="待接单" value="PENDING_ACCEPTANCE" />
          <el-option label="已接单" value="ACCEPTED" />
          <el-option label="生产中" value="IN_PRODUCTION" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已撤销" value="CANCELLED" />
        </el-select>
      </div>
    </div>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="tableData"
      border
      stripe
      @expand-change="onExpandChange"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand-content">
            <el-tabs model-value="timeline">
              <el-tab-pane label="生产进度" name="timeline">
                <el-timeline>
                  <el-timeline-item
                    v-for="step in getTimelineSteps(row)"
                    :key="step.label"
                    :timestamp="step.time"
                    :type="step.active ? 'primary' : 'info'"
                    :hollow="!step.done"
                    :color="step.done ? '' : '#e4e7ed'"
                  >
                    <span :class="{ 'step-active': step.active, 'step-done': step.done, 'step-pending': !step.done }">
                      {{ step.label }}
                    </span>
                  </el-timeline-item>
                </el-timeline>
              </el-tab-pane>

              <el-tab-pane label="协议信息" name="agreement" v-if="row._detail?.agreement">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="制造商">
                    {{ row._detail.agreement.manufacturerName }}
                  </el-descriptions-item>
                  <el-descriptions-item label="承诺交期">
                    {{ row._detail.agreement.promisedDelivery }}
                  </el-descriptions-item>
                  <el-descriptions-item label="约定价格">
                    {{ row._detail.agreement.agreedPrice }}
                  </el-descriptions-item>
                  <el-descriptions-item label="协议状态">
                    {{ row._detail.agreement.status }}
                  </el-descriptions-item>
                  <el-descriptions-item label="备注" :span="2">
                    {{ row._detail.agreement.remark || '-' }}
                  </el-descriptions-item>
                </el-descriptions>
              </el-tab-pane>

              <el-tab-pane label="ECID列表" name="ecids" v-if="row._detail?.ecidList?.length">
                <el-table :data="row._detail.ecidList" border size="small" max-height="300">
                  <el-table-column type="index" label="序号" width="60" align="center" />
                  <el-table-column prop="ecid" label="ECID" min-width="200" show-overflow-tooltip />
                  <el-table-column prop="status" label="状态" width="100" align="center" />
                  <el-table-column prop="createTime" label="生成时间" width="170" align="center" />
                </el-table>
              </el-tab-pane>

              <el-tab-pane label="质检报告" name="reports" v-if="row._detail?.testReports?.length">
                <el-table :data="row._detail.testReports" border size="small" max-height="300">
                  <el-table-column type="index" label="序号" width="60" align="center" />
                  <el-table-column prop="reportName" label="报告名称" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="result" label="结果" width="100" align="center">
                    <template #default="{ row: r }">
                      <el-tag :type="r.result === 'PASS' ? 'success' : 'danger'" size="small">
                        {{ r.result === 'PASS' ? '通过' : '不通过' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createTime" label="提交时间" width="170" align="center" />
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="orderId" label="订单ID" min-width="140" show-overflow-tooltip />
      <el-table-column prop="bomName" label="BOM" min-width="140" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="80" align="center" />
      <el-table-column prop="expectedDelivery" label="期望交期" width="120" align="center" />
      <el-table-column prop="targetManufacturerName" label="目标制造商" width="130" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" class="status-tag">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :loading="row._loading" @click="handleExpand(row)">
            加载详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductionOrderList, getProductionOrderTrack } from '@/api/supplier'

const STATUS_MAP = {
  PENDING_ACCEPTANCE: { label: '待接单', type: 'warning' },
  ACCEPTED: { label: '已接单', type: '' },
  IN_PRODUCTION: { label: '生产中', type: 'info' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已撤销', type: 'info' }
}

const STATUS_FLOW = ['PENDING_ACCEPTANCE', 'ACCEPTED', 'IN_PRODUCTION', 'COMPLETED']
const STEP_LABELS = { PENDING_ACCEPTANCE: '待接单', ACCEPTED: '已接单', IN_PRODUCTION: '生产中', COMPLETED: '生产完工' }

function statusLabel(status) {
  return STATUS_MAP[status]?.label || status
}
function statusType(status) {
  return STATUS_MAP[status]?.type ?? 'info'
}

function getTimelineSteps(row) {
  if (row.status === 'CANCELLED') {
    const t = row._detail?.statusTimes?.CANCELLED || ''
    return [{ label: '订单已撤销', done: true, active: true, time: t }]
  }
  const currentIdx = STATUS_FLOW.indexOf(row.status)
  return STATUS_FLOW.map((s, i) => ({
    label: STEP_LABELS[s],
    done: i <= currentIdx,
    active: i === currentIdx,
    time: i <= currentIdx ? (row._detail?.statusTimes?.[s] || '') : ''
  }))
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const tableRef = ref(null)

const queryParams = reactive({
  status: '',
  pageNum: 1,
  pageSize: 10
})

async function fetchList() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (!params.status) delete params.status
    const res = await getProductionOrderList(params)
    tableData.value = (res.data?.records || res.data?.list || []).map(item => ({
      ...item,
      _loading: false,
      _detail: null
    }))
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('获取订单列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  fetchList()
}

function onExpandChange(row, expanded) {
  if (expanded.length && expanded.includes(row)) {
    handleExpand(row)
  }
}

async function handleExpand(row) {
  if (row.id == null) {
    ElMessage.warning('订单缺少主键，无法加载详情')
    return
  }
  await nextTick()
  tableRef.value?.toggleRowExpansion(row, true)
  if (row._detail) return
  row._loading = true
  try {
    const res = await getProductionOrderTrack(row.id)
    row._detail = res.data || {}
  } catch {
    ElMessage.error('获取订单详情失败')
  } finally {
    row._loading = false
  }
}

onMounted(() => fetchList())
</script>

<style scoped lang="scss">
.expand-content {
  padding: 16px 24px;
}

.step-active {
  font-weight: 600;
  color: #409eff;
}

.step-done {
  color: #303133;
}

.step-pending {
  color: #c0c4cc;
}
</style>
