import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MiembrosModal from './MiembrosModal'
import { ApiError } from '../../api/errors'
import type { Balance, GroupMember } from '../../types/api'

/**
 * Gestion de miembros.
 *
 * Lo que se fija aqui son dos cosas que el backend ya decide y la interfaz
 * tiene que respetar sin inventarse nada:
 *
 * 1. **Quien ve cada accion.** Ofrecer un boton que va a responder 403 es peor
 *    que no ofrecerlo: el usuario descubre que no puede despues de intentarlo.
 * 2. **Que el motivo de una negativa llegue intacto.** El backend responde 400
 *    con el importe pendiente o con "promueve antes a otro miembro", que es
 *    justo lo que hay que hacer a continuacion. Sustituirlo por un "no se pudo
 *    completar la operacion" convierte una instruccion en un callejon.
 */
vi.mock('../../api/groups', () => ({
  listarGrupos: vi.fn(),
  obtenerGrupo: vi.fn(),
  crearGrupo: vi.fn(),
  actualizarGrupo: vi.fn(),
  anadirMiembro: vi.fn(),
  expulsarMiembro: vi.fn(),
  cambiarRol: vi.fn(),
}))
vi.mock('../../api/invitations', () => ({
  crearInvitacion: vi.fn(),
  aceptarInvitacion: vi.fn(),
  vistaPreviaDeInvitacion: vi.fn(),
}))

const { expulsarMiembro, cambiarRol } = await import('../../api/groups')
const expulsarSimulado = vi.mocked(expulsarMiembro)
const cambiarRolSimulado = vi.mocked(cambiarRol)

const YO = 1
const OTRA = 2

const MIEMBROS: GroupMember[] = [
  { userId: YO, name: 'Ana', email: 'ana@test.com', role: 'ADMIN' },
  { userId: OTRA, name: 'Beto', email: 'beto@test.com', role: 'MEMBER' },
]

const BALANCES: Balance[] = [
  {
    userId: YO,
    userName: 'Ana',
    totalPaid: 40,
    totalOwed: 20,
    settlementsPaid: 0,
    settlementsReceived: 0,
    netBalance: 20,
  },
  {
    userId: OTRA,
    userName: 'Beto',
    totalPaid: 0,
    totalOwed: 20,
    settlementsPaid: 0,
    settlementsReceived: 0,
    netBalance: -20,
  },
]

const onSali = vi.fn()

function montar(soyAdministrador: boolean, miembros: GroupMember[] = MIEMBROS) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MiembrosModal
        groupId={7}
        miembros={miembros}
        balances={BALANCES}
        moneda="EUR"
        miId={YO}
        soyAdministrador={soyAdministrador}
        onCerrar={() => {}}
        onSali={onSali}
      />
    </QueryClientProvider>,
  )
}

describe('MiembrosModal', () => {
  beforeEach(() => {
    onSali.mockReset()
    expulsarSimulado.mockReset()
    cambiarRolSimulado.mockReset()
    expulsarSimulado.mockResolvedValue(undefined)
  })

  it('un miembro corriente solo puede salir, no gestionar a los demas', () => {
    montar(false)

    expect(screen.getByRole('button', { name: 'Salir del grupo' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Expulsar a Beto' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Hacer administrador a Beto' }),
    ).not.toBeInTheDocument()
  })

  it('un administrador gestiona a los demas, pero no se expulsa a si mismo', () => {
    montar(true)

    expect(screen.getByRole('button', { name: 'Expulsar a Beto' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hacer administrador a Beto' })).toBeInTheDocument()

    // Para uno mismo la accion es "salir", no "expulsar": son la misma
    // peticion, pero llamarla expulsion invita a pulsarla creyendo que echa a
    // otro.
    expect(screen.queryByRole('button', { name: 'Expulsar a Ana' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salir del grupo' })).toBeInTheDocument()
  })

  it('muestra intacto el motivo por el que el backend se niega', async () => {
    const usuario = userEvent.setup()
    expulsarSimulado.mockRejectedValue(
      new ApiError(
        'Ese miembro tiene una deuda pendiente de 20.00. Hay que saldar las cuentas antes de salir del grupo.',
        400,
      ),
    )
    montar(true)

    await usuario.click(screen.getByRole('button', { name: 'Expulsar a Beto' }))

    // El texto del backend dice el importe y que hacer. Cualquier resumen
    // nuestro perderia una de las dos cosas.
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /deuda pendiente de 20.00.*saldar las cuentas/,
    )
  })

  it('salir del grupo avisa al padre para que deje la pantalla', async () => {
    const usuario = userEvent.setup()
    montar(true, [
      { userId: YO, name: 'Ana', email: 'ana@test.com', role: 'ADMIN' },
      { userId: OTRA, name: 'Beto', email: 'beto@test.com', role: 'ADMIN' },
    ])

    await usuario.click(screen.getByRole('button', { name: 'Salir del grupo' }))

    // Quedarse en el detalle tras salir provocaria un 403 en la siguiente
    // consulta del grupo, que el usuario leeria como un fallo.
    expect(expulsarSimulado).toHaveBeenCalledWith(7, YO)
    expect(onSali).toHaveBeenCalled()
  })

  it('avisa al unico administrador antes de que lo intente', () => {
    montar(true)

    // El backend se negaria igualmente, pero decirlo antes ahorra el intento
    // y explica por que el grupo no le deja irse.
    expect(screen.getByText(/unico administrador/i)).toBeInTheDocument()
  })

  it('el saldo de cada uno se ve junto a las acciones', () => {
    montar(true)

    // Es lo que explica que una expulsion se rechace: el backend no deja salir
    // a quien no esta a cero.
    expect(screen.getByText(/Debe 20,00/)).toBeInTheDocument()
  })
})
