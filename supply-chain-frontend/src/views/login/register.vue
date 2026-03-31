<template>
  <div class="register-wrapper">
    <div class="register-card">
      <div class="register-header">
        <el-icon :size="40" color="#409eff"><Connection /></el-icon>
        <h2 class="title">企业注册</h2>
        <p class="subtitle">注册成为供应链平台成员</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        label-position="top"
        size="large"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="登录账号" prop="username">
              <el-input v-model="form.username" placeholder="请输入账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色" prop="roleKey">
              <el-select v-model="form.roleKey" placeholder="请选择角色" style="width: 100%">
                <el-option label="供应商" value="supplier" />
                <el-option label="制造商" value="manufacturer" />
                <el-option label="组装商" value="assembler" />
                <el-option label="分销商" value="distributor" />
                <el-option label="监管机构" value="regulator" />
                <el-option label="终端用户" value="enduser" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="!isEnduser" :gutter="20">
          <el-col :span="12">
            <el-form-item label="企业名称" prop="enterpriseName">
              <el-input v-model="form.enterpriseName" placeholder="请输入企业名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统一社会信用代码" prop="creditCode">
              <el-input v-model="form.creditCode" placeholder="请输入信用代码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col v-if="!isEnduser" :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="isEnduser ? 24 : 12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item v-if="!isEnduser" label="电子邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入电子邮箱" />
        </el-form-item>

        <template v-if="form.roleKey === 'supplier'">
          <el-form-item label="营业执照">
            <el-upload
              v-model:file-list="licenseFileList"
              action="/api/upload"
              :auto-upload="false"
              :limit="1"
              accept=".pdf,.jpg,.jpeg,.png"
            >
              <el-button type="primary" plain>
                <el-icon><Upload /></el-icon>
                上传营业执照
              </el-button>
            </el-upload>
          </el-form-item>

          <el-form-item label="资质证书（可选）">
            <el-upload
              v-model:file-list="certFileList"
              action="/api/upload"
              :auto-upload="false"
              :limit="1"
              accept=".pdf,.jpg,.jpeg,.png"
            >
              <el-button type="primary" plain>
                <el-icon><Upload /></el-icon>
                上传资质证书
              </el-button>
              <template #tip>
                <div class="el-upload__tip">
                  支持 PDF/JPG/JPEG/PNG。营业执照与资质证书分开上传，提交时会按顺序传给后端。
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </template>

        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            @click="handleRegister"
          >
            提交注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        <router-link to="/login" class="link">已有账号？返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const licenseFileList = ref([])
const certFileList = ref([])

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  enterpriseName: '',
  creditCode: '',
  contactPerson: '',
  phone: '',
  email: '',
  roleKey: ''
})

const isEnduser = computed(() => form.roleKey === 'enduser')

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validatePhone = (rule, value, callback) => {
  if (!value) {
    if (isEnduser.value) {
      callback(new Error('请输入联系电话'))
    } else {
      callback()
    }
    return
  }
  if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号码'))
  } else {
    callback()
  }
}

const validateEnterpriseName = (rule, value, callback) => {
  if (!isEnduser.value && !value) {
    callback(new Error('请输入企业名称'))
  } else {
    callback()
  }
}

const validateCreditCode = (rule, value, callback) => {
  if (!isEnduser.value && !value) {
    callback(new Error('请输入统一社会信用代码'))
  } else {
    callback()
  }
}

const validateContactPerson = (rule, value, callback) => {
  if (!isEnduser.value && !value) {
    callback(new Error('请输入联系人'))
  } else {
    callback()
  }
}

const validateEmail = (rule, value, callback) => {
  if (!value) {
    if (!isEnduser.value) {
      callback(new Error('请输入电子邮箱'))
    } else {
      callback()
    }
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在3到20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  enterpriseName: [{ validator: validateEnterpriseName, trigger: 'blur' }],
  creditCode: [{ validator: validateCreditCode, trigger: 'blur' }],
  contactPerson: [{ validator: validateContactPerson, trigger: 'blur' }],
  phone: [
    { validator: validatePhone, trigger: 'blur' }
  ],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  roleKey: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const submitData = { ...form }
    delete submitData.confirmPassword

    if (form.roleKey === 'supplier') {
      const fd = new FormData()
      Object.entries(submitData).forEach(([k, v]) => fd.append(k, v))
      if (licenseFileList.value.length > 0) {
        fd.append('files', licenseFileList.value[0].raw)
      }
      if (certFileList.value.length > 0) {
        fd.append('files', certFileList.value[0].raw)
      }
      await register(fd)
    } else {
      await register(submitData)
    }

    ElMessage.success('注册成功，请等待审核')
    router.push('/login')
  } catch (err) {
    ElMessage.error(err.response?.data?.message || err.message || '注册失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => form.roleKey,
  (role) => {
    if (role === 'enduser') {
      form.enterpriseName = ''
      form.creditCode = ''
      form.contactPerson = ''
      form.email = ''
    }
    if (role !== 'supplier') {
      licenseFileList.value = []
      certFileList.value = []
    }
  }
)
</script>

<style scoped lang="scss">
.register-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
}

.register-card {
  width: 680px;
  padding: 40px 40px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.register-header {
  text-align: center;
  margin-bottom: 28px;

  .title {
    font-size: 22px;
    font-weight: 700;
    color: #303133;
    margin: 8px 0 4px;
  }

  .subtitle {
    font-size: 14px;
    color: #909399;
    margin: 0;
  }
}

.submit-btn {
  width: 100%;
  font-size: 16px;
  letter-spacing: 4px;
}

.register-footer {
  text-align: center;
  margin-top: 4px;

  .link {
    font-size: 13px;
    color: #409eff;
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
