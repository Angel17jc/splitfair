import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/errors'
import { aplicarErrorDeApi, describirEspera, mensajeGeneral } from './formularios'

const CAMPOS = ['name', 'email', 'password'] as const

/**
 * El puente entre los errores de la API y lo que ve el usuario en el
 * formulario.
 *
 * Lo que estos tests protegen es que **ningun mensaje se pierda por el
 * camino**. Un formulario que rechaza el envio sin decir por que es la peor
 * version del error: el usuario no sabe si fallo el servidor, si escribio algo
 * mal, o cual de los cuatro campos es el problema.
 */
describe('aplicarErrorDeApi', () => {
  it('coloca cada fieldError bajo su campo', () => {
    const setError = vi.fn()
    const error = new ApiError('Datos invalidos', 400, {
      fieldErrors: { email: 'El email ya esta en uso', password: 'Demasiado corta' },
    })

    aplicarErrorDeApi(error, setError, CAMPOS)

    expect(setError).toHaveBeenCalledWith('email', { message: 'El email ya esta en uso' })
    expect(setError).toHaveBeenCalledWith('password', { message: 'Demasiado corta' })
  })

  it('un error sin desglose sube a la cabecera del formulario', () => {
    // Un 401 de login no trae fieldErrors, pero su mensaje es justo lo que hay
    // que ensenar: "Credenciales invalidas".
    const setError = vi.fn()

    aplicarErrorDeApi(new ApiError('Credenciales invalidas', 401), setError, CAMPOS)

    expect(setError).toHaveBeenCalledWith('root', { message: 'Credenciales invalidas' })
  })

  it('un fieldError de un campo que el formulario no tiene NO se descarta', () => {
    // Es el caso que se traga los errores en silencio: el backend valida un
    // campo que este formulario no pinta —invitationToken, por ejemplo—, no
    // hay donde ponerlo, y el usuario ve el envio rechazado sin explicacion.
    const setError = vi.fn()
    const error = new ApiError('Datos invalidos', 400, {
      fieldErrors: { invitationToken: 'La invitacion ha caducado' },
    })

    aplicarErrorDeApi(error, setError, CAMPOS)

    expect(setError).toHaveBeenCalledWith('root', { message: 'La invitacion ha caducado' })
  })

  it('combina los de campo con los que no encajan en ninguno', () => {
    const setError = vi.fn()
    const error = new ApiError('Datos invalidos', 400, {
      fieldErrors: { email: 'Formato invalido', otroCampo: 'Algo mas fallo' },
    })

    aplicarErrorDeApi(error, setError, CAMPOS)

    expect(setError).toHaveBeenCalledWith('email', { message: 'Formato invalido' })
    expect(setError).toHaveBeenCalledWith('root', { message: 'Algo mas fallo' })
  })

  it('un fallo que no es de la API tambien dice algo', () => {
    // Un TypeError dentro del manejador no puede dejar el formulario mudo.
    const setError = vi.fn()

    aplicarErrorDeApi(new TypeError('roto'), setError, CAMPOS)

    expect(setError).toHaveBeenCalledWith('root', {
      message: expect.stringContaining('inesperado'),
    })
  })

  it('el 429 explica cuanto hay que esperar, no solo que se agoto el cupo', () => {
    const setError = vi.fn()

    aplicarErrorDeApi(new ApiError('Demasiados intentos', 429, { retryAfter: 180 }), setError, CAMPOS)

    expect(setError).toHaveBeenCalledWith('root', {
      message: expect.stringContaining('3 minutos'),
    })
  })
})

describe('describirEspera', () => {
  it('convierte los segundos en algo que se lee', () => {
    // El limite de acceso es de 15 minutos: decir "espera 899 segundos"
    // obliga al usuario a hacer la division.
    expect(describirEspera(45)).toBe('45 segundos')
    expect(describirEspera(60)).toBe('un minuto')
    expect(describirEspera(180)).toBe('3 minutos')
    expect(describirEspera(899)).toBe('15 minutos')
  })

  it('sin dato no inventa una cifra', () => {
    // La cabecera Retry-After puede no llegar; decir "espera 0 segundos"
    // seria peor que ser vago.
    expect(describirEspera(undefined)).toBe('unos minutos')
    expect(describirEspera(0)).toBe('unos minutos')
  })
})

describe('mensajeGeneral', () => {
  it('para el resto de errores usa el mensaje del backend tal cual', () => {
    expect(mensajeGeneral(new ApiError('El grupo no existe', 404))).toBe('El grupo no existe')
  })
})
