import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '../api/errors'

/**
 * Vuelca un error de la API sobre un formulario.
 *
 * Los 400 de validacion del backend traen `fieldErrors` con el nombre del
 * campo y su mensaje, que es exactamente lo que react-hook-form necesita para
 * pintar el error donde corresponde. Sin esto el usuario recibe un "revisa
 * los datos" generico y tiene que adivinar cual falla.
 *
 * Los mensajes que no corresponden a ningun campo del formulario —un
 * `fieldErrors` de un campo que aqui no existe, o un error sin desglose— no
 * se descartan: acaban en `root`, para que el formulario los muestre arriba.
 * Tragarselos dejaria un formulario que rechaza el envio sin decir por que.
 */
export function aplicarErrorDeApi<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  camposDelFormulario: readonly Path<T>[],
): void {
  const fallo = error instanceof ApiError ? error : null

  if (!fallo) {
    setError('root', { message: 'Ha ocurrido un error inesperado. Intentalo de nuevo.' })
    return
  }

  const sueltos: string[] = []
  let algunoColocado = false

  for (const [campo, mensaje] of Object.entries(fallo.fieldErrors)) {
    if ((camposDelFormulario as readonly string[]).includes(campo)) {
      setError(campo as Path<T>, { message: mensaje })
      algunoColocado = true
    } else {
      sueltos.push(mensaje)
    }
  }

  if (!algunoColocado || sueltos.length > 0) {
    setError('root', { message: sueltos.join(' ') || mensajeGeneral(fallo) })
  }
}

/** Texto para mostrar en la cabecera del formulario. */
export function mensajeGeneral(error: ApiError): string {
  if (error.esDeCupo) {
    return `Demasiados intentos. Vuelve a probar en ${describirEspera(error.retryAfter)}.`
  }
  return error.message
}

/**
 * Convierte los segundos de `Retry-After` en algo legible.
 *
 * El limite de acceso es de 15 minutos: decir "espera 899 segundos" obliga al
 * usuario a hacer la division.
 */
export function describirEspera(segundos: number | undefined): string {
  if (segundos === undefined || segundos <= 0) {
    return 'unos minutos'
  }
  if (segundos < 60) {
    return `${segundos} segundos`
  }
  const minutos = Math.ceil(segundos / 60)
  return minutos === 1 ? 'un minuto' : `${minutos} minutos`
}
