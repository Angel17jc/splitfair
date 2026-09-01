/** Perfil del usuario autenticado. */

import { apiClient } from './client'
import type { ChangePasswordInput, User } from '../types/api'

export async function obtenerPerfil(): Promise<User> {
  const { data } = await apiClient.get<User>('/users/me')
  return data
}

export async function actualizarPerfil(name: string): Promise<User> {
  const { data } = await apiClient.patch<User>('/users/me', { name })
  return data
}

/**
 * Cambia la contrasena y **revoca todas las sesiones**, incluida la actual.
 *
 * Quien la cambia suele hacerlo porque sospecha que alguien mas tiene acceso;
 * si las sesiones abiertas sobrevivieran, el intruso conservaria un refresh
 * token valido treinta dias. Tras esta llamada hay que volver a iniciar
 * sesion, asi que quien la invoque debe llevar al login.
 */
export async function cambiarContrasena(datos: ChangePasswordInput): Promise<void> {
  await apiClient.post('/users/me/password', datos)
}
