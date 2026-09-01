import type { ReactNode } from 'react'

interface Props {
  titulo: string
  /** Que hacer a continuacion, no una disculpa por no tener datos. */
  descripcion?: string
  accion?: ReactNode
  icono?: ReactNode
}

/**
 * Lo que se ve cuando todavia no hay nada.
 *
 * Un estado vacio sin salida ("no tienes grupos") deja al usuario mirando una
 * pantalla en blanco sin saber si falta algo por cargar, si se rompio, o si
 * es que aun no ha hecho nada. Por eso el texto describe el siguiente paso y
 * casi siempre hay una accion.
 *
 * Es distinto de un error y de una carga: aqui todo ha ido bien y la respuesta
 * es que no hay datos.
 */
export default function EmptyState({ titulo, descripcion, accion, icono }: Props) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
      {icono && <div className="mb-3 text-slate-300">{icono}</div>}
      <h2 className="text-base font-medium text-slate-900">{titulo}</h2>
      {descripcion && (
        <p className="mt-1 max-w-sm text-sm text-slate-500">{descripcion}</p>
      )}
      {accion && <div className="mt-5">{accion}</div>}
    </div>
  )
}
