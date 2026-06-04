import axios from 'axios'
import auth from '../store/auth'
import router from '../router/index'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = auth.getToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

api.interceptors.response.use(
  response => {
    const refreshedToken = response.headers['x-refreshed-token']
    if (refreshedToken) {
      auth.updateToken(refreshedToken)
    }
    return response
  },
  error => {
    if (error.response && error.response.status === 401) {
      auth.logout()
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export const getConfigs = (env, ns = 'default') =>
  api.get('/configs', { params: { env, ns } })

export const createConfig = (data) =>
  api.post('/configs', data)

export const updateConfig = (id, data) =>
  api.put(`/configs/${id}`, data)

export const deleteConfig = (id) =>
  api.delete(`/configs/${id}`)

export const getVersionHistory = (id) =>
  api.get(`/configs/${id}/versions`)

export const rollbackConfig = (id, targetVersion) =>
  api.post(`/configs/${id}/rollback`, null, { params: { targetVersion } })

export const getCurrentVersion = (env, ns = 'default') =>
  api.get('/version', { params: { env, ns } })

export const getGrayscaleRules = (env, ns = 'default') =>
  api.get('/grayscale/rules', { params: { env, ns } })

export const createGrayscaleRule = (data) =>
  api.post('/grayscale/rules', data)

export const updateGrayscaleRule = (id, data) =>
  api.put(`/grayscale/rules/${id}`, data)

export const fullReleaseRule = (id) =>
  api.post(`/grayscale/rules/${id}/full-release`)

export const cancelRule = (id) =>
  api.post(`/grayscale/rules/${id}/cancel`)

export const getClients = (env, ns = 'default') =>
  api.get('/grayscale/clients', { params: { env, ns } })

export const changePassword = (oldPassword, newPassword) =>
  api.post('/auth/change-password', { oldPassword, newPassword })

export const refreshToken = () =>
  api.post('/auth/refresh')
