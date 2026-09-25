import { useState } from 'react'
import Button from '../../components/Button'
import Modal from '../../components/Modal'
import { ApiError } from '../../api/errors'
import { formatearFecha, formatearImporte } from '../../utils/dinero'
import { useBorrarGasto } from './hooks'
import type { Expense } from '../../types/api'

interface Props {
  gasto: Expense
  groupId: number
  moneda: string
  onCerrar: () => void
}

/**
 * Confirmacion antes de borrar un gasto.
 *
 * Se pide confirmacion porque **no hay deshacer**: borrar un gasto cambia los
 * balances de todo el grupo, y reponerlo exige recordar el importe, la fecha y
 * el reparto exactos.
 *
 * El dialogo repite descripcion, importe y fecha en vez de preguntar "¿seguro?"
 * a secas. Con una lista de diez gastos parecidos —tres "Supermercado" del
 * mismo mes—, la pregunta sin datos no permite comprobar que se va a borrar el
 * que se pretende.
 */
export default function BorrarGastoModal({ gasto, groupId, moneda, onCerrar }: Props) {
  const borrar = useBorrarGasto(groupId)
  const [error, setError] = useState<string | null>(null)

  const confirmar = async () => {
    setError(null)
    try {
      await borrar.mutateAsync(gasto.id)
      onCerrar()
    } catch (fallo) {
      // El modal se queda abierto: cerrarlo dejaria el gasto en la lista sin
      // ninguna explicacion, y el usuario creeria que el borrado funciono.
      setError(
        fallo instanceof ApiError ? fallo.message : 'No se pudo borrar el gasto.',
      )
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Borrar gasto"
      pie={
        <>
          <Button variante="secundario" onClick={onCerrar} disabled={borrar.isPending}>
            Cancelar
          </Button>
          <Button variante="peligro" onClick={confirmar} cargando={borrar.isPending}>
            {borrar.isPending ? 'Borrando...' : 'Borrar'}
          </Button>
        </>
      }
    >
      <p>Se va a borrar este gasto y los balances del grupo se recalcularan.</p>

      <div className="mt-3 rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
        <p className="text-sm font-medium text-slate-900">{gasto.description}</p>
        <p className="mt-0.5 text-xs text-slate-500">
          {formatearImporte(gasto.amount, moneda)} · {formatearFecha(gasto.expenseDate)} · Pago{' '}
          {gasto.paidByName}
        </p>
      </div>

      <p className="mt-3 text-sm text-slate-500">Esta accion no se puede deshacer.</p>

      {error && (
        <p role="alert" className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}
    </Modal>
  )
}
