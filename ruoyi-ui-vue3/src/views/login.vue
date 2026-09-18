<template>
  <div class="login">
    <language-switch class="login-language" />
    <div class="login-box">
      <el-form
        v-if="authMode === 'local'"
        ref="loginRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <h3 class="title">{{ $t('login.productTitle') }}</h3>
        <el-form-item v-if="giteeStar === 'true'">
          <el-text class="mx-1">给本项目 star 后即可访问：</el-text>
          <el-link type="primary" href="https://gitee.com/xiaochemgzi/RuoYi-Wvp" target="_blank">
            点我去 star
          </el-link>
        </el-form-item>
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            autocomplete="off"
            :placeholder="$t('login.account')"
          >
            <template #prefix>
              <svg-icon icon-class="user" class="el-input__icon input-icon" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            autocomplete="off"
            :placeholder="$t('login.password')"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <svg-icon icon-class="password" class="el-input__icon input-icon" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="captchaEnabled" prop="code">
          <el-input
            v-model="loginForm.code"
            size="large"
            autocomplete="off"
            :placeholder="$t('login.captcha')"
            style="width: 63%"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <svg-icon icon-class="validCode" class="el-input__icon input-icon" />
            </template>
          </el-input>
          <div class="login-code">
            <img :src="codeUrl" class="login-code-img" :alt="$t('login.captcha')" @click="getCode" />
          </div>
        </el-form-item>
        <el-checkbox v-model="loginForm.rememberMe" style="margin: 0 0 25px 0">{{ $t('login.remember') }}</el-checkbox>
        <el-form-item style="width: 100%">
          <el-button
            :loading="loading"
            size="large"
            type="primary"
            style="width: 100%"
            @click.prevent="handleLogin"
          >
            <span v-if="!loading">{{ $t('login.signIn') }}</span>
            <span v-else>{{ $t('login.signingIn') }}</span>
          </el-button>
          <div v-if="register" style="float: right">
            <router-link class="link-type" to="/register">{{ $t('login.register') }}</router-link>
          </div>
        </el-form-item>
      </el-form>

      <div v-else class="sso-panel">
        <h3 class="title">{{ $t('login.productTitle') }}</h3>
        <p v-if="authModeLoading">{{ $t('login.loadingMode') }}</p>
        <template v-else-if="authModeError || authModeErrorKey">
          <p class="mode-error">{{ authModeErrorKey ? $t(authModeErrorKey) : authModeError }}</p>
          <el-button type="primary" @click="loadAuthMode">{{ $t('login.reload') }}</el-button>
        </template>
        <template v-else>
          <p>{{ $t('login.ssoEnabled') }}</p>
          <p class="sso-tip">{{ $t('login.ssoTip') }}</p>
        </template>
      </div>

      <div class="el-login-footer">
        <span>Copyright © 2024-2026 {{ $t('login.copyright') }}</span>
      </div>
    </div>

    <el-dialog v-if="authMode === 'local'" v-model="open" title="关注公众号" width="32%" append-to-body>
      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="120px">
        <el-form-item label="公众号二维码">
          <el-image style="width: 150px" :src="gzhImage" :preview-src-list="[gzhImage]" />
        </el-form-item>
        <el-form-item label="公众号 code" prop="publicCode">
          <el-input
            v-model="loginForm.publicCode"
            type="text"
            size="large"
            placeholder="请输入公众号 code"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { getCurrentInstance, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Cookies from 'js-cookie'
import { getAuthMode, getCodeImg, giteeLogin } from '@/api/login'
import { getConfigKey } from '@/api/system/config'
import { decrypt, encrypt } from '@/utils/jsencrypt'
import useUserStore from '@/store/modules/user'
import LanguageSwitch from '@/components/LanguageSwitch/index.vue'
import { useI18n } from 'vue-i18n'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const { t } = useI18n()

const authMode = ref('')
const authModeLoading = ref(true)
const authModeError = ref('')
const authModeErrorKey = ref('')
const loginForm = ref({
  username: '',
  password: '',
  rememberMe: false,
  code: '',
  uuid: '',
  publicCode: ''
})
const loginRules = computed(() => ({
  username: [{ required: true, trigger: 'blur', message: t('login.accountRequired') }],
  password: [{ required: true, trigger: 'blur', message: t('login.passwordRequired') }],
  code: [{ required: true, trigger: 'change', message: t('login.captchaRequired') }],
  publicCode: [{ required: true, trigger: 'change', message: '请输入公众号 code' }]
}))

const codeUrl = ref('')
const loading = ref(false)
const captchaEnabled = ref(true)
const register = ref(false)
const redirect = ref(undefined)
const publicDemonstrate = ref('false')
const giteeStar = ref('false')
const open = ref(false)
const gzhImage = ref('https://gdhxkj.oss-cn-guangzhou.aliyuncs.com/2025/04/21/834e25b18791464eb903ffc39fa9bd0c.jpg')

watch(route, (newRoute) => {
  redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

async function loadAuthMode() {
  authModeLoading.value = true
  authModeError.value = ''
  authModeErrorKey.value = ''
  try {
    const response = await getAuthMode()
    const mode = String(response.mode || '').toLowerCase()
    if (mode !== 'sso' && mode !== 'local') {
      authModeErrorKey.value = 'login.unsupportedMode'
      return
    }
    authMode.value = mode
    if (mode === 'local') {
      initializeLocalLogin().catch(error => {
        proxy.$modal.msgError(error?.message || t('login.localInitFailed'))
      })
    }
  } catch (error) {
    authMode.value = ''
    authModeError.value = error?.message || ''
    authModeErrorKey.value = 'login.modeLoadFailed'
  } finally {
    authModeLoading.value = false
  }
}

async function initializeLocalLogin() {
  getCookie()
  await Promise.all([getCode(), getConfigKeyFun()])

  const giteeCode = route.query && route.query.code
  if (giteeCode === 'true') {
    await login()
    proxy.$modal.msgSuccess(t('login.success'))
  } else if (giteeCode === 'false') {
    proxy.$modal.msgError('登录失败：请先点 star 再登录')
  }
}

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (!valid) {
      return
    }
    if (publicDemonstrate.value === 'true' && loginForm.value.username !== 'admin') {
      open.value = true
      return
    }
    if (giteeStar.value === 'true' && loginForm.value.username !== 'admin') {
      proxy.$modal.confirm('访问本系统需检测是否 star 本项目，是否继续？').then(() => {
        saveRememberedLogin()
        giteeLogin().then(res => {
          window.location.href = res.msg
        })
      }).catch(() => {})
      return
    }
    login().catch(() => {})
  })
}

