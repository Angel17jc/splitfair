/**
 * Autenticacion.
 *
 * Ninguna de estas funciones toca el refresh token: lo pone y lo quita el
 * navegador a traves de la cookie HttpOnly. Cerrar sesion no necesita
 * "limpiar" nada guardado, porque nunca se guardo nada.
 */

import { apiClient, refrescarSesion } from './client'
import { comoApiError } from './errors'
import { sesion } from './session'
import type { Auth, LoginInput, RegisterInput } from '../types/api'

export async function registrar(datos: RegisterInput): Promise<Auth> {
  const { data } = await apiClient.post<Auth>('/auth/register', datos)
  sesion.abrir(data)
  return data
}

export async function iniciarSesion(datos: LoginInput): Promise<Auth> {
  const { data } = await apiClient.post<Auth>('/auth/login', datos)
  sesion.abrir(data)
  return data
}

/**
 * Recupera la sesion al cargar la aplicacion.
 *
 * El access token se pierde al recargar porque vive en memoria; la cookie
 * sobrevive. Esto cambia una por el otro.
 */
export const restaurarSesion = refrescarSesion

/**
 * Cierra sesion.
 *
 * El token local se borra pase lo que pase. Si la llamada falla —el servidor
 * no responde, la cookie ya habia caducado— dejar al usuario "dentro" seria
 * peor: cree que ha salido y la interfaz dice lo contrario. El backend
 * responde 204 aunque el token no existiera, asi que el caso normal no
 * distingue.
 */
export async function cerrarSesion(): Promise<void> {
  try {
    await apiClient.post('/auth/logout')
  } catch (error) {
    const fallo = comoApiError(error)
    // Un fallo de red no debe impedir salir; cualquier otro tampoco.
    if (!fallo.esDeRed && !fallo.esDeSesion) {
      throw fallo
    }
  } finally {
    sesion.cerrar()
  }
}
