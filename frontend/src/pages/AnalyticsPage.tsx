import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { expensesApi } from '../api/client'
import { formatCurrency } from '../lib/utils'
import {
  PieChart as RechartsPie,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
} from 'recharts'
import { Loader2, TrendingUp, PieChart as PieIcon, DollarSign, Wallet } from 'lucide-react'

const COLORS = [
  '#059669', // Emerald
  '#2563eb', // Blue
  '#d97706', // Amber
  '#7c3aed', // Purple
  '#db2777', // Pink
  '#0891b2', // Cyan
  '#ea580c', // Orange
  '#4f46e5', // Indigo
  '#64748b', // Slate
]

export const AnalyticsPage: React.FC = () => {
  const [selectedCurrency, setSelectedCurrency] = useState<string>('')

  const { data: analytics, isLoading } = useQuery({
    queryKey: ['personalAnalytics'],
    queryFn: async () => {
      const res = await expensesApi.getPersonalAnalytics()
      return res.data.data
    },
  })

  if (isLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    )
  }

  const currencies = analytics?.currencies || []
  const activeCurrency = currencies.find((c) => c.currency === selectedCurrency) || currencies[0]
  const currentCurrency = activeCurrency?.currency || 'USD'

  const categoryData =
    activeCurrency?.categoryBreakdown?.map((cat) => ({
      name: cat.categoryDisplayName || cat.category,
      value: cat.totalAmount,
      percentage: cat.percentage,
    })) || []

  const monthlyData =
    activeCurrency?.monthlyBreakdown?.map((m) => ({
      month: m.month,
      amount: m.totalAmount,
      count: m.count,
    })) || []

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          Spending Analytics
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Visualize where your money goes with detailed category distributions and monthly spending trends per currency.
        </p>
      </div>

      {/* Currency Switcher Tabs */}
      {currencies.length > 1 && (
        <div className="mt-6 flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 mr-1 flex items-center gap-1.5">
            <Wallet className="h-3.5 w-3.5" />
            Currencies:
          </span>
          {currencies.map((c) => {
            const isSelected = c.currency === currentCurrency
            return (
              <button
                key={c.currency}
                onClick={() => setSelectedCurrency(c.currency)}
                className={`inline-flex items-center gap-2 rounded-xl px-3.5 py-2 text-xs font-semibold transition cursor-pointer ${
                  isSelected
                    ? 'bg-emerald-600 text-white shadow-sm'
                    : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
                }`}
              >
                <span>{c.currency}</span>
                <span className={isSelected ? 'text-emerald-100 font-normal' : 'text-slate-400 font-normal'}>
                  ({formatCurrency(c.totalSpent, c.currency)})
                </span>
              </button>
            )
          })}
        </div>
      )}

      {/* Metric Cards */}
      <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Total Spending ({currentCurrency})
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
              <DollarSign className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">
            {formatCurrency(activeCurrency?.totalSpent || 0, currentCurrency)}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            Across {activeCurrency?.totalExpenseCount || 0} expenses
            {analytics?.totalExpenseCount && analytics.totalExpenseCount > (activeCurrency?.totalExpenseCount || 0)
              ? ` (${analytics.totalExpenseCount} total across all currencies)`
              : ''}
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Top Category
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
              <PieIcon className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-2 text-xl font-bold text-slate-900">
            {categoryData[0]?.name || 'N/A'}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            {categoryData[0] ? `${categoryData[0].percentage}% of ${currentCurrency} spend` : 'No expenses logged'}
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Active Months
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-50 text-purple-600">
              <TrendingUp className="h-4 w-4" />
            </div>
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">
            {monthlyData.length}
          </p>
          <p className="mt-1 text-xs text-slate-400">Months of tracked financial history</p>
        </div>
      </div>

      {/* Visual Charts */}
      <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Category Breakdown Pie Chart */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
          <h3 className="text-base font-bold text-slate-900">Category Breakdown ({currentCurrency})</h3>
          <p className="text-xs text-slate-500">Distribution of spending by expense categories</p>

          {categoryData.length > 0 ? (
            <div className="mt-6 flex flex-col items-center">
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <RechartsPie>
                    <Pie
                      data={categoryData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      outerRadius={80}
                      innerRadius={50}
                      paddingAngle={4}
                    >
                      {categoryData.map((_, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(val: any) => [
                        formatCurrency(Number(val) || 0, currentCurrency),
                        'Amount',
                      ]}
                    />
                  </RechartsPie>
                </ResponsiveContainer>
              </div>

              {/* Custom Legend */}
              <div className="mt-4 grid grid-cols-2 gap-2 w-full text-xs">
                {categoryData.map((cat, index) => (
                  <div key={cat.name} className="flex items-center gap-2">
                    <div
                      className="h-3 w-3 rounded-full shrink-0"
                      style={{ backgroundColor: COLORS[index % COLORS.length] }}
                    />
                    <span className="truncate text-slate-700">{cat.name}</span>
                    <span className="font-semibold text-slate-900 ml-auto">{cat.percentage}%</span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="py-16 text-center text-sm text-slate-400">
              No category data available yet.
            </div>
          )}
        </div>

        {/* Monthly Trend Bar Chart */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
          <h3 className="text-base font-bold text-slate-900">Monthly Spending Trend ({currentCurrency})</h3>
          <p className="text-xs text-slate-500">Total expenditure over previous calendar months</p>

          {monthlyData.length > 0 ? (
            <div className="mt-6 h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={monthlyData}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                  <XAxis dataKey="month" tickLine={false} axisLine={false} tick={{ fontSize: 12 }} />
                  <YAxis
                    tickLine={false}
                    axisLine={false}
                    tick={{ fontSize: 12 }}
                  />
                  <Tooltip
                    formatter={(val: any) => [
                      formatCurrency(Number(val) || 0, currentCurrency),
                      'Spend',
                    ]}
                  />
                  <Bar dataKey="amount" fill="#059669" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="py-16 text-center text-sm text-slate-400">
              No monthly trend data available yet.
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
