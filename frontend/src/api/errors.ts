/**
 * Un unico tipo de error para toda la aplicacion.
 *
 * Sin esto, cada pantalla acaba escarbando en `error.response.data.message`
 * con encadenamiento opcional por todas partes, y los fallos de red —donde no
 * hay `response` en absoluto— se cuelan como `undefined` y se pintan en
 * blanco. Aqui se normaliza una vez: todo lo que sale de la capa de API es un
 * `ApiError` con un `message` que se puede mostrar tal cual.
 */

import { AxiosError } from 'axios'
import type { ErrorBody } from '../types/api'

/** No hubo respuesta: servidor caido, DNS, CORS o el usuario sin conexion. */
export const SIN_RESPUESTA = 0

export class ApiError extends Error {
  /** `0` si la peticion nunca llego a obtener respuesta. */
  readonly status: number

  /**
   * Errores por campo de los 400 de validacion. Vacio en los demas casos.
   * Se mapea directo a `setError` de react-hook-form.
   */
  readonly fieldErrors: Record<string, string>

  /**
   * Identificador corto que el backend registra junto al stack trace. Solo
   * viene en los errores inesperados, y es lo unico que permite localizar en
   * el log lo que el usuario vio en pantalla.
   */
  readonly traceId?: string

  /** Segundos que pide esperar un 429, si los indica. */
  readonly retryAfter?: number

  constructor(
    message: string,
    status: number,
    opciones: {
      fieldErrors?: Record<string, string>
      traceId?: string
      retryAfter?: number
    } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = opciones.fieldErrors ?? {}
    this.traceId = opciones.traceId
    this.retryAfter = opciones.retryAfter
  }

  /** 400 con detalle por campo: el formulario puede senalar cual falla. */
  get esDeValidacion() {
    return this.status === 400 && Object.keys(this.fieldErrors).length > 0
  }

  /** 401: no sabemos quien eres. El interceptor ya intento renovar. */
  get esDeSesion() {
    return this.status === 401
  }

  /**
   * 403: sabemos quien eres y no puedes.
   *
   * Se distingue del 401 a proposito: renovar el token no cambiaria nada, y
   * reintentar contra un recurso al que no se tiene derecho es un bucle.
   */
  get esDePermisos() {
    return this.status === 403
  }

  /** 429: cupo agotado. `retryAfter` dice cuanto esperar. */
  get esDeCupo() {
    return this.status === 429
  }

  get esDeRed() {
    return this.status === SIN_RESPUESTA
  }
}

const MENSAJE_SIN_RESPUESTA =
  'No se pudo conectar con el servidor. Comprueba tu conexion e intentalo de nuevo.'

const MENSAJE_GENERICO = 'Ha ocurrido un error inesperado. Intentalo de nuevo.'

/** Convierte cualquier fallo de Axios en un ApiError presentable. */
export function comoApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (!(error instanceof AxiosError)) {
    return new ApiError(MENSAJE_GENERICO, SIN_RESPUESTA)
  }

  const respuesta = error.response
  if (!respuesta) {
    return new ApiError(MENSAJE_SIN_RESPUESTA, SIN_RESPUESTA)
  }

  const cuerpo = respuesta.data as Partial<ErrorBody> | undefined

  return new ApiError(cuerpo?.message?.trim() || MENSAJE_GENERICO, respuesta.status, {
    fieldErrors: cuerpo?.fieldErrors,
    traceId: cuerpo?.traceId,
    retryAfter: segundosDeEspera(respuesta.headers as Record<string, unknown>),
  })
}

/**
 * Lee `Retry-After`.
 *
 * La cabecera admite segundos o una fecha HTTP; el backend manda segundos,
 * pero se contempla la fecha porque un proxy o un balanceador delante pueden
 * responder con la otra forma y el cliente veria un NaN.
 */
function segundosDeEspera(cabeceras: Record<string, unknown> | undefined): number | undefined {
  const valor = cabeceras?.['retry-after']
  if (typeof valor !== 'string' && typeof valor !== 'number') {
    return undefined
  }

  const segundos = Number(valor)
  if (Number.isFinite(segundos)) {
    return Math.max(0, Math.ceil(segundos))
  }

  const instante = Date.parse(String(valor))
  if (Number.isNaN(instante)) {
    return undefined
  }
  return Math.max(0, Math.ceil((instante - Date.now()) / 1000))
}
