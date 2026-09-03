import { formatearImporte } from '../../utils/dinero'
import { ETIQUETA_DE_CATEGORIA } from '../expenses/categorias'
import type { CategoryTotal } from '../../types/api'

interface Props {
  datos: CategoryTotal[]
  moneda: string
  total: number
}

/**
 * Gasto por categoria, en barras horizontales.
 *
 * ## Por que barras y no un sector circular
 *
 * La pregunta es "en que se va el dinero", es decir **comparar magnitudes**.
 * Comparar longitudes alineadas contra un mismo origen es lo que mejor hace el
 * ojo; comparar angulos, de lo peor. Ademas los nombres de categoria son
 * largos y en horizontal caben sin girarlos.
 *
 * ## Por que todas las barras del mismo color
 *
 * La longitud ya codifica la magnitud. Pintar ademas un degradado por valor
 * seria codificar dos veces lo mismo, y un abanico de ocho colores para ocho
 * categorias es peor todavia: a partir de cuatro tonos, dos de ellos dejan de
 * distinguirse para quien tiene daltonismo, y el color no estaria diciendo
 * nada que la barra no diga ya.
 *
 * El tono es el azul `#2a78d6`, con 3:1 de contraste contra el fondo claro.
 */
export default function CategoryBars({ datos, moneda, total }: Props) {
  if (datos.length === 0) return null

  // La escala se toma del mayor, no del total: si no, con ocho categorias
  // repartidas todas las barras saldrian diminutas y no se compararian entre
  // si, que es justo para lo que estan.
  const mayor = Math.max(...datos.map((d) => d.total))

  return (
    <figure className="m-0">
      <figcaption className="text-sm font-medium text-slate-700">En que se gasta</figcaption>

      {/*
        El grafico se marca como imagen con su descripcion, y debajo va la
        tabla con las mismas cifras. Quien no ve las barras no se queda sin el
        dato: lo lee.
      */}
      <div role="img" aria-label={resumenParaLectores(datos, moneda, total)} className="mt-3">
        <ul className="space-y-2.5">
          {datos.map((fila) => (
            <li key={fila.category}>
              <div className="flex items-baseline justify-between gap-3 text-xs">
                <span className="text-slate-600">{ETIQUETA_DE_CATEGORIA[fila.category]}</span>
                {/* Etiqueta directa: el valor junto a su barra, sin obligar a
                    cruzar la vista hasta un eje. */}
                <span className="font-medium tabular-nums text-slate-900">
                  {formatearImporte(fila.total, moneda)}
                </span>
              </div>
              <div className="mt-1 h-2 w-full rounded-sm bg-slate-100">
                <div
                  className="h-2 rounded-sm"
                  style={{
                    // Minimo visible: una categoria con 0,50 € de 900 € daria
                    // una barra de cero pixeles y pareceria que no existe.
                    width: `${Math.max(2, (fila.total / mayor) * 100)}%`,
                    backgroundColor: 'var(--viz-serie, #2a78d6)',
                  }}
                />
              </div>
            </li>
          ))}
        </ul>
      </div>

      <TablaOculta datos={datos} moneda={moneda} />
    </figure>
  )
}

function resumenParaLectores(datos: CategoryTotal[], moneda: string, total: number): string {
  const mayor = datos[0]
  return (
    `Gasto por categoria. Total ${formatearImporte(total, moneda)} repartido en ` +
    `${datos.length} categorias. La mayor es ${ETIQUETA_DE_CATEGORIA[mayor.category]} ` +
    `con ${formatearImporte(mayor.total, moneda)}.`
  )
}

/**
 * Las mismas cifras en una tabla, oculta a la vista pero no a un lector de
 * pantalla ni al buscador del navegador.
 */
function TablaOculta({ datos, moneda }: { datos: CategoryTotal[]; moneda: string }) {
  return (
    <table className="sr-only">
      <caption>Gasto por categoria</caption>
      <thead>
        <tr>
          <th scope="col">Categoria</th>
          <th scope="col">Total</th>
          <th scope="col">Numero de gastos</th>
        </tr>
      </thead>
      <tbody>
        {datos.map((fila) => (
          <tr key={fila.category}>
            <th scope="row">{ETIQUETA_DE_CATEGORIA[fila.category]}</th>
            <td>{formatearImporte(fila.total, moneda)}</td>
            <td>{fila.count}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
