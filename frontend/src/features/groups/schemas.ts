import { z } from 'zod'

/** Validacion del formulario de alta de grupos. Espejo de `CreateGroupRequest`. */
export const esquemaDeGrupo = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'El nombre es obligatorio')
    .max(100, 'El nombre no puede pasar de 100 caracteres'),
  description: z.string().trim().max(255, 'La descripcion es demasiado larga').optional(),
  currency: z.string().length(3),
})

export type DatosDeGrupo = z.infer<typeof esquemaDeGrupo>
