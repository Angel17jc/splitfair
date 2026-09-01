/** Gastos de un grupo. */

import { apiClient } from './client'
import type { Expense, ExpenseFilters, ExpenseInput, PageParams, Paged } from '../types/api'

export async function listarGastos(
  groupId: number,
  filtros: ExpenseFilters & PageParams = {},
): Promise<Paged<Expense>> {
  const { data } = await apiClient.get<Paged<Expense>>(`/groups/${groupId}/expenses`, {
    params: filtros,
  })
  return data
}

export async function crearGasto(groupId: number, datos: ExpenseInput): Promise<Expense> {
  const { data } = await apiClient.post<Expense>(`/groups/${groupId}/expenses`, datos)
  return data
}

/** Solo el pagador o un ADMIN. */
export async function actualizarGasto(
  expenseId: number,
  datos: ExpenseInput,
): Promise<Expense> {
  const { data } = await apiClient.put<Expense>(`/expenses/${expenseId}`, datos)
  return data
}

export async function borrarGasto(expenseId: number): Promise<void> {
  await apiClient.delete(`/expenses/${expenseId}`)
}
