import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExpenseList from './ExpenseList'
import type { Expense, GroupMember, Paged } from '../../types/api'

/**
 * Quien ve los botones de editar y borrar.
 *
 * El backend solo deja tocar un gasto a quien lo pago o a un administrador del
 * grupo. La interfaz tiene que aplicar la misma regla: ofrecer botones que van
 * a responder 403 es peor que no ofrecerlos, porque el usuario descubre que no
 * puede despues de intentarlo.
 *
 * El escenario son **dos miembros con el mismo nombre**. Es el caso que obliga
 * a comparar identificadores: una implementacion que comparase `paidByName`
 * pasaria cualquier test con nombres distintos y aqui dejaria editar el gasto
 * ajeno.
 */
vi.mock('../../api/expenses', () => ({
  listarGastos: vi.fn(),
}))

const { listarGastos } = await import('../../api/expenses')
const listarGastosSimulado = vi.mocked(listarGastos)

const YO = 1
const LA_OTRA = 2

const MIEMBROS: GroupMember[] = [
  { userId: YO, name: 'Ana Garcia', email: 'ana1@test.com', role: 'MEMBER' },
  { userId: LA_OTRA, name: 'Ana Garcia', email: 'ana2@test.com', role: 'MEMBER' },
]

function gasto(id: number, descripcion: string, pagadoPor: number): Expense {
  return {
    id,
    description: descripcion,
    amount: 20,
    category: 'COMIDA',
    splitType: 'EQUAL',
    expenseDate: '2026-09-25',
    paidByUserId: pagadoPor,
    paidByName: 'Ana Garcia',
    splits: [{ userId: YO, userName: 'Ana Garcia', amountOwed: 10, value: null }],
  }
}

function montar(soyAdministrador: boolean) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ExpenseList
        groupId={1}
        moneda="EUR"
        miembros={MIEMBROS}
        onAnadir={() => {}}
        miId={YO}
        soyAdministrador={soyAdministrador}
        onEditar={() => {}}
        onBorrar={() => {}}
      />
    </QueryClientProvider>,
  )
}

describe('ExpenseList', () => {
  beforeEach(() => {
    const pagina: Paged<Expense> = {
      content: [gasto(10, 'Mi cena', YO), gasto(11, 'Su cena', LA_OTRA)],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      last: true,
    }
    listarGastosSimulado.mockReset()
    listarGastosSimulado.mockResolvedValue(pagina)
  })

  it('un miembro solo puede gestionar el gasto que pago el', async () => {
    montar(false)

    expect(await screen.findByRole('button', { name: 'Editar Mi cena' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Borrar Mi cena' })).toBeInTheDocument()

    // Las dos se llaman "Ana Garcia": si la comparacion fuera por nombre, este
    // boton estaria ahi y el backend lo rechazaria con un 403.
    expect(screen.queryByRole('button', { name: 'Editar Su cena' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Borrar Su cena' })).not.toBeInTheDocument()
  })

  it('un administrador puede gestionar tambien los ajenos', async () => {
    montar(true)

    expect(await screen.findByRole('button', { name: 'Editar Su cena' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Borrar Su cena' })).toBeInTheDocument()
  })
})
