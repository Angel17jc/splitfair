import { useEffect, useRef, useState } from 'react'
import Button from '../../components/Button'
import Card from '../../components/Card'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import Skeleton from '../../components/Skeleton'
import ExpenseFiltersBar from './ExpenseFilters'
import ExpenseItem from './ExpenseItem'
import { useGastos } from './hooks'
import type { ExpenseFilters, GroupMember } from '../../types/api'

interface Props {
  groupId: number
  moneda: string
  miembros: GroupMember[]
  onAnadir: () => void
}

export default function ExpenseList({ groupId, moneda, miembros, onAnadir }: Props) {
  const [filtros, setFiltros] = useState<ExpenseFilters>({})
  const {
    data,
    isPending,
    isError,
    error,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useGastos(groupId, filtros)

  const gastos = data?.pages.flatMap((pagina) => pagina.content) ?? []
  const total = data?.pages[0]?.totalElements ?? 0
  const hayFiltros = Object.values(filtros).some((v) => v !== undefined)

  return (
    <Card como="section">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-medium text-slate-900">
          Gastos{' '}
          {!isPending && !isError && (
            <span className="font-normal text-slate-400">({total})</span>
          )}
        </h2>
        <Button onClick={onAnadir}>Anadir gasto</Button>
      </div>

      <ExpenseFiltersBar filtros={filtros} onCambiar={setFiltros} miembros={miembros} />

      {isPending ? (
        <ListaEsqueleto />
      ) : isError ? (
        <ErrorState error={error} contexto="los gastos" onReintentar={() => refetch()} />
      ) : gastos.length === 0 ? (
        <EmptyState
          titulo={hayFiltros ? 'Ningun gasto con esos filtros' : 'Todavia no hay gastos'}
          descripcion={
            hayFiltros
              ? 'Prueba a ampliar el rango de fechas o a quitar la categoria.'
              : 'Anota el primero y SplitFair calculara quien debe a quien.'
          }
          accion={
            hayFiltros ? (
              <Button variante="secundario" onClick={() => setFiltros({})}>
                Quitar filtros
              </Button>
            ) : (
              <Button onClick={onAnadir}>Anadir el primer gasto</Button>
            )
          }
        />
      ) : (
        <>
          <ul className="divide-y divide-slate-100">
            {gastos.map((gasto) => (
              <ExpenseItem key={gasto.id} gasto={gasto} moneda={moneda} />
            ))}
          </ul>

          {hasNextPage && (
            <CargarMas
              cargando={isFetchingNextPage}
              onCargar={() => fetchNextPage()}
            />
          )}
        </>
      )}
    </Card>
  )
}

/**
 * Carga la pagina siguiente al llegar al final, y **tambien** con un boton.
 *
 * El boton no es un adorno ni un respaldo para navegadores viejos: el scroll
 * infinito solo funciona si hay scroll, y quien navega con teclado o con
 * lector de pantalla puede llegar al final de la lista sin provocar ningun
 * desplazamiento. Sin boton, para esa persona la lista simplemente se acaba
 * antes de tiempo, sin sintoma de que falte algo.
 */
function CargarMas({ cargando, onCargar }: { cargando: boolean; onCargar: () => void }) {
  const centinela = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const nodo = centinela.current
    if (!nodo) return

    const observador = new IntersectionObserver(
      (entradas) => {
        // La guarda de "cargando" la aplica el llamante con el boton
        // deshabilitado; aqui basta con no disparar si ya esta en curso.
        if (entradas[0].isIntersecting && !cargando) {
          onCargar()
        }
      },
      // Se adelanta un poco al borde para que la siguiente pagina llegue
      // antes de que el usuario se quede mirando el vacio.
      { rootMargin: '200px' },
    )

    observador.observe(nodo)
    return () => observador.disconnect()
  }, [cargando, onCargar])

  return (
    <div ref={centinela} className="mt-4 flex justify-center">
      <Button variante="secundario" cargando={cargando} onClick={onCargar}>
        {cargando ? 'Cargando...' : 'Cargar mas'}
      </Button>
    </div>
  )
}

function ListaEsqueleto() {
  return (
    <ul aria-busy="true" className="divide-y divide-slate-100">
      {[0, 1, 2, 3].map((i) => (
        <li key={i} className="flex items-start justify-between gap-4 py-4">
          <div className="flex-1">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="mt-2 h-3 w-1/3" />
          </div>
          <Skeleton className="h-4 w-16" />
        </li>
      ))}
    </ul>
  )
}
