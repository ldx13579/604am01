import axios from 'axios'
import auth from '../store/auth'

const api = axios.create({ baseURL: '/api', timeout: 10000 })
api.interceptors.request.use(config => {
  const token = auth.getToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

export const getScripts = (env, ns = 'default') =>
  api.get('/validation/scripts', { params: { env, ns } })

export const createScript = (data) => api.post('/validation/scripts', data)
export const updateScript = (id, data) => api.put(`/validation/scripts/${id}`, data)
export const deleteScript = (id) => api.delete(`/validation/scripts/${id}`)
export const testScript = (data) => api.post('/validation/test', data)
