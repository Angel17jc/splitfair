import { useId, useState } from 'react'
import { formatearImporte } from '../../utils/dinero'
import type { MonthTotal } from '../../types/api'

interface Props {
  datos: MonthTotal[]
  moneda: string
}

const ANCHO = 320
const ALTO = 110
const MARGEN = { arriba: 8, derecha: 6, abajo: 20, izquierda: 6 }

/**
 * Evolucion del gasto mes a mes.
 *
 * Una sola serie, asi que no hace falta leyenda: el titulo ya dice que es.
 * Linea con relleno tenue debajo, que es lo indicado para serie unica; el
 * relleno da volumen sin anadir informacion que compita con la linea.
 *
 * El SVG usa un sistema de coordenadas fijo y se escala con CSS, con
 * `vector-effect="non-scaling-stroke"` para que la linea siga midiendo 2px
 * pase lo que pase con el ancho. Sin eso, al ensancharse el contenedor la
 * linea engorda en horizontal y adelgaza en vertical.
 */
export default function MonthlyTrend({ datos, moneda }: Props) {
  const idRelleno = useId()
  const [activo, setActivo] = useState<number | null>(null)

  if (datos.length === 0) return null

  // Con un solo mes no hay evolucion que dibujar: una linea de un punto es
  // una mentira visual. Se dice el dato y ya.
  if (datos.length === 1) {
    return (
      <figure className="m-0">
        <figcaption className="text-sm font-medium text-slate-700">Evolucion mensual</figcaption>
        <p className="mt-2 text-sm text-slate-500">
          Todo el gasto es de {etiquetaDeMes(datos[0].month)}:{' '}
          <span className="font-medium text-slate-900">
            {formatearImporte(datos[0].total, moneda)}
          </span>
          . Con un solo mes todavia no hay evolucion que mostrar.
        </p>
      </figure>
    )
  }

  const maximo = Math.max(...datos.map((d) => d.total))
  const anchoUtil = ANCHO - MARGEN.izquierda - MARGEN.derecha
  const altoUtil = ALTO - MARGEN.arriba - MARGEN.abajo

  const x = (i: number) => MARGEN.izquierda + (i / (datos.length - 1)) * anchoUtil
  // El eje empieza en cero, siempre. Recortarlo para "ver mejor la variacion"
  // exagera diferencias pequenas hasta hacerlas parecer saltos.
  const y = (valor: number) => MARGEN.arriba + altoUtil - (valor / maximo) * altoUtil

  const linea = datos.map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(d.total)}`).join(' ')
  const area = `${linea} L ${x(datos.length - 1)} ${MARGEN.arriba + altoUtil} L ${x(0)} ${
    MARGEN.arriba + altoUtil
  } Z`

  const mostrado = activo !== null ? datos[activo] : null

  return (
    <figure className="m-0">
      <figcaption className="text-sm font-medium text-slate-700">Evolucion mensual</figcaption>

      <div className="relative mt-3">
        <svg
          viewBox={`0 0 ${ANCHO} ${ALTO}`}
          className="w-full"
          role="img"
          aria-label={resumenParaLectores(datos, moneda)}
          onMouseLeave={() => setActivo(null)}
        >
          <defs>
            <linearGradient id={idRelleno} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--viz-serie, #2a78d6)" stopOpacity="0.18" />
              <stop offset="100%" stopColor="var(--viz-serie, #2a78d6)" stopOpacity="0" />
            </linearGradient>
          </defs>

          <path d={area} fill={`url(#${idRelleno})`} />
          <path
            d={linea}
            fill="none"
            stroke="var(--viz-serie, #2a78d6)"
            strokeWidth="2"
            strokeLinejoin="round"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
          />

          {datos.map((d, i) => (
            <g key={d.month}>
              {/* Zona de deteccion mucho mas ancha que el punto: acertar en un
                  circulo de 4px con el raton es un ejercicio de punteria. */}
              <rect
                x={x(i) - anchoUtil / (datos.length * 2)}
                y={0}
                width={anchoUtil / datos.length}
                height={ALTO}
                fill="transparent"
                onMouseEnter={() => setActivo(i)}
              />
              <circle
                cx={x(i)}
                cy={y(d.total)}
                r={activo === i ? 4.5 : 3}
                fill="var(--viz-serie, #2a78d6)"
                stroke="#fcfcfb"
                strokeWidth="2"
                vectorEffect="non-scaling-stroke"
              />
            </g>
          ))}

          {/* Solo se etiquetan los extremos: un numero sobre cada punto
              convierte el grafico en una tabla mal maquetada. */}
          <text
            x={x(0)}
            y={ALTO - 6}
            textAnchor="start"
            className="fill-slate-400"
            fontSize="9"
          >
            {etiquetaDeMes(datos[0].month)}
          </text>
          <text
            x={x(datos.length - 1)}
            y={ALTO - 6}
            textAnchor="end"
            className="fill-slate-400"
            fontSize="9"
          >
            {etiquetaDeMes(datos[datos.length - 1].month)}
          </text>
        </svg>

        {/* aria-live para que el dato que aparece al pasar el raton tambien se
            anuncie; el SVG ya lleva su descripcion completa aparte. */}
        <p aria-live="polite" className="mt-1 h-4 text-xs text-slate-500">
          {mostrado
            ? `${etiquetaDeMes(mostrado.month)}: ${formatearImporte(mostrado.total, moneda)} en ${
                mostrado.count
              } ${mostrado.count === 1 ? 'gasto' : 'gastos'}`
            : ''}
        </p>
      </div>

      <table className="sr-only">
        <caption>Gasto por mes</caption>
        <thead>
          <tr>
            <th scope="col">Mes</th>
            <th scope="col">Total</th>
            <th scope="col">Numero de gastos</th>
          </tr>
        </thead>
        <tbody>
          {datos.map((d) => (
            <tr key={d.month}>
              <th scope="row">{etiquetaDeMes(d.month)}</th>
              <td>{formatearImporte(d.total, moneda)}</td>
              <td>{d.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  )
}

/** `2026-09` -> `sept 2026`. Se construye sin `new Date` para no tocar husos. */
function etiquetaDeMes(mes: string): string {
  const [ano, numero] = mes.split('-').map(Number)
  const nombre = new Intl.DateTimeFormat('es-ES', { month: 'short' }).format(
    new Date(ano, numero - 1, 1),
  )
  return `${nombre} ${ano}`
}

function resumenParaLectores(datos: MonthTotal[], moneda: string): string {
  const primero = datos[0]
  const ultimo = datos[datos.length - 1]
  return (
    `Evolucion del gasto en ${datos.length} meses, de ${etiquetaDeMes(primero.month)} a ` +
    `${etiquetaDeMes(ultimo.month)}. Empieza en ${formatearImporte(primero.total, moneda)} y ` +
    `termina en ${formatearImporte(ultimo.total, moneda)}.`
  )
}