function login() {
  loading.value = true
  saveRememberedLogin()
  return userStore.login(loginForm.value).then(() => {
    const otherQueryParams = Object.keys(route.query).reduce((result, key) => {
      if (key !== 'redirect') {
        result[key] = route.query[key]
      }
      return result
    }, {})
    return router.push({ path: redirect.value || '/', query: otherQueryParams })
  }).catch(error => {
    if (captchaEnabled.value) {
      getCode()
    }
    throw error
  }).finally(() => {
    loading.value = false
  })
}

function getCode() {
  return getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = `data:image/gif;base64,${res.img}`
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get('username')
  const password = Cookies.get('password')
  const rememberMe = Cookies.get('rememberMe')
  loginForm.value.username = username === undefined ? '' : username
  loginForm.value.password = password === undefined ? '' : decrypt(password)
  loginForm.value.rememberMe = rememberMe === 'true'
}

function saveRememberedLogin() {
  if (loginForm.value.rememberMe) {
    Cookies.set('username', loginForm.value.username, { expires: 30 })
    Cookies.set('password', encrypt(loginForm.value.password), { expires: 30 })
    Cookies.set('rememberMe', 'true', { expires: 30 })
  } else {
    Cookies.remove('username')
    Cookies.remove('password')
    Cookies.remove('rememberMe')
  }
}

function getConfigKeyFun() {
  return Promise.all([
    getConfigKey('sys_public_demonstrate').then(response => {
      publicDemonstrate.value = response.msg
    }),
    getConfigKey('sys_gitee_star').then(response => {
      giteeStar.value = response.msg
    })
  ])
}

function submitForm() {
  proxy.$refs.loginFormRef.validate(valid => {
    if (valid) {
      open.value = false
      login().catch(() => {})
    }
  })
}

onMounted(loadAuthMode)
</script>

<style lang="scss" scoped>
.login {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  height: 100%;
  background-image: url('../assets/images/bg-url.png');
  background-size: cover;
}

.login-language {
  position: fixed;
  top: 20px;
  right: 24px;
  z-index: 10;
  color: #606266;
}

:global(html[dir="rtl"]) .login-language {
  right: auto;
  left: 24px;
}

:global(html[dir="rtl"]) .login {
  justify-content: flex-start;
}

:global(html[dir="rtl"]) .login-box {
  border-radius: 0 25px 25px 0;
}

.title {
  margin: 0 auto 30px;
  text-align: center;
  font-size: 30px;
  color: #707070;
}

.login-box {
  width: 900px;
  height: 100vh;
  background: #ffffff;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 25px 0 0 25px;
  box-shadow: rgba(0, 0, 0, 0.16) 0 1px 4px;
}

.login-form,
.sso-panel {
  border-radius: 6px;
  background: #ffffff;
  width: 500px;
  padding: 20px 20px 5px;
}

.login-form {
  .el-input {
    height: 40px;

    input {
      height: 40px;
    }
  }

  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0;
  }
}

.sso-panel {
  text-align: center;
  color: #606266;
}

.sso-tip {
  margin-top: 12px;
  color: #909399;
}

.mode-error {
  color: #f56c6c;
}

.login-code {
  width: 33%;
  height: 40px;
  float: right;

  img {
    cursor: pointer;
    vertical-align: middle;
  }
}

.el-login-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #000000;
  font-family: Arial, sans-serif;
  font-size: 12px;
  letter-spacing: 1px;
}

.login-code-img {
  height: 40px;
  padding-left: 12px;
}
</style>
