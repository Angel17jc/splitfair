import Input from '../../components/Input'
import { MODOS_DE_REPARTO, type Cuadre } from './reparto'
import type { GroupMember, SplitType } from '../../types/api'

interface Props {
  tipo: SplitType
  miembros: GroupMember[]
  participantes: number[]
  valores: Record<string, string>
  cuadre: Cuadre
  onAlternar: (userId: number) => void
  onValor: (userId: number, valor: string) => void
}

/**
 * Quien participa en el gasto y, si el reparto no es a partes iguales, cuanto
 * le toca a cada uno.
 *
 * El cuadre se muestra **mientras se escribe**, no al enviar. Descubrir que
 * faltan 3,40 € despues de pulsar el boton obliga a releer cuatro campos para
 * encontrar donde; verlo bajar hasta cero conforme se teclea convierte el
 * mismo dato en una guia.
 */
export default function SplitEditor({
  tipo,
  miembros,
  participantes,
  valores,
  cuadre,
  onAlternar,
  onValor,
}: Props) {
  const modo = MODOS_DE_REPARTO[tipo]
  const conImportes = tipo !== 'EQUAL'

  return (
    <fieldset>
      <legend className="text-sm font-medium text-slate-700">Repartir entre</legend>
      <p className="mt-0.5 text-xs text-slate-500">{modo.ayuda}</p>

      <div className="mt-3 space-y-2">
        {miembros.map((miembro) => {
          const participa = participantes.includes(miembro.userId)

          return (
            <div key={miembro.userId} className="flex items-center gap-3">
              <label className="flex flex-1 cursor-pointer items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={participa}
                  onChange={() => onAlternar(miembro.userId)}
                  className="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
                />
                {miembro.name}
              </label>

              {conImportes && (
                <div className="flex w-32 items-center gap-1">
                  <Input
                    etiqueta={`${modo.etiqueta} de ${miembro.name}`}
                    /* La etiqueta existe para los lectores de pantalla aunque
                       no se vea: una columna de campos sin nombre es
                       indescifrable si no se puede ver la fila. */
                    etiquetaOculta
                    className="text-right"
                    type="text"
                    inputMode="decimal"
                    placeholder="0"
                    // Deshabilitado y no oculto: quien deja de participar ve
                    // que su casilla sigue ahi y puede volver a entrar sin
                    // que la fila se reorganice bajo el cursor.
                    disabled={!participa}
                    value={valores[String(miembro.userId)] ?? ''}
                    onChange={(e) => onValor(miembro.userId, e.target.value)}
                  />
                  {modo.sufijo && (
                    <span aria-hidden="true" className="text-sm text-slate-500">
                      {modo.sufijo}
                    </span>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>

      {conImportes && (
        /*
          aria-live para que el cuadre se anuncie al cambiar. Es informacion
          que se actualiza sola mientras el usuario escribe en otro sitio, y
          sin esto solo existe para quien la ve.
        */
        <p
          aria-live="polite"
          className={`mt-3 text-sm ${cuadre.valido ? 'text-emerald-700' : 'text-amber-700'}`}
        >
          {cuadre.valido ? 'El reparto cuadra.' : cuadre.mensaje}
        </p>
      )}
    </fieldset>
  )
}
