import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'
import { esquemaDeRegistro, type DatosDeRegistro } from '../features/auth/schemas'
import { aplicarErrorDeApi } from '../utils/formularios'

const CAMPOS = ['name', 'email', 'password'] as const

export default function Register() {
  const { registrarse } = useAuth()
  const ubicacion = useLocation()
  const [parametros] = useSearchParams()

  /**
   * Token de invitacion, si se llego desde un link.
   *
   * Se envia junto con el alta en una sola peticion. No es comodidad: con dos
   * llamadas separadas, un fallo entre ambas dejaria al usuario registrado
   * pero fuera del grupo al que le invitaron, y con la invitacion gastada o
   * sin gastar segun el orden.
   *
   * La pantalla que muestra a que grupo te invitan llega en la fase de
   * grupos; aqui basta con no perder el token por el camino.
   */
  const invitationToken = parametros.get('invitation') ?? undefined

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DatosDeRegistro>({
    resolver: zodResolver(esquemaDeRegistro),
    defaultValues: { name: '', email: '', password: '' },
  })

  const enviar = handleSubmit(async (datos) => {
    try {
      await registrarse({ ...datos, invitationToken })
    } catch (error) {
      aplicarErrorDeApi(error, setError, CAMPOS)
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-semibold text-slate-900">Crear cuenta</h1>
        <p className="mt-1 text-sm text-slate-500">
          {invitationToken
            ? 'Completa tus datos para unirte al grupo.'
            : 'Empieza a repartir gastos con tu grupo.'}
        </p>

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
            <label htmlFor="name" className="block text-sm font-medium text-slate-700">
              Nombre
            </label>
            <input
              id="name"
              type="text"
              autoComplete="name"
              autoFocus
              aria-invalid={errors.name ? 'true' : undefined}
              aria-describedby={errors.name ? 'error-name' : undefined}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
              {...register('name')}
            />
            {errors.name && (
              <p id="error-name" role="alert" className="mt-1 text-sm text-red-600">
                {errors.name.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-slate-700">
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
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
              /* new-password, no current-password: le dice al gestor de
                 contrasenas que ofrezca generar una en vez de rellenar la que
                 ya tuviera guardada para este sitio. */
              autoComplete="new-password"
              aria-invalid={errors.password ? 'true' : undefined}
              aria-describedby={errors.password ? 'error-password' : 'ayuda-password'}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
              {...register('password')}
            />
            {errors.password ? (
              <p id="error-password" role="alert" className="mt-1 text-sm text-red-600">
                {errors.password.message}
              </p>
            ) : (
              <p id="ayuda-password" className="mt-1 text-xs text-slate-500">
                Al menos 8 caracteres.
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:bg-slate-400"
          >
            {isSubmitting ? 'Creando cuenta...' : 'Crear cuenta'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Ya tienes cuenta?{' '}
          <Link
            to="/login"
            state={ubicacion.state}
            className="font-medium text-slate-900 underline underline-offset-2"
          >
            Iniciar sesion
          </Link>
        </p>
      </div>
    </div>
  )
}
