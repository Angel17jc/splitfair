import { useState } from 'react'
import Button from '../../components/Button'
import Card from '../../components/Card'
import Modal from '../../components/Modal'
import Skeleton from '../../components/Skeleton'
import { ApiError } from '../../api/errors'
import { formatearImporte, formatearInstante } from '../../utils/dinero'
import {
  useCancelarPago,
  useConfirmarPago,
  useHistorial,
  useRegistrarPago,
  useSugerencias,
} from './hooks'
import type { Settlement, SettlementSuggestion } from '../../types/api'

interface Props {
  groupId: number
  moneda: string
  miId: number | undefined
  soyAdministrador: boolean
}

export default function SettlementsPanel({ groupId, moneda, miId, soyAdministrador }: Props) {
  const sugerencias = useSugerencias(groupId)
  const historial = useHistorial(groupId)
  const registrar = useRegistrarPago(groupId)
  const confirmar = useConfirmarPago(groupId)
  const cancelar = useCancelarPago(groupId)
  const [explicando, setExplicando] = useState(false)

  const pagos = historial.data?.content ?? []
  const cargando = sugerencias.isPending || historial.isPending
  const fallo = registrar.error ?? confirmar.error ?? cancelar.error

  return (
    <Card como="section" className="lg:self-start">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-medium text-slate-900">Liquidaciones</h2>
        <Button variante="texto" onClick={() => setExplicando(true)}>
          Como se calculan
        </Button>
      </div>

      {fallo && (
        <p role="alert" className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {fallo instanceof ApiError ? fallo.message : 'No se pudo completar la operacion.'}
        </p>
      )}

      {cargando ? (
        <div aria-busy="true" className="mt-4 space-y-3">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      ) : (
        <>
          <Sugerencias
            sugerencias={sugerencias.data ?? []}
            moneda={moneda}
            miId={miId}
            registrando={registrar.isPending}
            onRegistrar={(s) => registrar.mutate({ paidTo: s.toUserId, amount: s.amount })}
          />

          {pagos.length > 0 && (
            <div className="mt-6 border-t border-slate-100 pt-4">
              <h3 className="text-sm font-medium text-slate-700">Pagos registrados</h3>
              <ul className="mt-2 divide-y divide-slate-100">
                {pagos.map((pago) => (
                  <PagoRegistrado
                    key={pago.id}
                    pago={pago}
                    moneda={moneda}
                    miId={miId}
                    soyAdministrador={soyAdministrador}
                    ocupado={confirmar.isPending || cancelar.isPending}
                    onConfirmar={() => confirmar.mutate(pago.id)}
                    onCancelar={() => cancelar.mutate(pago.id)}
                  />
                ))}
              </ul>
            </div>
          )}
        </>
      )}

      <Modal
        abierto={explicando}
        onCerrar={() => setExplicando(false)}
        titulo="Como se calculan las liquidaciones"
        pie={
          <Button variante="secundario" onClick={() => setExplicando(false)}>
            Entendido
          </Button>
        }
      >
        <p>
          Si cada persona pagara a cada una de las demas lo que le debe, un grupo de cinco
          necesitaria hasta diez transferencias. SplitFair agrupa las deudas y propone en su
          lugar una lista mas corta: <strong>como mucho una transferencia menos que personas
          hay en el grupo</strong>.
        </p>
        <p className="mt-3">
          El resultado es el mismo: aplicando esos pagos, todos los saldos quedan a cero. Lo
          que cambia es cuantas veces hay que mover dinero.
        </p>
        <p className="mt-3">
          Las sugerencias se recalculan solas con cada gasto y cada pago confirmado, asi que no
          hace falta seguirlas al pie de la letra: si alguien paga otra cantidad, la propuesta
          se ajusta.
        </p>
      </Modal>
    </Card>
  )
}

/**
 * Los pagos que dejarian el grupo a cero.
 *
 * El boton de registrar solo aparece en la fila donde **el usuario es quien
 * paga**: registrar el pago de otro seria afirmar que ha entregado dinero, y
 * eso solo lo puede decir quien lo entrega.
 */
