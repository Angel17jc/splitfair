import Button from '../../components/Button'
import Card from '../../components/Card'
import ErrorState from '../../components/ErrorState'
import Skeleton from '../../components/Skeleton'
import {
  COLOR_DE_SALDO,
  describirSaldo,
  formatearImporteAbsoluto,
  signoDeSaldo,
} from '../../utils/dinero'
import type { Balance, GroupMember } from '../../types/api'

interface Props {
  balances: Balance[] | undefined
  miembros: GroupMember[]
  moneda: string
  miId: number | undefined
  cargando: boolean
  error: unknown
  onReintentar: () => void
  onInvitar?: () => void
}

/**
 * Quien debe y a quien le deben, por miembro.
 *
 * Se ordena por saldo descendente: arriba quien tiene dinero por recuperar y
 * abajo quien lo debe. Alfabetico obligaria a recorrer la lista comparando
 * signos para hacerse una idea; asi la idea es la propia lista.
 *
 * Los datos vienen de dos sitios que hay que casar: el saldo lo da el endpoint
 * de balances y el rol, el del grupo. Se parte de **los balances** y no de los
 * miembros porque el backend construye los balances a partir de la lista de
 * miembros, asi que siempre estan todos; al reves habria que decidir que
 * mostrar para un miembro sin saldo calculado.
 */
export default function MembersBalances({
  balances,
  miembros,
  moneda,
  miId,
  cargando,
  error,
  onReintentar,
  onInvitar,
}: Props) {
  const rolDe = new Map(miembros.map((m) => [m.userId, m.role]))
  const ordenados = balances ? [...balances].sort((a, b) => b.netBalance - a.netBalance) : []

  return (
    <Card como="section" className="lg:self-start">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-medium text-slate-900">
          Balances{' '}
          {balances && <span className="font-normal text-slate-400">({balances.length})</span>}
        </h2>
        {/*
          El boton solo existe para administradores porque solo ellos pueden
          invitar. Mostrarlo a todos y dejar que el backend responda 403 seria
          ensenar una puerta que no abre.
        */}
        {onInvitar && (
          <Button variante="secundario" onClick={onInvitar}>
            Invitar
          </Button>
        )}
      </div>

      {cargando ? (
        <div aria-busy="true" className="mt-4 space-y-4">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-3">
              <Skeleton className="h-8 w-8 rounded-full" />
              <div className="flex-1">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="mt-1 h-3 w-1/2" />
              </div>
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="mt-4">
          <ErrorState error={error} contexto="los balances" onReintentar={onReintentar} />
        </div>
      ) : (
        <ul className="mt-4 divide-y divide-slate-100">
          {ordenados.map((balance) => {
            const signo = signoDeSaldo(balance.netBalance)

            return (
              <li key={balance.userId} className="flex items-center gap-3 py-3">
                <span
                  aria-hidden="true"
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-600"
                >
                  {balance.userName.slice(0, 2).toUpperCase()}
                </span>

                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-900">
                    {balance.userName}
                    {balance.userId === miId && (
                      <span className="ml-1 font-normal text-slate-400">(tu)</span>
                    )}
                    {rolDe.get(balance.userId) === 'ADMIN' && (
                      <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                        Admin
                      </span>
                    )}
                  </p>
                  <p className={`truncate text-xs ${COLOR_DE_SALDO[signo]}`}>
                    {describirParaOtro(balance, moneda, balance.userId === miId)}
                  </p>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}

/**
 * "Te deben 40,00 €" solo tiene sentido en primera persona. Para el resto se
 * dice quien debe y quien cobra, que es lo mismo visto desde fuera.
 */
function describirParaOtro(balance: Balance, moneda: string, esMio: boolean): string {
  if (esMio) {
    return describirSaldo(balance.netBalance, moneda)
  }

  switch (signoDeSaldo(balance.netBalance)) {
    case 'acreedor':
      return `Le deben ${formatearImporteAbsoluto(balance.netBalance, moneda)}`
    case 'deudor':
      return `Debe ${formatearImporteAbsoluto(balance.netBalance, moneda)}`
    default:
      return 'Esta al dia'
  }
}
