import { useState } from 'react'
import Button from '../../components/Button'
import Modal from '../../components/Modal'
import { ApiError } from '../../api/errors'
import { COLOR_DE_SALDO, describirSaldo, formatearImporteAbsoluto, signoDeSaldo } from '../../utils/dinero'
import { useCambiarRol, useExpulsarMiembro } from './mutaciones'
import type { Balance, GroupMember } from '../../types/api'

interface Props {
  groupId: number
  miembros: GroupMember[]
  balances: Balance[] | undefined
  moneda: string
  miId: number | undefined
  soyAdministrador: boolean
  onCerrar: () => void
  /** Se llama tras salir del grupo: ya no se puede seguir en esta pantalla. */
  onSali: () => void
}

/**
 * Gestion de miembros: ascender, degradar, expulsar y salir.
 *
 * Va en un dialogo aparte y no dentro de la tarjeta de balances porque son dos
 * preguntas distintas —como esta el grupo de dinero, y quien puede hacer que—
 * y esa tarjeta es estrecha.
 *
 * Aun asi **se muestra el saldo de cada uno aqui tambien**, y no por adorno:
 * el backend se niega a sacar del grupo a quien no esta a cero, porque los
 * balances se construyen a partir de la lista de miembros y quitar a un deudor
 * haria que los de los demas dejaran de sumar cero. Teniendo el saldo al lado,
 * la negativa se entiende sin leerla dos veces.
 */
export default function MiembrosModal({
  groupId,
  miembros,
  balances,
  moneda,
  miId,
  soyAdministrador,
  onCerrar,
  onSali,
}: Props) {
  const cambiarRol = useCambiarRol(groupId)
  const expulsar = useExpulsarMiembro(groupId)

  /** El error se guarda por miembro: va justo debajo de quien lo provoco. */
  const [errores, setErrores] = useState<Record<number, string>>({})
  const [enCurso, setEnCurso] = useState<number | null>(null)

  const saldoDe = new Map((balances ?? []).map((b) => [b.userId, b.netBalance]))
  const administradores = miembros.filter((m) => m.role === 'ADMIN').length

  const ejecutar = async (userId: number, accion: () => Promise<unknown>, alSalir?: () => void) => {
    setErrores((antes) => ({ ...antes, [userId]: '' }))
    setEnCurso(userId)
    try {
      await accion()
      alSalir?.()
    } catch (fallo) {
      // Los 400 del backend traen el motivo exacto —el importe pendiente, o
      // que eres el unico administrador— y se muestran tal cual. Sustituirlos
      // por un "no se pudo completar la operacion" perderia justo lo que le
      // dice al usuario que hacer a continuacion.
      setErrores((antes) => ({
        ...antes,
        [userId]: fallo instanceof ApiError ? fallo.message : 'No se pudo completar la operacion.',
      }))
    } finally {
      setEnCurso(null)
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Miembros del grupo"
      pie={
        <Button variante="secundario" onClick={onCerrar}>
          Cerrar
        </Button>
      }
    >
      <ul className="divide-y divide-slate-100">
        {miembros.map((miembro) => {
          const soyYo = miembro.userId === miId
          const saldo = saldoDe.get(miembro.userId)
          const ocupado = enCurso === miembro.userId
          const error = errores[miembro.userId]

          return (
            <li key={miembro.userId} className="py-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900">
                    {miembro.name}
                    {soyYo && <span className="ml-1 font-normal text-slate-400">(tu)</span>}
                    {miembro.role === 'ADMIN' && (
                      <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                        Admin
                      </span>
                    )}
                  </p>
                  <p className="truncate text-xs text-slate-500">{miembro.email}</p>
                  {saldo !== undefined && (
                    <p className={`mt-0.5 text-xs ${COLOR_DE_SALDO[signoDeSaldo(saldo)]}`}>
                      {soyYo ? describirSaldo(saldo, moneda) : descripcionAjena(saldo, moneda)}
                    </p>
                  )}
                </div>

                <div className="flex shrink-0 flex-wrap gap-2 text-xs">
                  {soyAdministrador && !soyYo && (
                    <button
                      type="button"
                      disabled={ocupado}
                      onClick={() =>
                        ejecutar(miembro.userId, () =>
                          cambiarRol.mutateAsync({
                            userId: miembro.userId,
                            role: miembro.role === 'ADMIN' ? 'MEMBER' : 'ADMIN',
                          }),
                        )
                      }
                      aria-label={`${miembro.role === 'ADMIN' ? 'Quitar administrador a' : 'Hacer administrador a'} ${miembro.name}`}
                      className="rounded text-slate-500 underline-offset-2 hover:text-slate-900 hover:underline disabled:opacity-50"
                    >
                      {miembro.role === 'ADMIN' ? 'Quitar admin' : 'Hacer admin'}
                    </button>
                  )}

                  {soyAdministrador && !soyYo && (
                    <button
                      type="button"
                      disabled={ocupado}
                      onClick={() =>
                        ejecutar(miembro.userId, () =>
                          expulsar.mutateAsync({ userId: miembro.userId, salgoYo: false }),
                        )
                      }
                      aria-label={`Expulsar a ${miembro.name}`}
                      className="rounded text-slate-500 underline-offset-2 hover:text-red-700 hover:underline disabled:opacity-50"
                    >
                      Expulsar
                    </button>
                  )}

                  {soyYo && (
                    <button
                      type="button"
                      disabled={ocupado}
                      onClick={() =>
                        ejecutar(
                          miembro.userId,
                          () => expulsar.mutateAsync({ userId: miembro.userId, salgoYo: true }),
                          onSali,
                        )
                      }
                      aria-label="Salir del grupo"
                      className="rounded text-slate-500 underline-offset-2 hover:text-red-700 hover:underline disabled:opacity-50"
                    >
                      Salir del grupo
                    </button>
                  )}
                </div>
              </div>

              {error && (
                <p role="alert" className="mt-2 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">
                  {error}
                </p>
              )}
            </li>
          )
        })}
      </ul>

      {soyAdministrador && administradores === 1 && miembros.length > 1 && (
        <p className="mt-4 rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-600">
          Eres el unico administrador. Para poder salir del grupo o dejar de serlo, asciende antes
          a otro miembro.
        </p>
      )}
    </Modal>
  )
}

/** "Te deben" solo funciona en primera persona; visto desde fuera se invierte. */
function descripcionAjena(saldo: number, moneda: string): string {
  switch (signoDeSaldo(saldo)) {
    case 'acreedor':
      return `Le deben ${formatearImporteAbsoluto(saldo, moneda)}`
    case 'deudor':
      return `Debe ${formatearImporteAbsoluto(saldo, moneda)}`
    default:
      return 'Esta al dia'
  }
}
