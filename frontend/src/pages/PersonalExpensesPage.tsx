import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { expensesApi } from '../api/client'
import { formatCurrency, formatDate } from '../lib/utils'
import { CategoryBadge } from '../components/expenses/CategoryBadge'
import { CategoryPicker } from '../components/expenses/CategoryPicker'
import type { ExpenseCategory } from '../types/api'
import {
  PieChart as RechartsPie,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import {
  CreditCard,
  Plus,
  Loader2,
  Calendar,
  DollarSign,
  Filter,
  Download,
  PieChart,
} from 'lucide-react'

const CHART_COLORS = [
  '#059669',
  '#2563eb',
  '#d97706',
  '#7c3aed',
  '#db2777',
  '#0891b2',
  '#ea580c',
  '#4f46e5',
  '#64748b',
]

export const PersonalExpensesPage: React.FC = () => {
  const queryClient = useQueryClient()
  const [showAddModal, setShowAddModal] = useState(false)
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [category, setCategory] = useState<ExpenseCategory>('FOOD_AND_DINING')
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState<string>('')
  const [startDate, setStartDate] = useState<string>('')
  const [endDate, setEndDate] = useState<string>('')

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
    queryKey: ['personalExpenses', selectedCategoryFilter, startDate, endDate],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (selectedCategoryFilter) params.category = selectedCategoryFilter
      if (startDate) params.startDate = startDate
      if (endDate) params.endDate = endDate
      const res = await expensesApi.listPersonal(params)
      return res.data.data
    },
  })

  // Personal analytics
  const { data: analytics } = useQuery({
    queryKey: ['personalAnalytics', startDate, endDate],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (startDate) params.startDate = startDate
      if (endDate) params.endDate = endDate
      const res = await expensesApi.getPersonalAnalytics(params)
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

  const categoryChartData =
    analytics?.categoryBreakdown?.map((cat) => ({
      name: cat.categoryDisplayName || cat.category,
      value: cat.totalAmount,
      percentage: cat.percentage,
    })) || []

  // CSV Exporter for personal expenses
  const handleExportCsv = () => {
    if (!expenses || expenses.length === 0) return
    const headers = ['Date', 'Description', 'Category', 'Amount', 'Currency']
    const rows = expenses.map((e) => [
      `"${formatDate(e.createdAt)}"`,
      `"${e.description.replace(/"/g, '""')}"`,
      `"${e.categoryDisplayName || e.category}"`,
      e.amount.toFixed(2),
      `"${e.currency}"`,
    ])
    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `personal_expenses_${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

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

        <div className="flex items-center gap-2.5">
          <button
            onClick={handleExportCsv}
            disabled={!expenses || expenses.length === 0}
            className="inline-flex min-h-[44px] items-center gap-2 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 disabled:opacity-50 transition cursor-pointer"
          >
            <Download className="h-4 w-4 text-slate-500" />
            <span>Export CSV</span>
          </button>
          <button
            onClick={() => setShowAddModal(true)}
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 transition cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            <span>Add Expense</span>
          </button>
        </div>
      </div>

      {/* Analytics Highlights */}
      <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
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

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Top Category
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
              <PieChart className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-2 text-xl font-bold text-slate-900">
            {categoryChartData[0]?.name || 'N/A'}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            {categoryChartData[0] ? `${categoryChartData[0].percentage}% of your spend` : 'No expenses'}
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Average Expense
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-50 text-purple-600">
              <CreditCard className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">
            {expenses && expenses.length > 0
              ? formatCurrency(totalSpent / expenses.length, currency)
              : '$0.00'}
          </p>
          <p className="mt-1 text-xs text-slate-400">Average transaction size</p>
        </div>
      </div>

      {/* Date Filter & Category Filter Bar */}
      <div className="mt-8 flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2">
            <Filter className="h-4 w-4 text-slate-400" />
            <select
              value={selectedCategoryFilter}
              onChange={(e) => setSelectedCategoryFilter(e.target.value)}
              className="rounded-xl border border-slate-200 bg-slate-50/50 px-3 py-1.5 text-xs font-semibold text-slate-700"
            >
              <option value="">All Categories</option>
              {categories?.map((cat) => (
                <option key={cat.code} value={cat.code}>
                  {cat.displayName}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-2 text-xs text-slate-500">
            <Calendar className="h-4 w-4 text-slate-400" />
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="rounded-xl border border-slate-200 bg-slate-50/50 px-2.5 py-1 text-xs text-slate-700"
            />
            <span>to</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="rounded-xl border border-slate-200 bg-slate-50/50 px-2.5 py-1 text-xs text-slate-700"
            />
          </div>
        </div>

        {(selectedCategoryFilter || startDate || endDate) && (
          <button
            onClick={() => {
              setSelectedCategoryFilter('')
              setStartDate('')
              setEndDate('')
            }}
            className="text-xs font-semibold text-emerald-600 hover:text-emerald-700"
          >
            Clear Filters
          </button>
        )}
      </div>

      {/* Split Grid: Expenses List & Donut Chart */}
      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Expenses List */}
        <div className="lg:col-span-2">
          {isLoading ? (
            <div className="py-12 text-center">
              <Loader2 className="mx-auto h-6 w-6 animate-spin text-emerald-600" />
            </div>
          ) : expenses && expenses.length > 0 ? (
            <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs">
              {expenses.map((exp) => (
                <div key={exp.id} className="flex items-center justify-between p-4 hover:bg-slate-50/50 transition">
                  <div className="flex items-start gap-3">
                    <div className="mt-0.5">
                      <CategoryBadge category={exp.category} />
                    </div>
                    <div>
                      <p className="font-semibold text-slate-900">{exp.description}</p>
                      <p className="text-xs text-slate-400">{formatDate(exp.createdAt)}</p>
                    </div>
                  </div>
                  <p className="font-bold text-slate-900">
                    {formatCurrency(exp.amount, exp.currency)}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <div className="rounded-2xl border-2 border-dashed border-slate-200 bg-white p-12 text-center text-sm text-slate-500">
              No personal expenses recorded.
            </div>
          )}
        </div>

        {/* Category Breakdown Donut */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <h3 className="text-sm font-bold text-slate-900">Spending by Category</h3>
          <p className="text-[11px] text-slate-500">Visual share of your budget</p>

          {categoryChartData.length > 0 ? (
            <div className="mt-4 flex flex-col items-center">
              <div className="h-48 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <RechartsPie>
                    <Pie
                      data={categoryChartData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      outerRadius={65}
                      innerRadius={40}
                      paddingAngle={3}
                    >
                      {categoryChartData.map((_, index) => (
                        <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(val: any) => [
                        formatCurrency(Number(val) || 0, currency),
                        'Amount',
                      ]}
                    />
                  </RechartsPie>
                </ResponsiveContainer>
              </div>

              <div className="mt-3 w-full space-y-1.5 text-xs">
                {categoryChartData.map((cat, idx) => (
                  <div key={cat.name} className="flex items-center justify-between">
                    <div className="flex items-center gap-2 truncate">
                      <div
                        className="h-2.5 w-2.5 rounded-full shrink-0"
                        style={{ backgroundColor: CHART_COLORS[idx % CHART_COLORS.length] }}
                      />
                      <span className="truncate text-slate-700">{cat.name}</span>
                    </div>
                    <span className="font-semibold text-slate-900">{cat.percentage}%</span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="py-12 text-center text-xs text-slate-400">
              No category data available.
            </div>
          )}
        </div>
      </div>

      {/* Add Expense Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
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
                    min="0.01"
                    required
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0.00"
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-emerald-500"
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
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-2">
                  Category
                </label>
                <CategoryPicker selected={category} onSelect={setCategory} />
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
