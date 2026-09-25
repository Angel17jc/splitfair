import { useId, useState } from 'react'
import Button from '../../components/Button'
import Input from '../../components/Input'
import Modal from '../../components/Modal'
import Select from '../../components/Select'
import { ApiError } from '../../api/errors'
import { formatearImporte, parsearImporte } from '../../utils/dinero'
import { useRegistrarPago } from './hooks'
import type { GroupMember, SettlementSuggestion } from '../../types/api'

interface Props {
  groupId: number
  moneda: string
  miembros: GroupMember[]
  miId: number | undefined
  /** Para sugerir el importe cuando el algoritmo ya propone pagar a esa persona. */
  sugerencias: SettlementSuggestion[]
  onCerrar: () => void
}

/**
 * Registra un pago que no sale de una sugerencia.
 *
 * Hasta ahora solo se podia registrar el pago exacto que propone el algoritmo,
 * y eso deja fuera lo que de verdad pasa entre personas: pagar a plazos, pagar
 * de mas, o saldar con quien te viene bien en vez de con quien dice la
 * sugerencia. El backend nunca lo impidio —acepta cualquier miembro y
 * cualquier importe positivo—, era la interfaz la que solo ofrecia el camino
 * estrecho.
 *
 * No se limita el importe a lo que se debe, y es deliberado: pagar de mas
 * ocurre —se redondea al alza, se adelanta lo del mes que viene— y el saldo
 * resultante lo refleja sin problema. Impedirlo obligaria a hacerlo por fuera
 * de la aplicacion, que es peor: entonces el dinero se mueve y las cuentas no
 * se enteran.
 */
export default function RegistrarPagoModal({
  groupId,
  moneda,
  miembros,
  miId,
  sugerencias,
  onCerrar,
}: Props) {
  const registrar = useRegistrarPago(groupId)
  const idFormulario = useId()

  // Pagarse a uno mismo no significa nada, asi que no se ofrece.
  const destinatarios = miembros.filter((m) => m.userId !== miId)

  const [paraQuien, setParaQuien] = useState('')
  const [importe, setImporte] = useState('')
  const [errores, setErrores] = useState<{ paraQuien?: string; importe?: string }>({})

  const sugerido = sugerencias.find(
    (s) => s.fromUserId === miId && String(s.toUserId) === paraQuien,
  )

  const enviar = async (evento: React.FormEvent) => {
    evento.preventDefault()

    // parsearImporte ya devuelve null para el cero, los negativos y cualquier
    // cosa con mas de dos decimales, asi que no hace falta comprobarlo otra
    // vez aqui. Se anadio un `cantidad <= 0` de mas y lo destapo una mutacion:
    // quitarlo no rompia ningun test, porque nunca llegaba a ejecutarse. Una
    // guarda que no guarda nada engana a quien lee el codigo, haciendole creer
    // que parsearImporte puede devolver cero.
    const cantidad = parsearImporte(importe)
    const fallos: typeof errores = {}
    if (!paraQuien) fallos.paraQuien = 'Indica a quien le pagas'
    if (cantidad === null) fallos.importe = 'Indica un importe mayor que cero'

    setErrores(fallos)
    if (Object.keys(fallos).length > 0 || cantidad === null) return

    try {
      await registrar.mutateAsync({ paidTo: Number(paraQuien), amount: cantidad })
      onCerrar()
    } catch {
      // El error se pinta abajo desde el estado de la mutacion; el modal se
      // queda abierto para no perder lo escrito.
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Registrar un pago"
      pie={
        <>
          <Button variante="secundario" onClick={onCerrar} disabled={registrar.isPending}>
            Cancelar
          </Button>
          <Button type="submit" form={idFormulario} cargando={registrar.isPending}>
            {registrar.isPending ? 'Registrando...' : 'Registrar'}
          </Button>
        </>
      }
    >
      <form id={idFormulario} onSubmit={enviar} className="space-y-4" noValidate>
        <p className="text-sm text-slate-600">
          Anota un pago que ya hiciste. Queda <strong>pendiente</strong> hasta que quien lo
          recibe lo confirme, asi que los saldos no cambian hasta entonces.
        </p>

        <Select
          etiqueta="A quien le pagas"
          value={paraQuien}
          onChange={(e) => setParaQuien(e.target.value)}
          error={errores.paraQuien}
        >
          <option value="">Elige a alguien</option>
          {destinatarios.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.name}
            </option>
          ))}
        </Select>

        <Input
          etiqueta="Importe"
          inputMode="decimal"
          placeholder="0,00"
          value={importe}
          onChange={(e) => setImporte(e.target.value)}
          error={errores.importe}
          ayuda={
            sugerido
              ? `La sugerencia para saldar con esta persona es ${formatearImporte(
                  sugerido.amount,
                  moneda,
                )}. Puedes pagar otra cantidad.`
              : 'Puede ser un pago parcial.'
          }
        />

        {registrar.isError && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {registrar.error instanceof ApiError
              ? registrar.error.message
              : 'No se pudo registrar el pago.'}
          </p>
        )}
      </form>
    </Modal>
  )
}
