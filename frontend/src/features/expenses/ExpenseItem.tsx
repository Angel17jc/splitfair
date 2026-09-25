import { formatearFecha, formatearImporte } from '../../utils/dinero'
import { COLOR_DE_CATEGORIA, ETIQUETA_DE_CATEGORIA } from './categorias'
import type { GastoEnLista } from './hooks'

interface Props {
  gasto: GastoEnLista
  moneda: string
  /**
   * Acciones de gestion. Se pasan solo cuando el usuario puede ejecutarlas;
   * si no, no se pinta ningun boton.
   *
   * Ofrecer botones que el backend va a rechazar con un 403 es peor que no
   * ofrecerlos: el usuario descubre que no puede despues de intentarlo.
   */
  onEditar?: () => void
  onBorrar?: () => void
}

export default function ExpenseItem({ gasto, moneda, onEditar, onBorrar }: Props) {
  // Un gasto provisional lleva un id negativo que no existe en el servidor,
  // asi que editarlo o borrarlo fallaria. Se ocultan hasta que llega el real.
  const gestionable = !gasto.optimista && (onEditar || onBorrar)
  return (
    /*
      Un gasto provisional se atenua y se marca como que se esta guardando. Sin
      esa senal, una fila que aparece y luego desaparece porque el servidor la
      rechazo parece un fallo de la aplicacion; con ella, es el estado que el
      usuario ya esperaba.
    */
    <li
      aria-busy={gasto.optimista}
      className={`flex items-start justify-between gap-4 py-4 ${
        gasto.optimista ? 'opacity-60' : ''
      }`}
    >
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-slate-900">{gasto.description}</p>

        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-500">
          <span
            className={`rounded-full px-2 py-0.5 font-medium ${COLOR_DE_CATEGORIA[gasto.category]}`}
          >
            {ETIQUETA_DE_CATEGORIA[gasto.category]}
          </span>
          <span>{formatearFecha(gasto.expenseDate)}</span>
          <span aria-hidden="true">·</span>
          <span>Pago {gasto.paidByName}</span>
        </div>

        {/*
          Se dice entre cuantos se reparte y no solo el importe total: es la
          diferencia entre "cenamos 60" y "me tocan 20", que es el dato por el
          que se abre la aplicacion.
        */}
        <p className="mt-1 text-xs text-slate-400">
          {gasto.optimista ? (
            'Guardando...'
          ) : (
            <>
              Entre {gasto.splits.length}{' '}
              {gasto.splits.length === 1 ? 'persona' : 'personas'}
              {gasto.splitType !== 'EQUAL' && ' · reparto personalizado'}
            </>
          )}
        </p>
      </div>

      <div className="flex shrink-0 flex-col items-end gap-1">
        <p className="text-sm font-medium tabular-nums text-slate-900">
          {formatearImporte(gasto.amount, moneda)}
        </p>

        {gestionable && (
          <div className="flex gap-2 text-xs">
            {onEditar && (
              <button
                type="button"
                onClick={onEditar}
                /* El nombre accesible incluye la descripcion: una lista con
                   diez botones llamados "Editar" no dice a cual pertenece
                   cada uno para quien navega por voz o con lector. */
                aria-label={`Editar ${gasto.description}`}
                className="rounded text-slate-500 underline-offset-2 hover:text-slate-900 hover:underline"
              >
                Editar
              </button>
            )}
            {onBorrar && (
              <button
                type="button"
                onClick={onBorrar}
                aria-label={`Borrar ${gasto.description}`}
                className="rounded text-slate-500 underline-offset-2 hover:text-red-700 hover:underline"
              >
                Borrar
              </button>
            )}
          </div>
        )}
      </div>
    </li>
  )
}
