/**
 * Cliente HTTP de la aplicacion.
 *
 * Resuelve dos cosas que, hechas a mano en cada pantalla, se hacen mal:
 * adjuntar el access token y renovarlo cuando caduca sin que el usuario se
 * entere.
 */

import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { Auth } from '../types/api'
import { comoApiError } from './errors'
import { sesion } from './session'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

/**
 * `withCredentials` es imprescindible, no una opcion.
 *
 * El refresh token vive en una cookie HttpOnly. En una peticion entre origenes
 * distintos —y en desarrollo lo son: el frontend esta en el 5173 y la API en
 * el 8080— el navegador **no envia cookies** salvo que se pida expresamente.
 * Sin esta linea, `/auth/refresh` no recibiria la cookie y todo refresco
 * fallaria con 401, con el sintoma desconcertante de que la sesion se cae
 * exactamente a los 15 minutos.
 */
const configuracionComun = {
  baseURL: BASE_URL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
}

export const apiClient = axios.create(configuracionComun)

/**
 * Cliente aparte, sin interceptores, dedicado a renovar.
 *
 * Si el refresco viajara por `apiClient`, un 401 de `/auth/refresh` volveria
 * a entrar en el interceptor que intenta refrescar, y de ahi a una recursion
 * que solo para cuando revienta la pila.
 */
const clienteDeRefresco = axios.create(configuracionComun)

// --- token en cada peticion ------------------------------------------------

apiClient.interceptors.request.use((config) => {
  const token = sesion.token()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// --- renovacion transparente -----------------------------------------------

interface PeticionReintentable extends InternalAxiosRequestConfig {
  yaReintentada?: boolean
}

/**
 * Refresco en vuelo, compartido.
 *
 * Sin esto, si caducan cinco peticiones a la vez se lanzan cinco refrescos.
 * Medido: quitando esta guarda, una pantalla con cinco consultas produce
 * cinco llamadas a /auth/refresh en vez de una.
 *
 * Y el coste no es solo el desperdicio. La rotacion invalida cada refresh
 * token al usarlo, asi que un refresco que salga antes de recibir la cookie
 * renovada presenta un token ya rotado, y el backend lo lee como
 * **reutilizacion**: revoca la familia entera y expulsa al usuario. Depende
 * de la carrera entre las respuestas, asi que no falla siempre — lo cual lo
 * empeora, porque se convierte en un cierre de sesion intermitente que nadie
 * consigue reproducir.
 *
 * Guardando la promesa, las cinco esperan al mismo refresco y reintentan con
 * el token que salga de el.
 */
let refrescoEnCurso: Promise<string> | null = null

function renovarAccessToken(): Promise<string> {
  if (!refrescoEnCurso) {
    refrescoEnCurso = clienteDeRefresco
      // Sin cuerpo: la credencial la aporta el navegador en la cookie.
      .post<Auth>('/auth/refresh')
      .then(({ data }) => {
        sesion.abrir(data)
        return data.accessToken
      })
      .catch((error: unknown) => {
        // Una sola notificacion aunque hubiera diez peticiones esperando: si
        // no, el AuthProvider dispararia diez redirecciones al login.
        sesion.notificarPerdida()
        throw comoApiError(error)
      })
      .finally(() => {
        refrescoEnCurso = null
      })
  }
  return refrescoEnCurso
}

/**
 * Las rutas de `/auth` se quedan fuera de la renovacion.
 *
 * Un 401 de `/auth/login` significa "contrasena incorrecta", no "token
 * caducado". Intentar refrescar ahi convertiria un error que el formulario
 * debe mostrar en una redireccion al login que ya esta en pantalla.
 */
function esDeAutenticacion(url: string | undefined) {
  return url?.startsWith('/auth/') ?? false
}

apiClient.interceptors.response.use(
  (respuesta) => respuesta,
  async (error: unknown) => {
    if (!(error instanceof AxiosError)) {
      throw comoApiError(error)
    }

    const peticion = error.config as PeticionReintentable | undefined

    // Solo el 401 se renueva. El 403 dice "se quien eres y no puedes": el
    // token es valido y refrescarlo daria otro identico, con lo que el
    // reintento se repetiria indefinidamente contra un recurso ajeno.
    const renovable =
      error.response?.status === 401 &&
      peticion !== undefined &&
      !peticion.yaReintentada &&
      !esDeAutenticacion(peticion.url)

    if (!renovable) {
      throw comoApiError(error)
    }

    // Se marca antes de renovar: si el reintento tambien diera 401, la
    // peticion sale por la rama de arriba en vez de volver a empezar.
    peticion.yaReintentada = true

    let token: string
    try {
      token = await renovarAccessToken()
    } catch {
      // El refresco fallo, la sesion esta muerta y ya se ha notificado. Se
      // propaga el 401 original: es el error de la peticion que el usuario
      // hizo, no el del refresco interno que nunca pidio.
      throw comoApiError(error)
    }

    peticion.headers.Authorization = `Bearer ${token}`
    return apiClient.request(peticion)
  },
)

/** Emite credenciales por la ruta de refresco. Lo usa el arranque de sesion. */
export async function refrescarSesion(): Promise<Auth> {
  try {
    const { data } = await clienteDeRefresco.post<Auth>('/auth/refresh')
    sesion.abrir(data)
    return data
  } catch (error) {
    throw comoApiError(error)
  }
}
