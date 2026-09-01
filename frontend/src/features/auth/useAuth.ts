import { useContext } from 'react'
import { ContextoDeSesion, type Sesion } from './context'

/**
 * Acceso a la sesion desde cualquier componente.
 *
 * Lanza si se usa fuera del provider en vez de devolver un valor por defecto.
 * Un contexto nulo por defecto convertiria el olvido de montar `AuthProvider`
 * en una pantalla que se comporta como si nadie hubiera iniciado sesion:
 * un sintoma confuso y a varios pasos de su causa. Fallar aqui senala el
 * problema exacto.
 */
export function useAuth(): Sesion {
  const sesion = useContext(ContextoDeSesion)

  if (sesion === null) {
    throw new Error('useAuth se ha usado fuera de <AuthProvider>')
  }

  return sesion
}
