import { Navigate, Route, Routes } from 'react-router-dom'
import Login from '../pages/Login'
import Register from '../pages/Register'
import Dashboard from '../pages/Dashboard'
import NotFound from '../pages/NotFound'
import AnonymousRoute from './AnonymousRoute'
import ProtectedRoute from './ProtectedRoute'

export default function AppRoutes() {
  return (
    <Routes>
      {/* Solo para quien no ha entrado. */}
      <Route element={<AnonymousRoute />}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
      </Route>

      {/* Exigen sesion. Agrupadas para que anadir una pantalla privada no
          implique acordarse de protegerla: se cuelga de esta rama y ya. */}
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<Dashboard />} />
      </Route>

      {/* La raiz lleva al dashboard, no al login: si hay sesion se entra
          directo, y si no, ProtectedRoute redirige. Mandar siempre al login
          obligaria a pasar por el incluso teniendo la sesion abierta. */}
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
