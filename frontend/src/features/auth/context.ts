import { createContext } from 'react'
import type { Auth, LoginInput, RegisterInput } from '../../types/api'

/** Quien esta dentro. Es lo que la interfaz necesita saber del usuario. */
export interface UsuarioEnSesion {
  userId: number
  name: string
  email: string
}

/**
 * Los tres estados posibles, explicitos.
 *
 * `comprobando` existe porque al arrancar no se sabe todavia si hay sesion:
 * el access token vive en memoria y se ha perdido al recargar, asi que hay
 * que preguntarle al servidor. Sin este estado intermedio el unico valor
 * disponible seria "no autenticado", y toda recarga de una pagina privada
 * rebotaria al login un instante antes de que llegara la respuesta.
 */
export type EstadoDeSesion = 'comprobando' | 'autenticado' | 'anonimo'

export interface Sesion {
  estado: EstadoDeSesion
  usuario: UsuarioEnSesion | null
  entrar(datos: LoginInput): Promise<Auth>
  registrarse(datos: RegisterInput): Promise<Auth>
  salir(): Promise<void>
  /**
   * Refleja un cambio de nombre ya guardado en el servidor.
   *
   * Vive en el contexto y no solo en el modulo de sesion porque lo que pinta
   * la cabecera es el estado de React: actualizar unicamente la copia en
   * memoria dejaria el menu mostrando el nombre viejo hasta la siguiente
   * recarga.
   */
  renombrar(nombre: string): void
}

export const ContextoDeSesion = createContext<Sesion | null>(null)
