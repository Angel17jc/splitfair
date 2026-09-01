import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Button from '../components/Button'
import Card from '../components/Card'
import ErrorState from '../components/ErrorState'
import Skeleton from '../components/Skeleton'
import InviteModal from '../features/groups/InviteModal'
import { useGrupo } from '../features/groups/hooks'
import { useAuth } from '../features/auth/useAuth'

export default function GroupDetail() {
  const { groupId } = useParams()
  const { usuario } = useAuth()
  const [invitando, setInvitando] = useState(false)
  const { data: grupo, isPending, isError, error, refetch } = useGrupo(Number(groupId))

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

      <Card como="section">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-medium text-slate-900">
            Miembros{' '}
            <span className="font-normal text-slate-400">({grupo.members.length})</span>
          </h2>
          {/*
            El boton solo existe para administradores porque solo ellos pueden
            invitar. Mostrarlo a todos y dejar que el backend responda 403
            seria ensenar una puerta que no abre.
          */}
          {soyAdministrador && (
            <Button variante="secundario" onClick={() => setInvitando(true)}>
              Invitar
            </Button>
          )}
        </div>

        <ul className="mt-4 divide-y divide-slate-100">
          {grupo.members.map((miembro) => (
            <li key={miembro.userId} className="flex items-center gap-3 py-3">
              <span
                aria-hidden="true"
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-600"
              >
                {miembro.name.slice(0, 2).toUpperCase()}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900">
                  {miembro.name}
                  {/* Saber cual eres tu evita tener que compararse con el
                      email en una lista de cinco personas. */}
                  {miembro.userId === usuario?.userId && (
                    <span className="ml-1 font-normal text-slate-400">(tu)</span>
                  )}
                </p>
                <p className="truncate text-xs text-slate-500">{miembro.email}</p>
              </div>
              {miembro.role === 'ADMIN' && (
                <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                  Admin
                </span>
              )}
            </li>
          ))}
        </ul>
      </Card>

      <InviteModal
        abierto={invitando}
        onCerrar={() => setInvitando(false)}
        groupId={grupo.id}
        nombreDelGrupo={grupo.name}
      />

      {/* Gastos, balances y liquidaciones llegan en los commits siguientes de
          esta fase y en la Fase 7. */}
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
      <div className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
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
      </div>
    </div>
  )
}
