import Card from '../../components/Card'
import ErrorState from '../../components/ErrorState'
import Skeleton from '../../components/Skeleton'
import CategoryBars from './CategoryBars'
import MonthlyTrend from './MonthlyTrend'
import { useAnalitica } from './hooks'

interface Props {
  groupId: number
}

/**
 * En que se va el dinero del grupo y como evoluciona.
 *
 * Las cifras vienen agregadas del backend, no de sumar la lista de gastos: esa
 * lista esta paginada, asi que sumar lo cargado daria totales parciales con
 * pinta de completos, y ademas sumar dinero en JavaScript es sumar en coma
 * flotante.
 */
export default function AnalyticsPanel({ groupId }: Props) {
  const { data, isPending, isError, error, refetch } = useAnalitica(groupId)

  if (isError) {
    return (
      <Card como="section">
        <h2 className="text-base font-medium text-slate-900">Analitica</h2>
        <div className="mt-4">
          <ErrorState error={error} contexto="la analitica" onReintentar={() => refetch()} />
        </div>
      </Card>
    )
  }

  if (isPending) {
    return (
      <Card como="section" aria-busy="true">
        <h2 className="text-base font-medium text-slate-900">Analitica</h2>
        <Skeleton className="mt-4 h-4 w-1/3" />
        <div className="mt-3 space-y-2.5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-5 w-full" />
          ))}
        </div>
      </Card>
    )
  }

  // Sin gastos no hay nada que analizar, y una tarjeta vacia con dos titulos
  // ocupa sitio sin decir nada. Se retira entera.
  if (data.byCategory.length === 0) {
    return null
  }

  return (
    <Card como="section">
      <h2 className="text-base font-medium text-slate-900">Analitica</h2>

      <div className="mt-4 space-y-6">
        <CategoryBars datos={data.byCategory} moneda={data.currency} total={data.totalSpent} />
        <MonthlyTrend datos={data.byMonth} moneda={data.currency} />
      </div>
    </Card>
  )
}
