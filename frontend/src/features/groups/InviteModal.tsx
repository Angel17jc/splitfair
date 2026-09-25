import { useState } from 'react'
import Button from '../../components/Button'
import Modal from '../../components/Modal'
import { ApiError } from '../../api/errors'
import { useCrearInvitacion } from './mutaciones'

interface Props {
  abierto: boolean
  onCerrar: () => void
  groupId: number
  nombreDelGrupo: string
}

/**
 * Genera un link de invitacion y lo pone a mano para compartir.
 *
 * El link es de **un solo uso**: para invitar a tres personas se generan tres.
 * Se dice en pantalla porque lo natural es suponer lo contrario y reenviar el
 * mismo a todo el mundo, con lo que solo entraria el primero.
 */
export default function InviteModal({ abierto, onCerrar, groupId, nombreDelGrupo }: Props) {
  const crear = useCrearInvitacion(groupId)
  const [copiado, setCopiado] = useState(false)

  const url = crear.data ? enlaceDeInvitacion(crear.data.token) : null

  /**
   * Cerrar limpia el link generado y el aviso de copiado.
   *
   * Se hace aqui, en el manejador, y no en un efecto sobre `abierto`. Un
   * efecto que llama a setState provoca un render en cascada: React pinta,
   * descubre que el estado cambio y vuelve a pintar. Ajustar el estado en
   * respuesta a una accion del usuario es precisamente para lo que existen los
   * manejadores.
   *
   * Cubre todas las formas de cerrar porque el `<dialog>` nativo emite su
   * evento `close` con el boton, con Escape y con el clic en el fondo, y
   * `Modal` lo reenvia por esta misma via.
   *
   * Limpiar importa: el link es de **un solo uso**. Si al reabrir siguiera el
   * anterior, lo natural seria volver a copiarlo y mandarlo a otra persona,
   * que se encontraria una invitacion ya gastada.
   */
  const cerrar = () => {
    crear.reset()
    setCopiado(false)
    onCerrar()
  }

  const copiar = async () => {
    if (!url) return
    try {
      await navigator.clipboard.writeText(url)
      setCopiado(true)
    } catch {
      // El portapapeles puede fallar por permisos o por contexto no seguro.
      // No se muestra un error: el link sigue visible y seleccionable, que es
      // lo que el usuario necesita. Un aviso de fallo solo generaria alarma
      // sobre algo que ya puede resolver a mano.
      setCopiado(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={cerrar}
      titulo={`Invitar a ${nombreDelGrupo}`}
      pie={
        <Button variante="secundario" onClick={cerrar}>
          Cerrar
        </Button>
      }
    >
      {!url ? (
        <>
          <p>
            Se generara un link para que alguien se una al grupo. Es de{' '}
            <strong>un solo uso</strong> y caduca en 7 dias: para invitar a varias
            personas, genera un link para cada una.
          </p>

          {crear.isError && (
            <p role="alert" className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {crear.error instanceof ApiError
                ? crear.error.message
                : 'No se pudo generar el link.'}
            </p>
          )}

          <div className="mt-5">
            <Button onClick={() => crear.mutate(undefined)} cargando={crear.isPending}>
              {crear.isPending ? 'Generando...' : 'Generar link'}
            </Button>
          </div>
        </>
      ) : (
        <>
          <p>Comparte este link con la persona que quieres invitar:</p>

          <div className="mt-3 flex gap-2">
            {/* readOnly y no disabled: un campo deshabilitado no se puede
                seleccionar, y seleccionar y copiar a mano es justo la salida
                cuando el portapapeles no esta disponible. */}
            <input
              readOnly
              value={url}
              aria-label="Link de invitacion"
              onFocus={(e) => e.currentTarget.select()}
              className="min-w-0 flex-1 rounded-md border border-slate-300 bg-slate-50 px-3 py-2 font-mono text-xs text-slate-700"
            />
            <Button variante="secundario" onClick={copiar}>
              {copiado ? 'Copiado' : 'Copiar'}
            </Button>
          </div>

          {/* aria-live para que el cambio a "Copiado" se anuncie: quien no ve
              el boton no tendria forma de saber que la accion funciono. */}
          <p aria-live="polite" className="mt-2 text-xs text-slate-500">
            {copiado ? 'Link copiado al portapapeles.' : 'Un solo uso. Caduca en 7 dias.'}
          </p>

          <div className="mt-5">
            <Button variante="secundario" onClick={() => crear.mutate(undefined)}>
              Generar otro
            </Button>
          </div>
        </>
      )}
    </Modal>
  )
}

/**
 * El backend devuelve tambien una `url`, pero construida con
 * `app.frontend-base-url`, que en despliegues distintos puede no coincidir con
 * el sitio desde el que se esta navegando. El token es el dato; la direccion
 * la sabe el propio cliente.
 */
function enlaceDeInvitacion(token: string) {
  return `${window.location.origin}/invitacion/${token}`
}
