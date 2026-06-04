import { reactive } from 'vue'
import axios from 'axios'

const state = reactive({
  token: localStorage.getItem('token') || '',
  user: null,
  isAuthenticated: false,
  forcePasswordChange: false
})

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

async function login(username, password) {
  const res = await api.post('/auth/login', { username, password })
  state.token = res.data.token
  state.user = { username: res.data.username, displayName: res.data.displayName, roles: res.data.roles }
  state.isAuthenticated = true
  state.forcePasswordChange = res.data.forcePasswordChange || false
  localStorage.setItem('token', state.token)
  return res.data
}

async function fetchMe() {
  if (!state.token) return false
  try {
    const res = await api.get('/auth/me', {
      headers: { Authorization: 'Bearer ' + state.token }
    })
    state.user = res.data
    state.isAuthenticated = true
    return true
  } catch (e) {
    logout()
    return false
  }
}

function logout() {
  state.token = ''
  state.user = null
  state.isAuthenticated = false
  state.forcePasswordChange = false
  localStorage.removeItem('token')
}

function getToken() {
  return state.token
}

function updateToken(newToken) {
  state.token = newToken
  localStorage.setItem('token', newToken)
}

function isAdmin() {
  if (!state.user || !state.user.roles) return false
  return state.user.roles.some(r => r.role === 'ADMIN')
}

export default { state, login, logout, fetchMe, getToken, updateToken, isAdmin }
