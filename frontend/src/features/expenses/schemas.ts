import { z } from 'zod'
import { parsearImporte } from '../../utils/dinero'
import { CATEGORIAS, TIPOS_DE_REPARTO } from '../../types/api'
import { aCentimos, comprobarCuadre } from './reparto'

/**
 * Validacion del formulario de alta y edicion de gastos.
 *
 * Vive fuera del componente para poder probarla sin montarlo: el formulario
 * es el unico sitio donde se decide si un reparto se puede enviar, y esa
 * decision merece tests propios, no solo los que pasen por la interfaz.
 */
export const esquemaDeGasto = z
  .object({
    description: z
      .string()
      .trim()
      .min(1, 'Describe el gasto')
      .max(255, 'La descripcion es demasiado larga'),
    amount: z
      .string()
      .min(1, 'Indica el importe')
      .refine((texto) => parsearImporte(texto) !== null, {
        message: 'Importe no valido. Usa como maximo dos decimales, por ejemplo 12,50',
      }),
    expenseDate: z.string().min(1, 'Indica la fecha'),
    category: z.enum(CATEGORIAS),
    splitType: z.enum(TIPOS_DE_REPARTO),
    participantes: z
      .array(z.number())
      .min(1, 'Elige al menos una persona entre las que repartir'),
    valores: z.record(z.string()),
  })
  /**
   * El cuadre depende de tres campos a la vez —modo, importe y valores— asi
   * que no puede validarse campo a campo. Se comprueba aqui, sobre el objeto
   * completo, y con la misma funcion que alimenta el indicador en vivo: una
   * sola definicion de "cuadra", en vez de dos que pueden discrepar.
   */
  .superRefine((datos, ctx) => {
    if (datos.splitType === 'EQUAL') return

    const total = aCentimos(datos.amount)
    if (total === null) return // ya hay un error en el importe

    const valores = datos.participantes.map((id) => datos.valores[String(id)] ?? '')
    const cuadre = comprobarCuadre(datos.splitType, valores, total)

    if (!cuadre.valido) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['valores'], message: cuadre.mensaje })
    }
  })

export type DatosDeGasto = z.infer<typeof esquemaDeGasto>
