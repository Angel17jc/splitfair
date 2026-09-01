import type { ReactNode } from 'react'

interface Props {
  children: ReactNode
  className?: string
  /** Etiqueta HTML a usar. `section` o `article` cuando el bloque tiene entidad propia. */
  como?: 'div' | 'section' | 'article' | 'li'
}

/**
 * Contenedor con borde y fondo.
 *
 * `como` existe para no imponer un `div`: una tarjeta que representa un grupo
 * dentro de una lista debe ser un `li`, y una que agrupa contenido con titulo
 * propio, una `section`. Un arbol de `div` anidados no le dice nada a un
 * lector de pantalla.
 */
export default function Card({ children, className = '', como: Etiqueta = 'div' }: Props) {
  return (
    <Etiqueta
      className={`rounded-xl border border-slate-200 bg-white p-6 shadow-sm ${className}`}
    >
      {children}
    </Etiqueta>
  )
}
