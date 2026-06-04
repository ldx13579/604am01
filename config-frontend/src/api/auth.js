import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const login = (username, password) =>
  api.post('/auth/login', { username, password })

export const getMe = (token) =>
  api.get('/auth/me', { headers: { Authorization: 'Bearer ' + token } })
