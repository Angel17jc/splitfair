import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'
import { esquemaDeAcceso, type DatosDeAcceso } from '../features/auth/schemas'
import { aplicarErrorDeApi } from '../utils/formularios'

const CAMPOS = ['email', 'password'] as const

export default function Login() {
  const { entrar } = useAuth()
  const ubicacion = useLocation()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DatosDeAcceso>({
    resolver: zodResolver(esquemaDeAcceso),
    defaultValues: { email: '', password: '' },
  })

  /**
   * Al entrar no se navega desde aqui.
   *
   * El estado pasa a autenticado y AnonymousRoute, que envuelve esta ruta,
   * redirige al destino que se guardo al expulsar al usuario, o al dashboard.
   * Navegar tambien desde el formulario duplicaria esa decision en dos sitios
   * que tendrian que mantenerse de acuerdo.
   */
  const enviar = handleSubmit(async (datos) => {
    try {
      await entrar(datos)
    } catch (error) {
      aplicarErrorDeApi(error, setError, CAMPOS)
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-semibold text-slate-900">Iniciar sesion</h1>
        <p className="mt-1 text-sm text-slate-500">Entra para ver tus grupos y gastos.</p>

        {/* noValidate deja la validacion a Zod: los mensajes nativos del
            navegador cambian de idioma y de forma segun el navegador, y no se
            pueden alinear con el resto de la interfaz. */}
        <form onSubmit={enviar} className="mt-6 space-y-4" noValidate>
          {errors.root && (
            <p
              role="alert"
              className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700"
            >
              {errors.root.message}
            </p>
          )}

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-slate-700">
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              autoFocus
              aria-invalid={errors.email ? 'true' : undefined}
              aria-describedby={errors.email ? 'error-email' : undefined}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
              {...register('email')}
            />
            {errors.email && (
              <p id="error-email" role="alert" className="mt-1 text-sm text-red-600">
                {errors.email.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-slate-700">
              Contrasena
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={errors.password ? 'true' : undefined}
              aria-describedby={errors.password ? 'error-password' : undefined}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
              {...register('password')}
            />
            {errors.password && (
              <p id="error-password" role="alert" className="mt-1 text-sm text-red-600">
                {errors.password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:bg-slate-400"
          >
            {isSubmitting ? 'Entrando...' : 'Entrar'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          No tienes cuenta?{' '}
          {/* Se arrastra el state para no perder el destino al cambiar de
              formulario: quien venia de /grupos/7 debe acabar alli tanto si
              entra como si se registra. */}
          <Link
            to="/register"
            state={ubicacion.state}
            className="font-medium text-slate-900 underline underline-offset-2"
          >
            Crear una
          </Link>
        </p>
      </div>
    </div>
  )
}
