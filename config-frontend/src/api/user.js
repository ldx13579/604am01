import axios from 'axios'
import auth from '../store/auth'

const api = axios.create({ baseURL: '/api', timeout: 10000 })
api.interceptors.request.use(config => {
  const token = auth.getToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

export const getUsers = () => api.get('/users')
export const createUser = (data) => api.post('/users', data)
export const deleteUser = (id) => api.delete(`/users/${id}`)
export const assignRole = (userId, data) => api.post(`/users/${userId}/roles`, data)
export const removeRole = (userId, roleId) => api.delete(`/users/${userId}/roles/${roleId}`)
