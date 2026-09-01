import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import Button from '../../components/Button'
import Input from '../../components/Input'
import Modal from '../../components/Modal'
import Select from '../../components/Select'
import { aplicarErrorDeApi } from '../../utils/formularios'
import { hoyISO, parsearImporte } from '../../utils/dinero'
import { CATEGORIAS, type GroupMember } from '../../types/api'
import { ETIQUETA_DE_CATEGORIA } from './categorias'
import { useCrearGasto } from './hooks'

/**
 * El importe se valida como **texto**, no con `z.number()`.
 *
 * Asi se acepta la coma decimal —lo natural al escribir en espanol— y se
 * rechazan los importes con mas de dos decimales en vez de redondearlos en
 * silencio. Redondear un `10,555` a `10,56` cambia lo que el usuario escribio
 * sin decirselo, y en una aplicacion de dinero eso no es una comodidad.
 */
const esquema = z.object({
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
  participantes: z
    .array(z.number())
    .min(1, 'Elige al menos una persona entre las que repartir'),
})

type Datos = z.infer<typeof esquema>

const CAMPOS = ['description', 'amount', 'expenseDate', 'category'] as const

interface Props {
  abierto: boolean
  onCerrar: () => void
  groupId: number
  miembros: GroupMember[]
}

export default function CreateExpenseModal({ abierto, onCerrar, groupId, miembros }: Props) {
  const crear = useCrearGasto(groupId)
  const todos = miembros.map((m) => m.userId)

  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({
    resolver: zodResolver(esquema),
    defaultValues: {
      description: '',
      amount: '',
      expenseDate: hoyISO(),
      category: 'OTROS',
      // Por defecto se reparte entre todos, que es el caso habitual.
      participantes: todos,
    },
  })

  const participantes = watch('participantes')

  useEffect(() => {
    if (!abierto) {
      reset({
        description: '',
        amount: '',
        expenseDate: hoyISO(),
        category: 'OTROS',
        participantes: miembros.map((m) => m.userId),
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto])

  const alternar = (userId: number) => {
    setValue(
      'participantes',
      participantes.includes(userId)
        ? participantes.filter((id) => id !== userId)
        : [...participantes, userId],
      { shouldValidate: true },
    )
  }

  const enviar = handleSubmit(async (datos) => {
    const importe = parsearImporte(datos.amount)
    if (importe === null) return

    try {
      await crear.mutateAsync({
        description: datos.description,
        amount: importe,
        expenseDate: datos.expenseDate,
        category: datos.category,
        // EQUAL con la lista de participantes: el backend reparte por mayor
        // residuo y garantiza que las partes suman exactamente el importe.
        splitType: 'EQUAL',
        splitBetweenUserIds: datos.participantes,
      })
      onCerrar()
    } catch (error) {
      aplicarErrorDeApi(error, setError, CAMPOS)
    }
  })

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Nuevo gasto"
      pie={
        <>
          <Button variante="secundario" onClick={onCerrar} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button type="submit" form="form-gasto" cargando={isSubmitting}>
            {isSubmitting ? 'Guardando...' : 'Anadir gasto'}
          </Button>
        </>
      }
    >
      <form id="form-gasto" onSubmit={enviar} className="space-y-4" noValidate>
        {errors.root && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {errors.root.message}
          </p>
        )}

        <Input
          etiqueta="Descripcion"
          autoFocus
          placeholder="Cena del sabado"
          error={errors.description?.message}
          {...register('description')}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            etiqueta="Importe"
            /* type text con inputMode decimal: abre el teclado numerico en
               movil pero deja escribir la coma, que un type=number rechaza
               segun el locale del navegador. */
            type="text"
            inputMode="decimal"
            placeholder="12,50"
            error={errors.amount?.message}
            {...register('amount')}
          />

          <Input
            etiqueta="Fecha"
            type="date"
            error={errors.expenseDate?.message}
            {...register('expenseDate')}
          />
        </div>

        <Select etiqueta="Categoria" error={errors.category?.message} {...register('category')}>
          {CATEGORIAS.map((categoria) => (
            <option key={categoria} value={categoria}>
              {ETIQUETA_DE_CATEGORIA[categoria]}
            </option>
          ))}
        </Select>

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Repartir entre</legend>
          <div className="mt-2 space-y-1.5">
            {miembros.map((miembro) => (
              <label
                key={miembro.userId}
                className="flex cursor-pointer items-center gap-2 text-sm text-slate-700"
              >
                <input
                  type="checkbox"
                  checked={participantes.includes(miembro.userId)}
                  onChange={() => alternar(miembro.userId)}
                  className="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900"
                />
                {miembro.name}
              </label>
            ))}
          </div>
          {errors.participantes && (
            <p role="alert" className="mt-1 text-sm text-red-600">
              {errors.participantes.message}
            </p>
          )}
          <p className="mt-2 text-xs text-slate-500">
            Se reparte a partes iguales. Los repartos personalizados llegan en la siguiente
            entrega.
          </p>
        </fieldset>
      </form>
    </Modal>
  )
}
