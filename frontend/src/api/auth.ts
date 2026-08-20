import apiClient from './client'

export interface AuthResponse {
  token: string
  userId: number
  name: string
  email: string
}

export const register = async (name: string, email: string, password: string) => {
  const { data } = await apiClient.post<AuthResponse>('/auth/register', { name, email, password })
  return data
}

export const login = async (email: string, password: string) => {
  const { data } = await apiClient.post<AuthResponse>('/auth/login', { email, password })
  return data
}
