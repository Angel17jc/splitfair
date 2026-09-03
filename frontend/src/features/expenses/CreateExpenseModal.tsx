import { useEffect, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import Button from '../../components/Button'
import Input from '../../components/Input'
import Modal from '../../components/Modal'
import Select from '../../components/Select'
import { aplicarErrorDeApi } from '../../utils/formularios'
import { hoyISO, parsearImporte } from '../../utils/dinero'
import { CATEGORIAS, TIPOS_DE_REPARTO, type GroupMember, type SplitInput } from '../../types/api'
import { ETIQUETA_DE_CATEGORIA } from './categorias'
import { aCentimos, comprobarCuadre, MODOS_DE_REPARTO } from './reparto'
import SplitEditor from './SplitEditor'
import { useCrearGasto } from './hooks'

const esquema = z
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

  const porDefecto = useMemo(
    () => ({
      description: '',
      amount: '',
      expenseDate: hoyISO(),
      category: 'OTROS' as const,
      splitType: 'EQUAL' as const,
      // Por defecto se reparte entre todos, que es el caso habitual.
      participantes: miembros.map((m) => m.userId),
      valores: {} as Record<string, string>,
    }),
    [miembros],
  )

  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({ resolver: zodResolver(esquema), defaultValues: porDefecto })

  const splitType = watch('splitType')
  const participantes = watch('participantes')
  const valores = watch('valores')
  const amount = watch('amount')

  /** El mismo calculo que valida el envio, para mostrarlo mientras se escribe. */
  const cuadre = useMemo(() => {
    const total = aCentimos(amount) ?? 0
    return comprobarCuadre(
      splitType,
      participantes.map((id) => valores[String(id)] ?? ''),
      total,
    )
  }, [splitType, participantes, valores, amount])

  useEffect(() => {
    if (!abierto) reset(porDefecto)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto])

  const alternar = (userId: number) => {
    const dentro = participantes.includes(userId)
    setValue(
      'participantes',
      dentro ? participantes.filter((id) => id !== userId) : [...participantes, userId],
      { shouldValidate: true },
    )
    if (dentro) {
      // Se limpia su importe al salir: dejarlo ahi haria que al volver a
      // marcarle reapareciera una cifra que el usuario ya habia descartado.
      const { [String(userId)]: _fuera, ...resto } = valores
      setValue('valores', resto, { shouldValidate: true })
    }
  }

  const enviar = handleSubmit(async (datos) => {
    const importe = parsearImporte(datos.amount)
    if (importe === null) return

    // Para EQUAL basta la lista de participantes; el backend reparte por mayor
    // residuo. Para los demas modos se envia el valor de cada uno, y es el
    // backend quien lo convierte en importes: aqui no se calcula dinero.
    const splits: SplitInput[] | undefined =
      datos.splitType === 'EQUAL'
        ? undefined
        : datos.participantes.map((userId) => ({
            userId,
            value: Number(String(datos.valores[String(userId)]).replace(',', '.')),
          }))

    try {
      await crear.mutateAsync({
        description: datos.description,
        amount: importe,
        expenseDate: datos.expenseDate,
        category: datos.category,
        splitType: datos.splitType,
        ...(splits ? { splits } : { splitBetweenUserIds: datos.participantes }),
      })
      onCerrar()
    } catch (error) {
      aplicarErrorDeApi(error, setError, CAMPOS)
    }
  })

  /*
    El error de `valores` lo pone superRefine sobre un campo que es un mapa, y
    react-hook-form lo tipa como si pudiera anidar errores por clave. Se
    extrae el texto con una comprobacion explicita en vez de forzar el tipo:
    un cast aqui compilaria igual y se rompe en silencio el dia que el
    error deje de ser plano.
  */
  const errorDeValores = errors.valores?.message
  const mensajeDelReparto =
    errors.participantes?.message ??
    (typeof errorDeValores === 'string' ? errorDeValores : undefined)

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

        <div className="grid gap-4 sm:grid-cols-2">
          <Select etiqueta="Categoria" error={errors.category?.message} {...register('category')}>
            {CATEGORIAS.map((categoria) => (
              <option key={categoria} value={categoria}>
                {ETIQUETA_DE_CATEGORIA[categoria]}
              </option>
            ))}
          </Select>

          <Select etiqueta="Como se reparte" {...register('splitType')}>
            {TIPOS_DE_REPARTO.map((tipo) => (
              <option key={tipo} value={tipo}>
                {MODOS_DE_REPARTO[tipo].etiqueta}
              </option>
            ))}
          </Select>
        </div>

        <SplitEditor
          tipo={splitType}
          miembros={miembros}
          participantes={participantes}
          valores={valores}
          cuadre={cuadre}
          onAlternar={alternar}
          onValor={(userId, valor) =>
            setValue('valores', { ...valores, [String(userId)]: valor }, { shouldValidate: true })
          }
        />

        {mensajeDelReparto && (
          <p role="alert" className="text-sm text-red-600">
            {mensajeDelReparto}
          </p>
        )}
      </form>
    </Modal>
  )
}
