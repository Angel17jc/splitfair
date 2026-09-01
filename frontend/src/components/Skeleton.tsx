interface Props {
  className?: string
}

/**
 * Bloque gris que ocupa el sitio del contenido mientras llega.
 *
 * Se usa en vez de un spinner centrado porque reserva el **espacio real**: al
 * llegar los datos, la pagina no da un salto empujando hacia abajo lo que el
 * usuario estaba a punto de pulsar.
 *
 * Va con `aria-hidden`: para un lector de pantalla no hay nada que leer aqui.
 * Quien anuncia la espera es el contenedor, con `aria-busy`.
 */
export default function Skeleton({ className = '' }: Props) {
  return (
    <div aria-hidden="true" className={`animate-pulse rounded bg-slate-200 ${className}`} />
  )
}
