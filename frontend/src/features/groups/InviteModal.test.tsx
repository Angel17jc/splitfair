import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InviteModal from './InviteModal'

/**
 * El link de invitacion no debe sobrevivir al cierre del modal.
 *
 * Importa porque el link es de **un solo uso**: si al reabrir siguiera ahi el
 * de la vez anterior, lo natural es volver a copiarlo y enviarlo a otra
 * persona, que se encontraria con una invitacion ya gastada. El fallo no se
 * ve al provocarlo —el modal se abre con un link de aspecto perfectamente
 * valido— sino dias despues, en la persona equivocada.
 *
 * Este test se escribio **antes** de mover el reinicio fuera del efecto, y
 * sirvio: al refactorizar se puso rojo. El reinicio ya no ocurre si el padre
 * baja `abierto` por su cuenta, solo si el cierre pasa por `onCerrar`.
 *
 * Es un cambio de contrato real, aunque invisible en la aplicacion: el
 * `<dialog>` nativo emite `close` con el boton, con Escape y con el clic en el
 * fondo, `Modal` lo reenvia a `onCerrar`, y el unico sitio donde el padre
 * cierra es ese manejador. El test se reescribio para cerrar **como cierra una
 * persona**, que ademas es el recorrido que de verdad hay que proteger.
 */
vi.mock('../../api/invitations', () => ({
  crearInvitacion: vi.fn(),
}))

const { crearInvitacion } = await import('../../api/invitations')
const crearInvitacionSimulada = vi.mocked(crearInvitacion)

function montar() {
  // Sin reintentos: un fallo debe verse en el propio test y no reintentarse
  // tres veces hasta agotar el tiempo.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  const vista = render(
    <QueryClientProvider client={queryClient}>
      <InviteModal abierto onCerrar={() => {}} groupId={1} nombreDelGrupo="Piso" />
    </QueryClientProvider>,
  )

  const conApertura = (abierto: boolean) =>
    vista.rerender(
      <QueryClientProvider client={queryClient}>
        <InviteModal abierto={abierto} onCerrar={() => {}} groupId={1} nombreDelGrupo="Piso" />
      </QueryClientProvider>,
    )

  return { conApertura }
}

describe('InviteModal', () => {
  beforeEach(() => {
    crearInvitacionSimulada.mockReset()
    crearInvitacionSimulada.mockResolvedValue({
      id: 1,
      url: 'http://localhost:5173/invitacion/abc123',
      token: 'abc123',
      expiresAt: '2026-10-01T00:00:00Z',
    })
  })

  it('al reabrir no conserva el link generado antes', async () => {
    const usuario = userEvent.setup()
    const { conApertura } = montar()

    await usuario.click(screen.getByRole('button', { name: 'Generar link' }))

    // El link lo compone el cliente con su propio origen y el token; el campo
    // `url` que devuelve la API no se usa. Por eso se afirma sobre el token,
    // que es lo unico que sale del servidor.
    expect(await screen.findByLabelText('Link de invitacion')).toHaveValue(
      `${window.location.origin}/invitacion/abc123`,
    )

    // Cerrar como lo hace una persona, no bajando la prop a mano: el reinicio
    // cuelga del manejador de cierre, que es por donde pasan todas las vias.
    await usuario.click(screen.getByRole('button', { name: 'Cerrar' }))
    conApertura(false)
    conApertura(true)

    // El campo del link desaparece y vuelve la pantalla inicial: el modal
    // reabierto ofrece generar uno nuevo, no reutilizar el anterior.
    expect(screen.queryByLabelText('Link de invitacion')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generar link' })).toBeInTheDocument()
  })

  it('no genera ningun link por su cuenta al abrirse', async () => {
    montar()

    // Abrir el modal no debe consumir una invitacion: son de un solo uso y
    // caducan, asi que generarlas sin pedirlo las desperdicia.
    expect(crearInvitacionSimulada).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Generar link' })).toBeInTheDocument()
  })
})
