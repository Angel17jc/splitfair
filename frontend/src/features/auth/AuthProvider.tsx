import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  cerrarSesion,
  iniciarSesion,
  registrar,
  restaurarSesion,
} from '../../api/auth'
import { sesion as sesionEnMemoria } from '../../api/session'
import { ContextoDeSesion, type EstadoDeSesion, type UsuarioEnSesion } from './context'
import type { Auth, LoginInput, RegisterInput } from '../../types/api'

/**
 * Refleja en React la sesion que gestiona la capa de API.
 *
 * La fuente de verdad del access token sigue siendo el modulo `session`, no
 * este estado: el interceptor de Axios lo necesita y corre fuera del arbol de
 * componentes. Aqui solo se mantiene lo que la interfaz tiene que pintar.
 * Duplicar el token en el estado abriria la puerta a que ambos se
 * desincronizaran, y el que manda —el que viaja en las peticiones— seria
 * justo el que no se ve.
 */
export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const [estado, setEstado] = useState<EstadoDeSesion>('comprobando')
  const [usuario, setUsuario] = useState<UsuarioEnSesion | null>(null)
  const queryClient = useQueryClient()

  const adoptar = useCallback((auth: Auth) => {
    setUsuario({ userId: auth.userId, name: auth.name, email: auth.email })
    setEstado('autenticado')
  }, [])

  /**
   * Al arrancar se intenta cambiar la cookie por credenciales nuevas.
   *
   * Que falle es el caso normal de quien no ha entrado nunca: se queda
   * anonimo y no pasa nada mas. No se distingue el motivo del fallo a
   * proposito — no hay sesion, la cookie caduco, el token fue revocado —
   * porque para el usuario las tres cosas significan lo mismo: hay que
   * iniciar sesion.
   */
  const intentoDeArranque = useRef<Promise<Auth> | null>(null)

  useEffect(() => {
    // StrictMode monta, desmonta y vuelve a montar en desarrollo, asi que
    // este efecto se ejecuta dos veces. Lo que se guarda es la **promesa**, no
    // un booleano de "ya lo intente":
    //
    // - Con un booleano, la segunda ejecucion salia por un return temprano
    //   sin suscribirse a nada, mientras la limpieza de la primera ya habia
    //   invalidado su resultado. Nadie recogia la respuesta y la aplicacion
    //   se quedaba en "comprobando" para siempre.
    // - Guardando la promesa, ambas ejecuciones se enganchan a la misma
    //   peticion y la que sigue viva aplica el resultado. Una sola llamada a
    //   la red y, sobre todo, un solo uso del refresh token: gastarlo dos
    //   veces seria reutilizacion a ojos del backend, que revocaria la
    //   familia entera y expulsaria al usuario en cada recarga.
    if (!intentoDeArranque.current) {
      intentoDeArranque.current = restaurarSesion()
    }

    let vigente = true

    intentoDeArranque.current
      .then((auth) => {
        if (vigente) adoptar(auth)
      })
      .catch(() => {
        if (vigente) setEstado('anonimo')
      })

    return () => {
      vigente = false
    }
  }, [adoptar])

  /**
   * El interceptor avisa cuando un refresco falla y la sesion muere de
   * verdad. Basta con pasar a anonimo: las rutas protegidas reaccionan solas
   * y llevan al login conservando el destino. Navegar imperativamente desde
   * aqui obligaria a este componente a conocer el router y a decidir a que
   * ruta ir, que es justo lo que las rutas ya saben.
   */
  useEffect(() => {
    sesionEnMemoria.observarPerdida(() => {
      setUsuario(null)
      setEstado('anonimo')
      // Los datos en cache son del usuario que acaba de salir.
      queryClient.clear()
    })
    return () => sesionEnMemoria.observarPerdida(null)
  }, [queryClient])

  const entrar = useCallback(
    async (datos: LoginInput) => {
      const auth = await iniciarSesion(datos)
      adoptar(auth)
      return auth
    },
    [adoptar],
  )

  const registrarse = useCallback(
    async (datos: RegisterInput) => {
      const auth = await registrar(datos)
      adoptar(auth)
      return auth
    },
    [adoptar],
  )

  /**
   * Cierra sesion y **vacia la cache de consultas**.
   *
   * Sin ese vaciado, quien entre despues en la misma pestana veria por un
   * instante los grupos, gastos y saldos del anterior: TanStack Query sirve
   * lo que tiene guardado mientras revalida. En una aplicacion de dinero
   * compartido eso es una fuga de datos entre cuentas, no un parpadeo.
   */
  const salir = useCallback(async () => {
    try {
      await cerrarSesion()
    } finally {
      setUsuario(null)
      setEstado('anonimo')
      queryClient.clear()
    }
  }, [queryClient])

  const valor = useMemo(
    () => ({ estado, usuario, entrar, registrarse, salir }),
    [estado, usuario, entrar, registrarse, salir],
  )

  return <ContextoDeSesion.Provider value={valor}>{children}</ContextoDeSesion.Provider>
}
