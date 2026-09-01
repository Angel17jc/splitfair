import { Link } from 'react-router-dom'
import { COLOR_DE_SALDO, describirSaldo, signoDeSaldo } from '../../utils/dinero'
import type { GroupSummary } from '../../types/api'

interface Props {
  grupo: GroupSummary
}

/** Un grupo en el listado, con el saldo propio ya resuelto por el backend. */
export default function GroupCard({ grupo }: Props) {
  const signo = signoDeSaldo(grupo.myBalance)

  return (
    <li className="rounded-xl border border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
      {/* El enlace envuelve toda la tarjeta: un area de pulsacion del tamano
          de lo que se ve, en vez de obligar a acertar en el titulo. */}
      <Link
        to={`/grupos/${grupo.id}`}
        className="block rounded-xl p-5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h3 className="truncate text-base font-medium text-slate-900">{grupo.name}</h3>
            {grupo.description && (
              <p className="mt-0.5 truncate text-sm text-slate-500">{grupo.description}</p>
            )}
          </div>
          {grupo.role === 'ADMIN' && (
            <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
              Admin
            </span>
          )}
        </div>

        <div className="mt-4 flex items-center justify-between gap-3 text-sm">
          <span className="text-slate-500">
            {grupo.memberCount} {grupo.memberCount === 1 ? 'miembro' : 'miembros'}
          </span>
          {/* El texto dice si debes o te deben; el color solo refuerza. Fiarlo
              al color dejaria fuera a quien no lo distingue. */}
          <span className={`font-medium ${COLOR_DE_SALDO[signo]}`}>
            {describirSaldo(grupo.myBalance, grupo.currency)}
          </span>
        </div>
      </Link>
    </li>
  )
}
