import { ApiError } from '../api/errors'
import Button from './Button'

interface Props {
  error: unknown
  onReintentar?: () => void
  /** Que se estaba intentando cargar, para dar contexto al mensaje. */
  contexto?: string
}

/**
 * Lo que se ve cuando una consulta falla.
 *
 * Distingue tres casos porque exigen tres reacciones distintas del usuario:
 * un fallo de red se reintenta, un 403 no se reintenta nunca (no cambiara), y
 * un error inesperado trae un identificador con el que se puede localizar la
 * causa en los registros del servidor.
 *
 * El boton de reintentar solo aparece cuando reintentar tiene sentido.
 * Ofrecerlo ante un 403 invita al usuario a pulsarlo diez veces contra algo
 * que nunca le va a dejar entrar.
 */
export default function ErrorState({ error, onReintentar, contexto }: Props) {
  const fallo = error instanceof ApiError ? error : null
  const sePuedeReintentar = !fallo || fallo.esDeRed || fallo.status >= 500

  return (
    <div
      role="alert"
      className="rounded-xl border border-red-200 bg-red-50 px-6 py-8 text-center"
    >
      <h2 className="text-base font-medium text-red-900">
        {contexto ? `No se pudo cargar ${contexto}` : 'No se pudo cargar'}
      </h2>
      <p className="mx-auto mt-1 max-w-md text-sm text-red-700">
        {fallo?.message ?? 'Ha ocurrido un error inesperado.'}
      </p>

      {fallo?.traceId && (
        // Es lo unico que conecta lo que vio el usuario con el stack trace del
        // servidor. Sin ensenarlo, un informe de error dice "no cargaba".
        <p className="mt-2 text-xs text-red-600">
          Referencia: <code>{fallo.traceId}</code>
        </p>
      )}

      {sePuedeReintentar && onReintentar && (
        <div className="mt-5">
          <Button variante="secundario" onClick={onReintentar}>
            Reintentar
          </Button>
        </div>
      )}
    </div>
  )
}
