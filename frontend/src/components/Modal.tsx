import { useEffect, useId, useRef, type ReactNode } from 'react'

interface Props {
  abierto: boolean
  onCerrar: () => void
  titulo: string
  children: ReactNode
  /** Botones de accion. Se colocan alineados a la derecha. */
  pie?: ReactNode
}

/**
 * Dialogo modal sobre el elemento nativo `<dialog>`.
 *
 * Se usa `showModal()` en vez de montar un div con posicion fija porque el
 * navegador da gratis, y bien hecho, lo que un modal casero implementa mal:
 *
 * - **Atrapa el foco.** Sin eso, tabular saca al usuario del dialogo hacia la
 *   pagina de detras, que sigue ahi aunque no se vea.
 * - **Cierra con Escape** sin escuchar teclas a mano.
 * - **Inertiza el resto de la pagina** para lectores de pantalla y clics.
 * - Se pinta en la capa superior, asi que ningun `z-index` de la aplicacion
 *   puede quedar por encima por accidente.
 *
 * Lo unico que hay que anadir es el cierre al pulsar fuera, que `<dialog>` no
 * trae: el clic sobre el fondo tiene como destino el propio dialogo, porque
 * `::backdrop` no es un elemento aparte.
 */
export default function Modal({ abierto, onCerrar, titulo, children, pie }: Props) {
  const referencia = useRef<HTMLDialogElement>(null)
  const idTitulo = useId()

  useEffect(() => {
    const dialogo = referencia.current
    if (!dialogo) return

    if (abierto && !dialogo.open) {
      dialogo.showModal()
    } else if (!abierto && dialogo.open) {
      dialogo.close()
    }
  }, [abierto])

  /**
   * `close` cubre todas las formas de cerrar, incluida la tecla Escape, que
   * el navegador gestiona por su cuenta. Escuchar solo el boton dejaria el
   * estado diciendo "abierto" con el dialogo ya cerrado, y la siguiente
   * apertura no haria nada.
   */
  useEffect(() => {
    const dialogo = referencia.current
    if (!dialogo) return

    const alCerrar = () => onCerrar()
    dialogo.addEventListener('close', alCerrar)
    return () => dialogo.removeEventListener('close', alCerrar)
  }, [onCerrar])

  return (
    <dialog
      ref={referencia}
      aria-labelledby={idTitulo}
      onClick={(evento) => {
        // El clic en el fondo llega con el dialogo como destino; el que ocurre
        // dentro del contenido lo tiene en un hijo.
        if (evento.target === referencia.current) {
          referencia.current?.close()
        }
      }}
      className="w-[min(28rem,calc(100vw-2rem))] rounded-xl border border-slate-200 p-0 shadow-lg backdrop:bg-slate-900/40"
    >
      <div className="p-6">
        <h2 id={idTitulo} className="text-lg font-semibold text-slate-900">
          {titulo}
        </h2>
        <div className="mt-3 text-sm text-slate-600">{children}</div>
        {pie && <div className="mt-6 flex justify-end gap-2">{pie}</div>}
      </div>
    </dialog>
  )
}
