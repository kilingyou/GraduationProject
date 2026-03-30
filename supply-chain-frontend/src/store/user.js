import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { login, getUserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: null,
    roles: [],
    menus: []
  }),

  getters: {
    isLoggedIn: state => !!state.token,
    roleKey: state => state.userInfo?.roleKey || '',
    userId: state => state.userInfo?.id || null
  },

  actions: {
    async doLogin(loginForm) {
      const res = await login(loginForm)
      this.token = res.data.token
      setToken(res.data.token)
      this.userInfo = res.data.user
      this.menus = res.data.menus || []
      return res
    },

    async fetchUserInfo() {
      const res = await getUserInfo()
      this.userInfo = res.data.user
      this.menus = res.data.menus || []
      return res
    },

    logout() {
      this.token = ''
      this.userInfo = null
      this.menus = []
      removeToken()
    }
  }
})
