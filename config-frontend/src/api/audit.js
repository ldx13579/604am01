import axios from 'axios'
import auth from '../store/auth'

const api = axios.create({ baseURL: '/api', timeout: 10000 })
api.interceptors.request.use(config => {
  const token = auth.getToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

export const queryAuditLogs = (params) => api.get('/audit', { params })
