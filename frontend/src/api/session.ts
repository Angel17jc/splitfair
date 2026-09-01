/**
 * El access token vive aqui: en memoria del modulo, nunca en `localStorage`.
 *
 * ## Por que no localStorage
 *
 * Todo lo que hay en `localStorage` es legible por cualquier script que
 * consiga ejecutarse en la pagina. Una sola dependencia comprometida, un
 * `dangerouslySetInnerHTML` descuidado, y la credencial se va. En memoria, en
 * cambio, el token muere con la pestana y no queda rastro en disco.
 *
 * ## Que pasa al recargar la pagina
 *
 * El token se pierde, y es lo esperado. La sesion se recupera pidiendo uno
 * nuevo a `/auth/refresh`, que se autentica con la cookie HttpOnly que el
 * navegador conserva y que este codigo **no puede leer**. Esa es justo la
 * division que se busca: lo que un XSS puede robar dura 15 minutos; lo que
 * dura 30 dias, no puede tocarlo.
 *
 * ## Por que un modulo y no un contexto de React
 *
 * El interceptor de Axios necesita el token y corre fuera del arbol de
 * componentes. Pasarlo por contexto obligaria a inyectarlo en el cliente
 * desde un efecto, con una ventana en la que el cliente ya existe y todavia
 * no tiene token. Aqui la fuente es unica y no depende del ciclo de vida de
 * React; el contexto (commit 2) se limita a reflejarla para la interfaz.
 */

import type { Auth } from '../types/api'

let accessToken: string | null = null

/** Quien esta dentro, o null si no hay sesion. */
let usuario: Pick<Auth, 'userId' | 'name' | 'email'> | null = null

/**
 * Avisa de que la sesion ha muerto y no se ha podido renovar.
 *
 * Lo registra el AuthProvider para redirigir al login. Es una funcion y no un
 * evento del DOM para que quede explicito quien escucha: un solo interesado,
 * y se ve en el codigo.
 */
let alPerderLaSesion: (() => void) | null = null

export const sesion = {
  token: () => accessToken,

  usuario: () => usuario,

  hayToken: () => accessToken !== null,

  /** Guarda las credenciales recien emitidas o renovadas. */
  abrir(auth: Auth) {
    accessToken = auth.accessToken
    usuario = { userId: auth.userId, name: auth.name, email: auth.email }
  },

  /**
   * Borra el token de memoria.
   *
   * No hay nada mas que limpiar: la cookie la borra el backend con su
   * `Set-Cookie` de `Max-Age=0` al cerrar sesion. Intentar borrarla desde
   * aqui seria imposible de todos modos, por ser HttpOnly.
   */
  cerrar() {
    accessToken = null
    usuario = null
  },

  observarPerdida(callback: (() => void) | null) {
    alPerderLaSesion = callback
  },

  /**
   * Uso interno cuando el refresco falla definitivamente.
   *
   * Solo se pierde una sesion que existia. La guarda hace dos cosas a la vez:
   *
   * - Si diez peticiones esperaban al mismo refresco y este falla, la primera
   *   cierra la sesion y avisa; las nueve restantes ya la encuentran cerrada
   *   y no vuelven a avisar. Sin esto habria diez redirecciones al login.
   * - Un intento de restaurar la sesion al arrancar que falla porque
   *   simplemente no habia ninguna **no es una perdida**. Sin la guarda,
   *   abrir una pagina publica sin haber iniciado sesion expulsaria al
   *   visitante al login.
   */
  notificarPerdida() {
    if (accessToken === null && usuario === null) {
      return
    }
    this.cerrar()
    alPerderLaSesion?.()
  },
}
