<template>
  <div class="nav_status flexRowAC">
    <span class="nav_title is-link" @click="handleBack">{{ parentTitle }}</span>
    <div class="nav_title lth">
      /&nbsp;&nbsp;<span>{{ title }}</span>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  parentTitle: {
    type: String,
    required: true
  },
  title: {
    type: String,
    required: true
  },
  /** 返回路径；不传则 router.back() */
  backPath: {
    type: String,
    default: ''
  }
})

const { proxy } = getCurrentInstance()
const router = useRouter()

function handleBack() {
  if (props.backPath) {
    proxy.$tab.closeOpenPage({ path: props.backPath })
    return
  }
  router.back()
}
</script>
