import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RegistrarPagoModal from './RegistrarPagoModal'
import type { GroupMember } from '../../types/api'

/**
 * Registrar un pago que no sale de una sugerencia.
 *
 * Es lo que de verdad pasa entre personas: se paga a plazos, se paga de mas, o
 * se salda con quien viene bien en vez de con quien dice el algoritmo. El
 * backend siempre lo acepto —cualquier miembro, cualquier importe positivo—;
 * era la interfaz la que solo ofrecia el camino estrecho.
 */
vi.mock('../../api/settlements', () => ({
  obtenerBalances: vi.fn(),
  obtenerSugerencias: vi.fn(),
  registrarPago: vi.fn(),
  historialDePagos: vi.fn(),
  confirmarPago: vi.fn(),
  cancelarPago: vi.fn(),
}))

const { registrarPago } = await import('../../api/settlements')
const registrarPagoSimulado = vi.mocked(registrarPago)

const YO = 1
const MIEMBROS: GroupMember[] = [
  { userId: YO, name: 'Ana', email: 'ana@test.com', role: 'ADMIN' },
  { userId: 2, name: 'Beto', email: 'beto@test.com', role: 'MEMBER' },
  { userId: 3, name: 'Carla', email: 'carla@test.com', role: 'MEMBER' },
]

function montar() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <RegistrarPagoModal
        groupId={7}
        moneda="EUR"
        miembros={MIEMBROS}
        miId={YO}
        sugerencias={[]}
        onCerrar={() => {}}
      />
    </QueryClientProvider>,
  )
}

describe('RegistrarPagoModal', () => {
  beforeEach(() => {
    registrarPagoSimulado.mockReset()
    registrarPagoSimulado.mockResolvedValue({
      id: 99,
      paidByUserId: YO,
      paidByName: 'Ana',
      paidToUserId: 2,
      paidToName: 'Beto',
      amount: 12.5,
      currency: 'EUR',
      status: 'PENDING',
      createdAt: '2026-09-25T10:00:00Z',
    })
  })

  it('no ofrece pagarse a uno mismo', () => {
    montar()

    const opciones = screen
      .getAllByRole('option')
      .map((o) => o.textContent)
      .filter((t) => t !== 'Elige a alguien')

    // Un pago a uno mismo no significa nada y el backend lo rechazaria.
    expect(opciones).toEqual(['Beto', 'Carla'])
  })

  it('registra el importe escrito, aunque no coincida con ninguna sugerencia', async () => {
    const usuario = userEvent.setup()
    montar()

    await usuario.selectOptions(screen.getByLabelText(/A quien le pagas/i), '2')
    await usuario.type(screen.getByLabelText(/Importe/i), '12,50')
    await usuario.click(screen.getByRole('button', { name: 'Registrar' }))

    // Con coma decimal, que es como se escribe aqui, y sin limitarlo a lo que
    // se deba: un pago parcial o de mas es legitimo.
    expect(registrarPagoSimulado).toHaveBeenCalledWith(7, { paidTo: 2, amount: 12.5 })
  })

  it('no envia nada si falta el destinatario o el importe', async () => {
    const usuario = userEvent.setup()
    montar()

    await usuario.click(screen.getByRole('button', { name: 'Registrar' }))

    expect(registrarPagoSimulado).not.toHaveBeenCalled()
    expect(screen.getByText('Indica a quien le pagas')).toBeInTheDocument()
    expect(screen.getByText('Indica un importe mayor que cero')).toBeInTheDocument()
  })

  it('un importe de cero no cuenta como pago', async () => {
    const usuario = userEvent.setup()
    montar()

    await usuario.selectOptions(screen.getByLabelText(/A quien le pagas/i), '2')
    await usuario.type(screen.getByLabelText(/Importe/i), '0')
    await usuario.click(screen.getByRole('button', { name: 'Registrar' }))

    // Registrar un pago de cero ensuciaria el historial con apuntes que no
    // mueven dinero, y ademas el backend lo rechaza.
    //
    // Quien lo impide es `parsearImporte`, que ya devuelve null para el cero
    // (cubierto en dinero.test.ts). Este test no duplica esa regla: comprueba
    // que el formulario **la respeta** en vez de colarse por su cuenta, que es
    // lo que podria cambiar al tocar este componente.
    expect(registrarPagoSimulado).not.toHaveBeenCalled()
  })
})
