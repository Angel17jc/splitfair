import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import Button from '../components/Button'
import Card from '../components/Card'
import Input from '../components/Input'
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
      <Card className="w-full max-w-sm">
        <h1 className="text-2xl font-semibold text-slate-900">Crear cuenta</h1>
        <p className="mt-1 text-sm text-slate-500">
          {invitationToken
            ? 'Completa tus datos para unirte al grupo.'
            : 'Empieza a repartir gastos con tu grupo.'}
        </p>

        <form onSubmit={enviar} className="mt-6 space-y-4" noValidate>
          {errors.root && (
            <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {errors.root.message}
            </p>
          )}

          <Input
            id="name"
            etiqueta="Nombre"
            type="text"
            autoComplete="name"
            autoFocus
            error={errors.name?.message}
            {...register('name')}
          />

          <Input
            id="email"
            etiqueta="Email"
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register('email')}
          />

          <Input
            id="password"
            etiqueta="Contrasena"
            type="password"
            /* new-password, no current-password: le dice al gestor de
               contrasenas que ofrezca generar una en vez de rellenar la que ya
               tuviera guardada para este sitio. */
            autoComplete="new-password"
            ayuda="Al menos 8 caracteres."
            error={errors.password?.message}
            {...register('password')}
          />

          <Button type="submit" ancho cargando={isSubmitting}>
            {isSubmitting ? 'Creando cuenta...' : 'Crear cuenta'}
          </Button>
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
      </Card>
    </div>
  )
}
