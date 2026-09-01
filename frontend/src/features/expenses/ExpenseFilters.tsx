import Button from '../../components/Button'
import Select from '../../components/Select'
import Input from '../../components/Input'
import { CATEGORIAS, type ExpenseFilters, type GroupMember } from '../../types/api'
import { ETIQUETA_DE_CATEGORIA } from './categorias'

interface Props {
  filtros: ExpenseFilters
  onCambiar: (filtros: ExpenseFilters) => void
  miembros: GroupMember[]
}

/**
 * Filtros del listado de gastos.
 *
 * Los valores vacios se envian como `undefined` y no como cadena vacia: un
 * `?category=` sin valor viaja igual y el backend lo rechazaria, ademas de
 * generar una clave de cache distinta para lo que es la misma consulta.
 */
export default function ExpenseFiltersBar({ filtros, onCambiar, miembros }: Props) {
  const hayFiltros = Object.values(filtros).some((v) => v !== undefined)

  const cambiar = (parcial: Partial<ExpenseFilters>) =>
    onCambiar({ ...filtros, ...parcial })

  const vacioAUndefined = (valor: string) => (valor === '' ? undefined : valor)

  return (
    <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Select
        etiqueta="Categoria"
        value={filtros.category ?? ''}
        onChange={(e) =>
          cambiar({ category: (vacioAUndefined(e.target.value) as ExpenseFilters['category']) })
        }
      >
        <option value="">Todas</option>
        {CATEGORIAS.map((categoria) => (
          <option key={categoria} value={categoria}>
            {ETIQUETA_DE_CATEGORIA[categoria]}
          </option>
        ))}
      </Select>

      <Select
        etiqueta="Pagado por"
        value={filtros.paidBy ?? ''}
        onChange={(e) =>
          cambiar({ paidBy: e.target.value === '' ? undefined : Number(e.target.value) })
        }
      >
        <option value="">Cualquiera</option>
        {miembros.map((miembro) => (
          <option key={miembro.userId} value={miembro.userId}>
            {miembro.name}
          </option>
        ))}
      </Select>

      <Input
        etiqueta="Desde"
        type="date"
        value={filtros.from ?? ''}
        onChange={(e) => cambiar({ from: vacioAUndefined(e.target.value) })}
      />

      <Input
        etiqueta="Hasta"
        type="date"
        value={filtros.to ?? ''}
        onChange={(e) => cambiar({ to: vacioAUndefined(e.target.value) })}
      />

      {hayFiltros && (
        <div className="sm:col-span-2 lg:col-span-4">
          {/*
            Un boton explicito para vaciar. Sin el, quien ha combinado tres
            filtros tiene que recordar cuales toco y deshacerlos uno a uno, y
            si se deja uno puesto vera una lista corta creyendola completa.
          */}
          <Button variante="texto" onClick={() => onCambiar({})}>
            Quitar filtros
          </Button>
        </div>
      )}
    </div>
  )
}
