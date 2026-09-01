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
}

export const ContextoDeSesion = createContext<Sesion | null>(null)
