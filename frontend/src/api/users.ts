/** Perfil del usuario autenticado. */

import { apiClient } from './client'
import type { ChangePasswordInput, DeleteAccountInput, User } from '../types/api'

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

/**
 * Da de baja la cuenta.
 *
 * **No borra la fila, la anonimiza.** Quien se da de baja aparece en gastos,
 * repartos y liquidaciones confirmadas; borrarlo dejaria apuntes sin dueno y
 * los balances del grupo dejarian de sumar cero. Lo que se elimina son los
 * datos personales: nombre y correo se sustituyen y la contrasena se vuelve
 * irrecuperable, asi que la cuenta queda inutilizable.
 *
 * El acceso se pierde en el acto: el access token lleva el correo como sujeto
 * y al sustituirlo deja de resolver a ningun usuario. Quien la invoque debe
 * limpiar la sesion local y llevar al inicio.
 */
export async function darDeBajaCuenta(datos: DeleteAccountInput): Promise<void> {
  // DELETE con cuerpo: la contrasena no puede ir en la URL, donde quedaria en
  // los logs de acceso del proxy y en el historial del navegador.
  await apiClient.delete('/users/me', { data: datos })
}
