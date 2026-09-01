import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'
import ComprobandoSesion from './ComprobandoSesion'

interface EstadoDeNavegacion {
  from?: { pathname?: string }
}

/**
 * Lo contrario de ProtectedRoute: login y registro, solo para quien no ha
 * entrado.
 *
 * Sin esta guarda, un usuario con sesion abierta que escriba `/login` ve un
 * formulario para volver a identificarse. No es solo raro: si lo rellena,
 * abre una segunda sesion y deja la primera colgando en la base de datos.
 *
 * Al redirigir se respeta el destino que ProtectedRoute guardo, de modo que
 * quien fue enviado al login desde una pagina concreta vuelva a ella y no al
 * dashboard.
 */
export default function AnonymousRoute() {
  const { estado } = useAuth()
  const ubicacion = useLocation()

  if (estado === 'comprobando') {
    return <ComprobandoSesion />
  }

  if (estado === 'autenticado') {
    const destino = (ubicacion.state as EstadoDeNavegacion | null)?.from?.pathname
    return <Navigate to={destino ?? '/dashboard'} replace />
  }

  return <Outlet />
}
