<template>
  <div class="login">
    <div class="login-box">
      <!--      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">-->
      <!--        <h3 class="title">{{ title }}</h3>-->
      <!--        <el-form-item v-if="giteeStar === 'true'">-->
      <!--          <el-text class="mx-1">给本项目star后即可访问：</el-text><el-link type="primary" href="https://gitee.com/xiaochemgzi/RuoYi-Wvp" target="_blank">点我去star</el-link>-->
      <!--        </el-form-item>-->
      <!--        <el-form-item prop="username">-->
      <!--          <el-input-->
      <!--              v-model="loginForm.username"-->
      <!--              type="text"-->
      <!--              size="large"-->
      <!--              auto-complete="off"-->
      <!--              placeholder="账号"-->
      <!--          >-->
      <!--            <template #prefix>-->
      <!--              <svg-icon icon-class="user" class="el-input__icon input-icon"/>-->
      <!--            </template>-->
      <!--          </el-input>-->
      <!--        </el-form-item>-->
      <!--        <el-form-item prop="password">-->
      <!--          <el-input-->
      <!--              v-model="loginForm.password"-->
      <!--              type="password"-->
      <!--              size="large"-->
      <!--              auto-complete="off"-->
      <!--              placeholder="密码"-->
      <!--              @keyup.enter="handleLogin"-->
      <!--          >-->
      <!--            <template #prefix>-->
      <!--              <svg-icon icon-class="password" class="el-input__icon input-icon"/>-->
      <!--            </template>-->
      <!--          </el-input>-->
      <!--        </el-form-item>-->
      <!--        <el-form-item prop="code" v-if="captchaEnabled">-->
      <!--          <el-input-->
      <!--              v-model="loginForm.code"-->
      <!--              size="large"-->
      <!--              auto-complete="off"-->
      <!--              placeholder="验证码"-->
      <!--              style="width: 63%"-->
      <!--              @keyup.enter="handleLogin"-->
      <!--          >-->
      <!--            <template #prefix>-->
      <!--              <svg-icon icon-class="validCode" class="el-input__icon input-icon"/>-->
      <!--            </template>-->
      <!--          </el-input>-->
      <!--          <div class="login-code">-->
      <!--            <img :src="codeUrl" @click="getCode" class="login-code-img"/>-->
      <!--          </div>-->
      <!--        </el-form-item>-->
      <!--        <el-checkbox v-model="loginForm.rememberMe" style="margin:0px 0px 25px 0px;">记住密码</el-checkbox>-->
      <!--        <el-form-item style="width:100%;">-->
      <!--          <el-button-->
      <!--              :loading="loading"-->
      <!--              size="large"-->
      <!--              type="primary"-->
      <!--              style="width:100%;"-->
      <!--              @click.prevent="handleLogin"-->
      <!--          >-->
      <!--            <span v-if="!loading">登 录</span>-->
      <!--            <span v-else>登 录 中...</span>-->
      <!--          </el-button>-->
      <!--          <div style="float: right;" v-if="register">-->
      <!--            <router-link class="link-type" :to="'/register'">立即注册</router-link>-->
      <!--          </div>-->
      <!--        </el-form-item>-->
      <!--      </el-form>-->
      <div class="el-login-footer">
        <span>Copyright © 2024-2025 视频监控平台 All Rights Reserved.</span>
      </div>
    </div>
    <!--  底部  -->

    <!--    <el-dialog title="关注公众号" v-model="open" width="32%" append-to-body>-->
    <!--      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="120px">-->
    <!--        <el-form-item label="公众号二维码">-->
    <!--          <el-image style="width: 150px" :src="gzhImage" :preview-src-list="[gzhImage]"></el-image>-->
    <!--        </el-form-item>-->
    <!--        <el-form-item label="公众号code" prop="publicCode">-->
    <!--          <template #label>-->
    <!--            <span>-->
    <!--               <el-tooltip-->
    <!--                   content="关注公众号获取"-->
    <!--                   placement="top">-->
    <!--                  <el-icon><question-filled/></el-icon>-->
    <!--               </el-tooltip>-->
    <!--               公众号code-->
    <!--            </span>-->
    <!--          </template>-->
    <!--          <el-input-->
    <!--              v-model="loginForm.publicCode"-->
    <!--              type="text"-->
    <!--              size="large"-->
    <!--              placeholder="请输入公众号code"-->
    <!--          >-->
    <!--          </el-input>-->
    <!--        </el-form-item>-->
    <!--      </el-form>-->
    <!--      <template #footer>-->
    <!--        <div class="dialog-footer">-->
    <!--          <el-button type="primary" @click="submitForm">确 定</el-button>-->
    <!--          <el-button @click="open = false">取 消</el-button>-->
    <!--        </div>-->
    <!--      </template>-->
    <!--    </el-dialog>-->
  </div>
</template>

<script setup>
import useUserStore from '@/store/modules/user'
import {isHttp} from "@/utils/validate.js";
import {isRelogin} from "@/utils/request.js";
import {setToken} from "@/utils/auth.js";
import usePermissionStore from "@/store/modules/permission.js";
import {useRoute, useRouter} from "vue-router";

const title = import.meta.env.VITE_APP_TITLE;
const userStore = useUserStore();
const route = useRoute();
const router = useRouter();
const {proxy} = getCurrentInstance();

const loginForm = ref({
  username: "ry",
  password: "123456",
  rememberMe: false,
  code: "",
  uuid: "",
  publicCode: ""
});

const codeUrl = ref("");
const loading = ref(false);
// 验证码开关
const captchaEnabled = ref(true);
// 注册开关
const redirect = ref(undefined);

watch(route, (newRoute) => {
  redirect.value = newRoute.query && newRoute.query.redirect;
}, {immediate: true});

function login() {
  loading.value = true;
  // 调用action的登录方法
  userStore.login(loginForm.value).then(() => {
    const query = route.query;
    const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
      if (cur !== "redirect") {
        acc[cur] = query[cur];
      }
      return acc;
    }, {});
    router.push({path: redirect.value || "/", query: query});
  }).catch(() => {
    loading.value = false;
  });
}

function submitForm(){
  proxy.$refs.loginFormRef.validate(valid => {
    if(valid){
      login()
    }
  })
}

onMounted((()=>{
  const accessToken = window.location.search.split("?accessToken=")[1].replace("&fromWhere=desktopHome", "").replace("&fromWhere=console_manage", "");
  if (accessToken) {
    setToken(accessToken);
    console.log(accessToken);
    useUserStore()
        .getInfo()
        .then(() => {
          isRelogin.show = false;
          usePermissionStore()
              .generateRoutes()
              .then((accessRoutes) => {
                accessRoutes.forEach((route) => {
                  if (!isHttp(route.path)) {
                    router.addRoute(route);
                  }
                });
                next({ ...to, replace: true });
              });
        })
    router.push({ path: "/index" }).catch(() => { });
  }
}))
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  height: 100%;
  background-image: url("../assets/images/bg-url.png");
  background-size: cover;
}

.title {
  margin: 0px auto 30px auto;
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
  border-radius: 25px 0px 0px 25px;
  box-shadow: rgba(0, 0, 0, 0.16) 0px 1px 4px;
}

.login-form {
  border-radius: 6px;
  background: #ffffff;
  width: 500px;
  padding: 20px 20px 5px 20px;

  .el-input {
    height: 40px;

    input {
      height: 40px;
    }
  }

  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0px;
  }
}

.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
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
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}

.login-code-img {
  height: 40px;
  padding-left: 12px;
}
</style>