function Sugerencias({
  sugerencias,
  moneda,
  miId,
  registrando,
  onRegistrar,
}: {
  sugerencias: SettlementSuggestion[]
  moneda: string
  miId: number | undefined
  registrando: boolean
  onRegistrar: (s: SettlementSuggestion) => void
}) {
  if (sugerencias.length === 0) {
    return (
      <p className="mt-4 rounded-md bg-emerald-50 px-3 py-3 text-sm text-emerald-800">
        El grupo esta saldado. No hace falta ningun pago.
      </p>
    )
  }

  return (
    <>
      <p className="mt-3 text-xs text-slate-500">
        {sugerencias.length === 1
          ? 'Un pago deja el grupo a cero:'
          : `${sugerencias.length} pagos dejan el grupo a cero:`}
      </p>

      <ul className="mt-2 space-y-2">
        {sugerencias.map((s) => {
          const pagoYo = s.fromUserId === miId

          return (
            <li
              key={`${s.fromUserId}-${s.toUserId}`}
              className="rounded-md border border-slate-200 px-3 py-2.5"
            >
              <p className="text-sm text-slate-700">
                <span className="font-medium">{pagoYo ? 'Tu' : s.fromUserName}</span> paga{' '}
                <span className="font-medium tabular-nums">
                  {formatearImporte(s.amount, moneda)}
                </span>{' '}
                a <span className="font-medium">{s.toUserName}</span>
              </p>

              {pagoYo && (
                <div className="mt-2">
                  <Button
                    variante="secundario"
                    cargando={registrando}
                    onClick={() => onRegistrar(s)}
                  >
                    Ya lo he pagado
                  </Button>
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}

/**
 * Un pago ya registrado.
 *
 * Solo quien cobra ve el boton de confirmar, porque solo el puede: si pudiera
 * confirmarlo quien paga, la confirmacion no anadiria nada sobre el registro
 * inicial. Y solo se puede cancelar mientras esta pendiente: una liquidacion
 * confirmada es un hecho contable, dinero que cambio de manos, y para
 * corregirla se registra el pago inverso.
 */
function PagoRegistrado({
  pago,
  moneda,
  miId,
  soyAdministrador,
  ocupado,
  onConfirmar,
  onCancelar,
}: {
  pago: Settlement
  moneda: string
  miId: number | undefined
  soyAdministrador: boolean
  ocupado: boolean
  onConfirmar: () => void
  onCancelar: () => void
}) {
  const pendiente = pago.status === 'PENDING'
  const yoCobro = pago.paidToUserId === miId
  const yoPago = pago.paidByUserId === miId

  return (
    <li className="py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-slate-700">
            <span className="font-medium">{yoPago ? 'Tu' : pago.paidByName}</span>
            {' → '}
            <span className="font-medium">{yoCobro ? 'ti' : pago.paidToName}</span>
          </p>
          <p className="mt-0.5 text-xs text-slate-400">{formatearInstante(pago.createdAt)}</p>
        </div>

        <div className="shrink-0 text-right">
          <p className="text-sm font-medium tabular-nums text-slate-900">
            {formatearImporte(pago.amount, moneda)}
          </p>
          <span
            className={`mt-0.5 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
              pendiente ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'
            }`}
          >
            {pendiente ? 'Pendiente' : 'Confirmado'}
          </span>
        </div>
      </div>

      {pendiente && (
        <div className="mt-2 flex flex-wrap gap-2">
          {yoCobro && (
            <Button variante="secundario" disabled={ocupado} onClick={onConfirmar}>
              Confirmar que lo recibi
            </Button>
          )}
          {(yoPago || soyAdministrador) && (
            <Button variante="texto" disabled={ocupado} onClick={onCancelar}>
              Cancelar
            </Button>
          )}
        </div>
      )}

      {pendiente && (
        <p className="mt-1.5 text-xs text-slate-400">
          {/* Se dice explicitamente: si no, el usuario ve el pago anotado y no
              entiende por que su saldo no se ha movido. */}
          Todavia no cuenta en los saldos: falta que {yoCobro ? 'lo confirmes' : 'lo confirme ' + pago.paidToName}.
        </p>
      )}
    </li>
  )
}
