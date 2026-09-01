import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import AuthProvider from './features/auth/AuthProvider'
import ErrorBoundary from './components/ErrorBoundary'
import { ApiError } from './api/errors'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      /**
       * Reintentar solo tiene sentido cuando el fallo puede resolverse solo.
       *
       * Por defecto TanStack Query reintenta tres veces cualquier error. Un
       * 403 o un 404 no van a cambiar por insistir: lo unico que se consigue
       * es que el usuario espere cuatro peticiones antes de ver el mensaje.
       * Y el 401 ya lo resuelve el interceptor renovando el token, asi que
       * reintentar aqui duplicaria el trabajo.
       */
      retry: (intentos, error) => {
        if (error instanceof ApiError && !error.esDeRed) {
          return false
        }
        return intentos < 2
      },
      staleTime: 30_000,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    {/* Por fuera de todo: un error al montar los proveedores tambien deja la
        pagina en blanco, y ese es justo el caso mas dificil de diagnosticar. */}
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  </React.StrictMode>,
)
