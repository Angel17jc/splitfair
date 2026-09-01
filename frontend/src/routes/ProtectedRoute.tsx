import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'
import ComprobandoSesion from './ComprobandoSesion'

/**
 * Envuelve las rutas que exigen sesion.
 *
 * Las tres ramas importan por igual:
 *
 * - **comprobando**: al recargar todavia no se sabe si hay sesion. Redirigir
 *   aqui expulsaria al login a un usuario perfectamente autenticado, cada vez
 *   que refresca la pagina.
 * - **anonimo**: al login, guardando de donde venia.
 * - **autenticado**: adelante.
 *
 * El destino se conserva en `state.from` y se navega con `replace` para que
 * el login no quede en el historial: sin eso, el boton de atras devuelve al
 * usuario recien autenticado a la pantalla de acceso.
 */
export default function ProtectedRoute() {
  const { estado } = useAuth()
  const ubicacion = useLocation()

  if (estado === 'comprobando') {
    return <ComprobandoSesion />
  }

  if (estado === 'anonimo') {
    return <Navigate to="/login" state={{ from: ubicacion }} replace />
  }

  return <Outlet />
}
