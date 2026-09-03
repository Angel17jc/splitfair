import Card from '../../components/Card'
import {
  COLOR_DE_SALDO,
  describirSaldo,
  formatearImporte,
  signoDeSaldo,
} from '../../utils/dinero'
import type { Balance } from '../../types/api'

interface Props {
  mio: Balance | undefined
  moneda: string
  totalDelGrupo: number
}

/**
 * El saldo propio, con el desglose que lo explica.
 *
 * Es el dato por el que se abre la aplicacion, asi que va arriba y en grande.
 * El desglose no es decoracion: un saldo suelto invita a la discusion que esta
 * herramienta existe para evitar. Viendo "adelantaste 90, te correspondian 40"
 * el numero deja de ser una afirmacion y pasa a ser una cuenta.
 *
 * Ninguna de estas cifras se calcula aqui. Todas vienen del servidor,
 * incluido el neto: restarlas en el cliente daria el mismo resultado casi
 * siempre, y "casi siempre" no sirve cuando se habla de dinero.
 */
export default function MyBalanceSummary({ mio, moneda, totalDelGrupo }: Props) {
  if (!mio) return null

  const signo = signoDeSaldo(mio.netBalance)
  const hayLiquidaciones = mio.settlementsPaid > 0 || mio.settlementsReceived > 0

  return (
    <Card como="section" className="mb-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-sm font-medium text-slate-500">Tu saldo en este grupo</h2>
          {/* El texto dice el sentido; el color solo lo refuerza. */}
          <p className={`mt-1 text-2xl font-semibold ${COLOR_DE_SALDO[signo]}`}>
            {describirSaldo(mio.netBalance, moneda)}
          </p>
        </div>

        <div className="text-right">
          <h2 className="text-sm font-medium text-slate-500">Gasto total del grupo</h2>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
            {formatearImporte(totalDelGrupo, moneda)}
          </p>
          {/* Las liquidaciones no cuentan como gasto: saldar no es gastar. */}
          <p className="text-xs text-slate-400">Sin contar liquidaciones</p>
        </div>
      </div>

      <dl className="mt-5 grid gap-x-6 gap-y-3 border-t border-slate-100 pt-4 text-sm sm:grid-cols-2">
        <Linea etiqueta="Has adelantado" valor={formatearImporte(mio.totalPaid, moneda)} />
        <Linea etiqueta="Te correspondia" valor={formatearImporte(mio.totalOwed, moneda)} />

        {hayLiquidaciones && (
          <>
            <Linea
              etiqueta="Has pagado en liquidaciones"
              valor={formatearImporte(mio.settlementsPaid, moneda)}
            />
            <Linea
              etiqueta="Has cobrado en liquidaciones"
              valor={formatearImporte(mio.settlementsReceived, moneda)}
            />
          </>
        )}
      </dl>

      {hayLiquidaciones && (
        <p className="mt-3 text-xs text-slate-400">
          Solo cuentan las liquidaciones confirmadas por quien cobra.
        </p>
      )}
    </Card>
  )
}

function Linea({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{etiqueta}</dt>
      <dd className="font-medium tabular-nums text-slate-900">{valor}</dd>
    </div>
  )
}
