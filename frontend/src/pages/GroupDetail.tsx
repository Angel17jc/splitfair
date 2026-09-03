import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Card from '../components/Card'
import ErrorState from '../components/ErrorState'
import Skeleton from '../components/Skeleton'
import AnalyticsPanel from '../features/analytics/AnalyticsPanel'
import MembersBalances from '../features/balances/MembersBalances'
import MyBalanceSummary from '../features/balances/MyBalanceSummary'
import SettlementsPanel from '../features/balances/SettlementsPanel'
import { useBalances } from '../features/balances/hooks'
import CreateExpenseModal from '../features/expenses/CreateExpenseModal'
import ExpenseList from '../features/expenses/ExpenseList'
import InviteModal from '../features/groups/InviteModal'
import { useGrupo } from '../features/groups/hooks'
import { useAuth } from '../features/auth/useAuth'

export default function GroupDetail() {
  const { groupId } = useParams()
  const { usuario } = useAuth()
  const [invitando, setInvitando] = useState(false)
  const [anadiendoGasto, setAnadiendoGasto] = useState(false)

  const id = Number(groupId)
  const { data: grupo, isPending, isError, error, refetch } = useGrupo(id)
  const balances = useBalances(id)

  if (isPending) {
    return <DetalleEsqueleto />
  }

  if (isError) {
    return (
      <>
        <Migas />
        <ErrorState error={error} contexto="este grupo" onReintentar={() => refetch()} />
      </>
    )
  }

  const soyAdministrador =
    grupo.members.find((m) => m.userId === usuario?.userId)?.role === 'ADMIN'

  const miSaldo = balances.data?.balances.find((b) => b.userId === usuario?.userId)

  return (
    <>
      <Migas />

      <header className="mb-6">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-slate-900">{grupo.name}</h1>
          <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
            {grupo.currency}
          </span>
        </div>
        {grupo.description && (
          <p className="mt-1 text-sm text-slate-500">{grupo.description}</p>
        )}
        <p className="mt-1 text-xs text-slate-400">Creado por {grupo.createdByName}</p>
      </header>

      {/*
        El saldo propio va arriba y a todo lo ancho: es el dato por el que se
        abre la aplicacion. Mientras carga no se reserva sitio con un esqueleto
        porque aparecer empujando el contenido una sola vez molesta menos que
        una franja gris permanente en la parte mas visible de la pagina.
      */}
      {balances.data && (
        <MyBalanceSummary
          mio={miSaldo}
          moneda={balances.data.currency}
          totalDelGrupo={balances.data.totalSpent}
        />
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <ExpenseList
            groupId={grupo.id}
            moneda={grupo.currency}
            miembros={grupo.members}
            onAnadir={() => setAnadiendoGasto(true)}
          />
        </div>

        {/*
          Balances y liquidaciones van juntos y a la derecha: el primero dice
          como esta el grupo y el segundo, que hacer al respecto. Separarlos
          obligaria a mirar arriba y abajo para responder a la misma pregunta.
        */}
        <div className="space-y-6 lg:self-start">
          <MembersBalances
            balances={balances.data?.balances}
            miembros={grupo.members}
            moneda={grupo.currency}
            miId={usuario?.userId}
            cargando={balances.isPending}
            error={balances.isError ? balances.error : null}
            onReintentar={() => balances.refetch()}
            onInvitar={soyAdministrador ? () => setInvitando(true) : undefined}
          />

          <SettlementsPanel
            groupId={grupo.id}
            moneda={grupo.currency}
            miId={usuario?.userId}
            soyAdministrador={soyAdministrador}
          />

          {/* La analitica va la ultima: es contexto, no accion. Quien entra
              quiere saber cuanto debe y a quien pagar antes que en que se ha
              ido el dinero. */}
          <AnalyticsPanel groupId={grupo.id} />
        </div>
      </div>

      <CreateExpenseModal
        abierto={anadiendoGasto}
        onCerrar={() => setAnadiendoGasto(false)}
        groupId={grupo.id}
        miembros={grupo.members}
      />

      <InviteModal
        abierto={invitando}
        onCerrar={() => setInvitando(false)}
        groupId={grupo.id}
        nombreDelGrupo={grupo.name}
      />

    </>
  )
}

function Migas() {
  return (
    <nav aria-label="Miga de pan" className="mb-4">
      <Link
        to="/dashboard"
        className="text-sm text-slate-500 underline underline-offset-2 hover:text-slate-800"
      >
        &larr; Mis grupos
      </Link>
    </nav>
  )
}

function DetalleEsqueleto() {
  return (
    <div aria-busy="true">
      <Skeleton className="h-4 w-24" />
      <Skeleton className="mt-6 h-8 w-1/3" />
      <Skeleton className="mt-2 h-4 w-1/2" />
      <Card className="mt-8">
        <Skeleton className="h-5 w-28" />
        {[0, 1, 2].map((i) => (
          <div key={i} className="mt-4 flex items-center gap-3">
            <Skeleton className="h-8 w-8 rounded-full" />
            <div className="flex-1">
              <Skeleton className="h-4 w-1/4" />
              <Skeleton className="mt-1 h-3 w-1/3" />
            </div>
          </div>
        ))}
      </Card>
    </div>
  )
}
