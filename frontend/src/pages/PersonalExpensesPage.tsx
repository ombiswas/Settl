import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { expensesApi } from '../api/client'
import { formatCurrency, formatDate } from '../lib/utils'
import type { ExpenseCategory } from '../types/api'
import {
  CreditCard,
  Plus,
  Loader2,
  Calendar,
  Tag,
  DollarSign,
  Receipt,
  Filter,
} from 'lucide-react'

export const PersonalExpensesPage: React.FC = () => {
  const queryClient = useQueryClient()
  const [showAddModal, setShowAddModal] = useState(false)
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [category, setCategory] = useState<ExpenseCategory>('FOOD_AND_DINING')
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState<string>('')

  // Categories list
  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const res = await expensesApi.getCategories()
      return res.data.data
    },
  })

  // Personal expenses list
  const { data: expenses, isLoading } = useQuery({
    queryKey: ['personalExpenses', selectedCategoryFilter],
    queryFn: async () => {
      const res = await expensesApi.listPersonal(
        selectedCategoryFilter ? { category: selectedCategoryFilter } : undefined
      )
      return res.data.data
    },
  })

  // Create Personal Expense Mutation
  const createMutation = useMutation({
    mutationFn: async () => {
      await expensesApi.createPersonal({
        description,
        amount: parseFloat(amount),
        currency,
        category,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['personalExpenses'] })
      queryClient.invalidateQueries({ queryKey: ['personalAnalytics'] })
      setShowAddModal(false)
      setDescription('')
      setAmount('')
      setCategory('FOOD_AND_DINING')
    },
  })

  const totalSpent = expenses?.reduce((sum, e) => sum + e.amount, 0) || 0

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Individual Expenses
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Track and categorize your private daily spending outside of shared groups.
          </p>
        </div>

        <button
          onClick={() => setShowAddModal(true)}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700 transition"
        >
          <Plus className="h-4 w-4" />
          <span>Add Expense</span>
        </button>
      </div>

      {/* Summary Card */}
      <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Total Logged Spend
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
              <DollarSign className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">
            {formatCurrency(totalSpent, currency)}
          </p>
          <p className="mt-1 text-xs text-slate-400">Across {expenses?.length || 0} transactions</p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="mt-8 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Filter className="h-4 w-4 text-slate-400" />
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Filter:</span>
          <select
            value={selectedCategoryFilter}
            onChange={(e) => setSelectedCategoryFilter(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700"
          >
            <option value="">All Categories</option>
            {categories?.map((cat) => (
              <option key={cat.code} value={cat.code}>
                {cat.displayName}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Expenses List */}
      {isLoading ? (
        <div className="py-12 text-center">
          <Loader2 className="mx-auto h-6 w-6 animate-spin text-emerald-600" />
        </div>
      ) : expenses && expenses.length > 0 ? (
        <div className="mt-4 divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
          {expenses.map((exp) => (
            <div key={exp.id} className="flex items-center justify-between p-4 hover:bg-slate-50/50 transition">
              <div>
                <p className="font-semibold text-slate-900">{exp.description}</p>
                <div className="mt-1 flex items-center gap-3 text-xs text-slate-500">
                  <span className="inline-flex items-center gap-1">
                    <Calendar className="h-3.5 w-3.5 text-slate-400" />
                    {formatDate(exp.createdAt)}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Tag className="h-3.5 w-3.5 text-slate-400" />
                    {exp.categoryDisplayName || exp.category}
                  </span>
                </div>
              </div>
              <div className="text-right">
                <p className="font-bold text-slate-900">
                  {formatCurrency(exp.amount, exp.currency)}
                </p>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-6 rounded-2xl border-2 border-dashed border-slate-200 p-10 text-center text-sm text-slate-500">
          No personal expenses logged yet.
        </div>
      )}

      {/* Add Expense Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl">
            <h2 className="text-xl font-bold text-slate-900">Add Personal Expense</h2>
            <p className="mt-1 text-xs text-slate-500">
              Log an individual purchase for categorization and budget tracking.
            </p>

            <form
              onSubmit={(e) => {
                e.preventDefault()
                createMutation.mutate()
              }}
              className="mt-5 space-y-4"
            >
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Description
                </label>
                <input
                  type="text"
                  required
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="e.g. Grocery store, Uber ride, Coffee"
                  className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                    Amount
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0.00"
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                    Currency
                  </label>
                  <select
                    value={currency}
                    onChange={(e) => setCurrency(e.target.value)}
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white"
                  >
                    <option value="USD">USD ($)</option>
                    <option value="EUR">EUR (€)</option>
                    <option value="GBP">GBP (£)</option>
                    <option value="INR">INR (₹)</option>
                    <option value="CAD">CAD ($)</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Category
                </label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value as ExpenseCategory)}
                  className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white"
                >
                  {categories?.map((cat) => (
                    <option key={cat.code} value={cat.code}>
                      {cat.displayName}
                    </option>
                  ))}
                </select>
              </div>

              {createMutation.isError && (
                <p className="text-xs text-red-600">Failed to save expense. Please verify details.</p>
              )}

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || !description.trim() || !amount}
                  className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition"
                >
                  {createMutation.isPending ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span>Saving...</span>
                    </>
                  ) : (
                    <span>Save Expense</span>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
