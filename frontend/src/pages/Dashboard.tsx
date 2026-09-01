import { useState } from 'react'
import Button from '../components/Button'
import EmptyState from '../components/EmptyState'
import ErrorState from '../components/ErrorState'
import Modal from '../components/Modal'
import Skeleton from '../components/Skeleton'
import GroupCard from '../features/groups/GroupCard'
import { useGrupos } from '../features/groups/hooks'
import { useAuth } from '../features/auth/useAuth'

export default function Dashboard() {
  const { usuario } = useAuth()
  const [pagina, setPagina] = useState(0)
  const [explicando, setExplicando] = useState(false)

  const { data, isPending, isError, error, isFetching, refetch } = useGrupos({
    page: pagina,
    size: 12,
  })

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">Mis grupos</h1>
        <p className="mt-1 text-sm text-slate-500">Hola, {usuario?.name}.</p>
      </div>

      {isPending ? (
        <ListaEsqueleto />
      ) : isError ? (
        <ErrorState error={error} contexto="tus grupos" onReintentar={() => refetch()} />
      ) : data.content.length === 0 ? (
        <EmptyState
          titulo="Todavia no tienes ningun grupo"
          descripcion="Un grupo es donde se registran los gastos compartidos y se calcula quien debe a quien."
          accion={
            <Button variante="secundario" onClick={() => setExplicando(true)}>
              Como funciona
            </Button>
          }
        />
      ) : (
        <>
          {/*
            aria-busy avisa de que el contenido se esta actualizando sin
            quitarlo de en medio: con keepPreviousData la lista anterior sigue
            visible mientras llega la siguiente pagina, y sin este atributo un
            lector de pantalla no tendria forma de saberlo.
          */}
          <ul aria-busy={isFetching} className="grid gap-4 sm:grid-cols-2">
            {data.content.map((grupo) => (
              <GroupCard key={grupo.id} grupo={grupo} />
            ))}
          </ul>

          {data.totalPages > 1 && (
            <nav
              aria-label="Paginacion de grupos"
              className="mt-6 flex items-center justify-center gap-3"
            >
              <Button
                variante="secundario"
                disabled={pagina === 0 || isFetching}
                onClick={() => setPagina((p) => Math.max(0, p - 1))}
              >
                Anterior
              </Button>
              <span className="text-sm text-slate-500" aria-live="polite">
                Pagina {data.page + 1} de {data.totalPages}
              </span>
              <Button
                variante="secundario"
                disabled={data.last || isFetching}
                onClick={() => setPagina((p) => p + 1)}
              >
                Siguiente
              </Button>
            </nav>
          )}
        </>
      )}

      <Modal
        abierto={explicando}
        onCerrar={() => setExplicando(false)}
        titulo="Como funciona SplitFair"
        pie={
          <Button variante="secundario" onClick={() => setExplicando(false)}>
            Entendido
          </Button>
        }
      >
        <p>
          Cada gasto se reparte entre los miembros del grupo, a partes iguales o como
          decidas. SplitFair lleva la cuenta de quien ha adelantado dinero y quien debe, y
          propone el minimo de pagos necesarios para dejar el grupo a cero.
        </p>
      </Modal>
    </>
  )
}

/** Reserva el sitio de las tarjetas para que la pagina no salte al cargar. */
function ListaEsqueleto() {
  return (
    <ul aria-busy="true" className="grid gap-4 sm:grid-cols-2">
      {[0, 1, 2, 3].map((i) => (
        <li key={i} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <Skeleton className="h-5 w-2/5" />
          <Skeleton className="mt-2 h-4 w-3/5" />
          <div className="mt-6 flex justify-between">
            <Skeleton className="h-4 w-20" />
            <Skeleton className="h-4 w-28" />
          </div>
        </li>
      ))}
    </ul>
  )
}
