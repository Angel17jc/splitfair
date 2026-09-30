import { useMutation } from '@tanstack/react-query'
import { actualizarPerfil, cambiarContrasena, darDeBajaCuenta } from '../../api/users'
import type { ChangePasswordInput, DeleteAccountInput } from '../../types/api'

/**
 * Cambia el nombre visible.
 *
 * No invalida ninguna consulta, y es deliberado. El nombre viaja **dentro** de
 * cada gasto, balance y liquidacion ya cargados, asi que refrescarlo de verdad
 * obligaria a invalidar la cache entera de todos los grupos. Para un cambio
 * que casi nadie hace dos veces, eso es mucho trafico a cambio de poco: los
 * datos se actualizan solos al navegar. Lo que si se actualiza al instante es
 * la sesion en memoria, que es lo que pinta la cabecera.
 */
export function useCambiarNombre(alRenombrar: (nombre: string) => void) {
  return useMutation({
    mutationFn: (nombre: string) => actualizarPerfil(nombre),
    onSuccess: (usuario) => alRenombrar(usuario.name),
  })
}

/**
 * Cambia la contrasena.
 *
 * Quien la llame debe cerrar la sesion despues: el backend revoca **todos** los
 * refresh tokens, incluido el de esta pestana. No se hace aqui para que el
 * componente decida el momento y pueda mostrar antes lo que ha pasado.
 */
export function useCambiarContrasena() {
  return useMutation({
    mutationFn: (datos: ChangePasswordInput) => cambiarContrasena(datos),
  })
}

/**
 * Da de baja la cuenta. Tambien exige cerrar sesion despues.
 *
 * El acceso se pierde en el acto, no al caducar el token: el access token
 * lleva el correo como sujeto y la baja lo sustituye, asi que deja de resolver
 * a ningun usuario. Sin cerrar la sesion local, la pantalla se quedaria
 * aparentemente dentro y cada peticion respondiendo 401.
 */
export function useDarDeBajaCuenta() {
  return useMutation({
    mutationFn: (datos: DeleteAccountInput) => darDeBajaCuenta(datos),
  })
}
