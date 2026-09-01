import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useLocation } from 'react-router-dom'
import Button from '../components/Button'
import Card from '../components/Card'
import Input from '../components/Input'
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
      <Card className="w-full max-w-sm">
        <h1 className="text-2xl font-semibold text-slate-900">Iniciar sesion</h1>
        <p className="mt-1 text-sm text-slate-500">Entra para ver tus grupos y gastos.</p>

        {/* noValidate deja la validacion a Zod: los mensajes nativos del
            navegador cambian de idioma y de forma segun el navegador, y no se
            pueden alinear con el resto de la interfaz. */}
        <form onSubmit={enviar} className="mt-6 space-y-4" noValidate>
          {errors.root && (
            <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {errors.root.message}
            </p>
          )}

          <Input
            id="email"
            etiqueta="Email"
            type="email"
            autoComplete="email"
            autoFocus
            error={errors.email?.message}
            {...register('email')}
          />

          <Input
            id="password"
            etiqueta="Contrasena"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />

          <Button type="submit" ancho cargando={isSubmitting}>
            {isSubmitting ? 'Entrando...' : 'Entrar'}
          </Button>
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
      </Card>
    </div>
  )
}
