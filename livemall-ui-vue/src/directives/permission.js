// v-permission 按钮级权限指令
// 用法：v-permission="['admin']" 或 v-permission.anchor
import { useUserStore } from '@/stores/user'

function check(roles) {
  const userStore = useUserStore()
  if (!roles || roles.length === 0) return true
  return roles.includes(userStore.role)
}

export const permissionDirective = {
  mounted(el, binding) {
    if (!check(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}
