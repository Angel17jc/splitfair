import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import Button from '../components/Button'
import Card from '../components/Card'
import ErrorState from '../components/ErrorState'
import Skeleton from '../components/Skeleton'
import { verInvitacion } from '../api/invitations'
import { ApiError } from '../api/errors'
import { useAuth } from '../features/auth/useAuth'
import { useAceptarInvitacion } from '../features/groups/mutaciones'

/**
 * Pantalla publica de una invitacion.
 *
 * Es **publica a proposito**: quien abre el link puede no tener cuenta
 * todavia, y obligarle a registrarse antes de ver a que grupo le invitan es
 * pedirle que confie a ciegas.
 */
export default function InvitationLanding() {
  const { token = '' } = useParams()
  const { estado } = useAuth()
  const ubicacion = useLocation()
  const navegar = useNavigate()
  const aceptar = useAceptarInvitacion()

  const {
    data: invitacion,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['invitacion', token],
    queryFn: () => verInvitacion(token),
    // No se reintenta: un token que no existe no va a aparecer.
    retry: false,
  })

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <Card className="w-full max-w-md text-center">
        {isPending ? (
          <div aria-busy="true" className="flex flex-col items-center gap-3">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-7 w-52" />
            <Skeleton className="mt-3 h-10 w-40" />
          </div>
        ) : isError ? (
          <ErrorState
            error={error}
            contexto="la invitacion"
            onReintentar={
              error instanceof ApiError && error.status === 404 ? undefined : () => refetch()
            }
          />
        ) : !invitacion.valid ? (
          /*
            valid:false llega con 200, no con 404: el link existio, solo que ya
            no sirve. Se distingue del token inventado porque el mensaje util
            es distinto — aqui hay alguien que si fue invitado y necesita que
            le manden otro link, no que le digan que se equivoco de direccion.
          */
          <>
            <h1 className="text-xl font-medium text-slate-900">Esta invitacion ya no vale</h1>
            <p className="mt-2 text-sm text-slate-500">
              Los links son de un solo uso y caducan a los 7 dias. Pide a quien te invito
              que te genere uno nuevo.
            </p>
            <div className="mt-6">
              <Link
                to="/login"
                className="text-sm font-medium text-slate-900 underline underline-offset-2"
              >
                Ir a SplitFair
              </Link>
            </div>
          </>
        ) : (
          <>
            <p className="text-sm text-slate-500">
              {invitacion.invitedByName} te invita a unirte a
            </p>
            <h1 className="mt-1 text-2xl font-semibold text-slate-900">
              {invitacion.groupName}
            </h1>

            {estado === 'autenticado' ? (
              <>
                {aceptar.isError && (
                  <p
                    role="alert"
                    className="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700"
                  >
                    {aceptar.error instanceof ApiError
                      ? aceptar.error.message
                      : 'No se pudo aceptar la invitacion.'}
                  </p>
                )}
                <div className="mt-6">
                  <Button
                    cargando={aceptar.isPending}
                    onClick={() =>
                      aceptar.mutate(token, {
                        onSuccess: (grupo) => navegar(`/grupos/${grupo.id}`, { replace: true }),
                      })
                    }
                  >
                    {aceptar.isPending ? 'Uniendote...' : 'Unirme al grupo'}
                  </Button>
                </div>
              </>
            ) : (
              <>
                <p className="mt-4 text-sm text-slate-500">
                  Crea una cuenta para unirte. Solo te llevara un momento.
                </p>
                <div className="mt-6 flex flex-col items-center gap-3">
                  {/*
                    El token viaja en la URL del registro para que la cuenta se
                    cree y se entre al grupo en una sola peticion. Con dos, un
                    fallo entre ambas deja al usuario registrado y fuera.
                  */}
                  <Link
                    to={`/register?invitation=${encodeURIComponent(token)}`}
                    className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
                  >
                    Crear cuenta y unirme
                  </Link>
                  {/*
                    Quien ya tiene cuenta vuelve aqui despues de entrar: se
                    guarda esta pagina como destino, igual que hace la
                    proteccion de rutas. Asi no pierde la invitacion por el
                    camino.
                  */}
                  <Link
                    to="/login"
                    state={{ from: ubicacion }}
                    className="text-sm text-slate-600 underline underline-offset-2"
                  >
                    Ya tengo cuenta
                  </Link>
                </div>
              </>
            )}

            <p className="mt-6 text-xs text-slate-400">
              Este link es de un solo uso.
            </p>
          </>
        )}
      </Card>
    </div>
  )
}
