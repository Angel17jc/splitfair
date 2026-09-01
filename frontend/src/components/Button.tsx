import type { ButtonHTMLAttributes } from 'react'
import Spinner from './Spinner'

type Variante = 'primario' | 'secundario' | 'peligro' | 'texto'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante
  /** Ocupa todo el ancho disponible. Util en formularios estrechos. */
  ancho?: boolean
  cargando?: boolean
}

const BASE =
  'inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium ' +
  'transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

const VARIANTES: Record<Variante, string> = {
  primario: 'bg-slate-900 text-white hover:bg-slate-700 focus-visible:outline-slate-900',
  secundario:
    'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus-visible:outline-slate-900',
  peligro: 'bg-red-600 text-white hover:bg-red-700 focus-visible:outline-red-600',
  texto: 'text-slate-700 hover:bg-slate-100 focus-visible:outline-slate-900',
}

/**
 * Boton de la aplicacion.
 *
 * `cargando` deshabilita y anuncia la espera, pero **mantiene el texto**. Un
 * boton que sustituye su etiqueta por un spinner cambia de ancho y desplaza
 * lo que tiene al lado justo cuando el usuario acaba de pulsar, que es el peor
 * momento para mover la interfaz.
 *
 * Se usa `focus-visible` y no `focus`: el anillo debe aparecer para quien
 * navega con teclado y no despues de cada clic con raton.
 */
export default function Button({
  variante = 'primario',
  ancho = false,
  cargando = false,
  disabled,
  children,
  className = '',
  type = 'button',
  ...resto
}: Props) {
  return (
    <button
      // El tipo por defecto de <button> dentro de un formulario es "submit",
      // asi que un boton auxiliar sin type lo envia sin querer.
      type={type}
      disabled={disabled || cargando}
      aria-busy={cargando || undefined}
      className={`${BASE} ${VARIANTES[variante]} ${ancho ? 'w-full' : ''} ${className}`}
      {...resto}
    >
      {cargando && <Spinner etiqueta="" />}
      {children}
    </button>
  )
}
