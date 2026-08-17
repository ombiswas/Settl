import React, { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  groupsApi,
  expensesApi,
  balancesApi,
  settlementsApi,
  recurringApi,
  activityApi,
} from '../api/client'
import { useAuthStore } from '../store/authStore'
import { formatCurrency, formatDate } from '../lib/utils'
import { exportExpensesToCsv, exportSettlementsToCsv, printGroupSummary } from '../lib/exportUtils'
import { CategoryBadge } from '../components/expenses/CategoryBadge'
import { CreateExpenseModal } from '../components/expenses/CreateExpenseModal'
import { CreateRecurringExpenseModal } from '../components/recurring/CreateRecurringExpenseModal'
import { RecordSettlementModal } from '../components/settlements/RecordSettlementModal'
import { BalanceGraph } from '../components/balances/BalanceGraph'
import type {
  CreateExpenseRequest,
  CreateRecurringExpenseRequest,
  CreateSettlementRequest,
} from '../types/api'
import {
  ArrowLeft,
  Users,
  Plus,
  DollarSign,
  Receipt,
  UserPlus,
  Loader2,
  CheckCircle,
  Repeat,
  History,
  Trash2,
  Filter,
  Search,
  Download,
  Printer,
  Ban,
  Zap,
} from 'lucide-react'

export const GroupDetailPage: React.FC = () => {
  const { groupId } = useParams<{ groupId: string }>()
  const queryClient = useQueryClient()
  const { user } = useAuthStore()

  const [activeTab, setActiveTab] = useState<'expenses' | 'balances' | 'settlements' | 'recurring' | 'activity'>('expenses')
  const [showAddExpenseModal, setShowAddExpenseModal] = useState(false)
  const [showAddRecurringModal, setShowAddRecurringModal] = useState(false)
  const [showRecordSettlementModal, setShowRecordSettlementModal] = useState(false)
  const [prefilledSettlement, setPrefilledSettlement] = useState<{ toUserId?: string; amount?: number }>({})

  const [showAddMember, setShowAddMember] = useState(false)
  const [memberEmail, setMemberEmail] = useState('')
  const [memberIsAdmin, setMemberIsAdmin] = useState(false)

  // Search & Filter for expenses
  const [expenseSearch, setExpenseSearch] = useState('')
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState<string>('')

  // Group Details
  const { data: group, isLoading: groupLoading } = useQuery({
    queryKey: ['group', groupId],
    queryFn: async () => {
      if (!groupId) throw new Error('Missing groupId')
      const res = await groupsApi.get(groupId)
      return res.data.data
    },
    enabled: !!groupId,
  })

  // Expenses
  const { data: expenses, isLoading: expensesLoading } = useQuery({
    queryKey: ['expenses', groupId],
    queryFn: async () => {
      if (!groupId) return []
      const res = await expensesApi.listGroup(groupId)
      return res.data.data
    },
    enabled: !!groupId,
  })

  // Balances
  const { data: balancesData } = useQuery({
    queryKey: ['balances', groupId],
    queryFn: async () => {
      if (!groupId) return null
      const res = await balancesApi.getGroupBalances(groupId)
      return res.data.data
    },
    enabled: !!groupId && activeTab === 'balances',
  })

  // Suggested Settlements
  const { data: suggestedData } = useQuery({
    queryKey: ['suggestedSettlements', groupId],
    queryFn: async () => {
      if (!groupId) return null
      const res = await balancesApi.getSuggestedSettlements(groupId)
      return res.data.data
    },
    enabled: !!groupId && (activeTab === 'balances' || activeTab === 'settlements'),
  })

  // Settlements History
  const { data: settlements } = useQuery({
    queryKey: ['settlements', groupId],
    queryFn: async () => {
      if (!groupId) return []
      const res = await settlementsApi.list(groupId)
      return res.data.data
    },
    enabled: !!groupId && activeTab === 'settlements',
  })

  // Recurring Expenses
  const { data: recurringList } = useQuery({
    queryKey: ['recurring', groupId],
    queryFn: async () => {
      if (!groupId) return []
      const res = await recurringApi.list(groupId)
      return res.data.data
    },
    enabled: !!groupId && activeTab === 'recurring',
  })

  // Activity Feed
  const { data: activityData } = useQuery({
    queryKey: ['activity', groupId],
    queryFn: async () => {
      if (!groupId) return null
      const res = await activityApi.list(groupId)
      return res.data.data
    },
    enabled: !!groupId && activeTab === 'activity',
  })

  // Create Expense Mutation
  const createExpenseMutation = useMutation({
    mutationFn: async (data: CreateExpenseRequest) => {
      if (!groupId) return
      await expensesApi.createGroup(groupId, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses', groupId] })
      queryClient.invalidateQueries({ queryKey: ['balances', groupId] })
      queryClient.invalidateQueries({ queryKey: ['suggestedSettlements', groupId] })
      queryClient.invalidateQueries({ queryKey: ['activity', groupId] })
    },
  })

  // Delete Expense Mutation
  const deleteExpenseMutation = useMutation({
    mutationFn: async (expenseId: string) => {
      if (!groupId) return
      await expensesApi.deleteGroup(groupId, expenseId)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses', groupId] })
      queryClient.invalidateQueries({ queryKey: ['balances', groupId] })
      queryClient.invalidateQueries({ queryKey: ['suggestedSettlements', groupId] })
      queryClient.invalidateQueries({ queryKey: ['activity', groupId] })
    },
  })

  // Create Recurring Expense Mutation
  const createRecurringMutation = useMutation({
    mutationFn: async (data: CreateRecurringExpenseRequest) => {
      if (!groupId) return
      await recurringApi.create(groupId, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recurring', groupId] })
      queryClient.invalidateQueries({ queryKey: ['activity', groupId] })
    },
  })

  // Deactivate Recurring Mutation
  const deactivateRecurringMutation = useMutation({
    mutationFn: async (recurringId: string) => {
      if (!groupId) return
      await recurringApi.deactivate(groupId, recurringId)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recurring', groupId] })
      queryClient.invalidateQueries({ queryKey: ['activity', groupId] })
    },
  })

  // Add Member Mutation
  const addMemberMutation = useMutation({
    mutationFn: async () => {
      if (!groupId) return
      await groupsApi.addMember(groupId, { email: memberEmail, isAdmin: memberIsAdmin })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] })
      setShowAddMember(false)
      setMemberEmail('')
      setMemberIsAdmin(false)
    },
  })

  // Record Settlement Mutation
  const recordSettlementMutation = useMutation({
    mutationFn: async (data: CreateSettlementRequest) => {
      if (!groupId) return
      await settlementsApi.record(groupId, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['balances', groupId] })
      queryClient.invalidateQueries({ queryKey: ['suggestedSettlements', groupId] })
      queryClient.invalidateQueries({ queryKey: ['settlements', groupId] })
      queryClient.invalidateQueries({ queryKey: ['activity', groupId] })
    },
  })

  // Filtered expenses
  const filteredExpenses = expenses?.filter((exp) => {
    const matchesSearch =
      exp.description.toLowerCase().includes(expenseSearch.toLowerCase()) ||
      exp.paidByName.toLowerCase().includes(expenseSearch.toLowerCase())
    const matchesCat = selectedCategoryFilter ? exp.category === selectedCategoryFilter : true
    return matchesSearch && matchesCat
  })

  if (groupLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    )
  }

  if (!group) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-12 text-center">
        <p className="text-slate-600">Group not found or you don't have access.</p>
        <Link to="/groups" className="mt-4 inline-block font-medium text-emerald-600">
          Return to groups
        </Link>
      </div>
    )
  }

  const isUserAdmin = group.members?.some((m) => m.userId === user?.id && m.admin)

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Header */}
      <div className="flex flex-col gap-4 border-b border-slate-200 pb-6 md:flex-row md:items-center md:justify-between">
        <div>
          <Link
            to="/groups"
            className="mb-2 inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-emerald-700 transition"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>Back to All Groups</span>
          </Link>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
              {group.name}
            </h1>
            <span className="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700 uppercase">
              {group.defaultCurrency}
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Created on {formatDate(group.createdAt)} • {group.members?.length || 0} members
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {/* Export Dropdown / Buttons */}
          <button
            onClick={() => exportExpensesToCsv(group.name, expenses || [])}
            disabled={!expenses || expenses.length === 0}
            title="Export CSV"
            className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 disabled:opacity-50 transition cursor-pointer"
          >
            <Download className="h-4 w-4 text-slate-500" />
            <span className="hidden sm:inline">CSV</span>
          </button>
          <button
            onClick={() => printGroupSummary(group.name, group.defaultCurrency, expenses || [])}
            disabled={!expenses || expenses.length === 0}
            title="Print / PDF Statement"
            className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 disabled:opacity-50 transition cursor-pointer"
          >
            <Printer className="h-4 w-4 text-slate-500" />
            <span className="hidden sm:inline">PDF</span>
          </button>

          <button
            onClick={() => setShowAddMember(true)}
            className="inline-flex min-h-[44px] items-center gap-2 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 transition cursor-pointer"
          >
            <UserPlus className="h-4 w-4 text-slate-500" />
            <span>Invite</span>
          </button>
          <button
            onClick={() => setShowAddExpenseModal(true)}
            className="inline-flex min-h-[44px] items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 transition cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            <span>Add Expense</span>
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="mt-6 flex overflow-x-auto border-b border-slate-200 pb-px">
        {[
          { id: 'expenses', label: 'Expenses', icon: Receipt },
          { id: 'balances', label: 'Balances & Settle Up', icon: DollarSign },
          { id: 'settlements', label: 'Settlement Ledger', icon: CheckCircle },
          { id: 'recurring', label: 'Recurring', icon: Repeat },
          { id: 'activity', label: 'Activity Feed', icon: History },
        ].map((tab) => {
          const Icon = tab.icon
          const isActive = activeTab === tab.id
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as typeof activeTab)}
              className={`flex shrink-0 items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold transition cursor-pointer ${
                isActive
                  ? 'border-emerald-600 text-emerald-700'
                  : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
            >
              <Icon className="h-4 w-4" />
              <span>{tab.label}</span>
            </button>
          )
        })}
      </div>

      {/* Tab Content */}
      <div className="mt-6">
        {/* EXPENSES TAB */}
        {activeTab === 'expenses' && (
          <div>
            {/* Search and Category Filter Bar */}
            <div className="mb-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="relative flex-1 max-w-sm">
                <Search className="absolute inset-y-0 left-3 my-auto h-4 w-4 text-slate-400" />
                <input
                  type="text"
                  value={expenseSearch}
                  onChange={(e) => setExpenseSearch(e.target.value)}
                  placeholder="Search expenses or payer..."
                  className="w-full rounded-xl border border-slate-200 bg-white py-2 pl-9 pr-3 text-xs focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              <div className="flex items-center gap-2">
                <Filter className="h-4 w-4 text-slate-400" />
                <select
                  value={selectedCategoryFilter}
                  onChange={(e) => setSelectedCategoryFilter(e.target.value)}
                  className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-700"
                >
                  <option value="">All Categories</option>
                  <option value="FOOD_AND_DINING">Food & Dining</option>
                  <option value="TRANSPORTATION">Transportation</option>
                  <option value="HOUSING_AND_UTILITIES">Housing & Utilities</option>
                  <option value="ENTERTAINMENT">Entertainment</option>
                  <option value="SHOPPING">Shopping</option>
                  <option value="HEALTHCARE">Healthcare</option>
                  <option value="TRAVEL">Travel</option>
                  <option value="EDUCATION">Education</option>
                  <option value="PERSONAL_CARE">Personal Care</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
            </div>

            {expensesLoading ? (
              <div className="py-12 text-center">
                <Loader2 className="mx-auto h-6 w-6 animate-spin text-emerald-600" />
              </div>
            ) : filteredExpenses && filteredExpenses.length > 0 ? (
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs">
                {filteredExpenses.map((expense) => {
                  const canDelete = expense.paidById === user?.id || isUserAdmin
                  return (
                    <div
                      key={expense.id}
                      className="group flex flex-col sm:flex-row sm:items-center justify-between p-4 gap-3 hover:bg-slate-50/60 transition"
                    >
                      <div className="flex items-start gap-3.5">
                        <div className="mt-0.5">
                          <CategoryBadge category={expense.category} />
                        </div>
                        <div>
                          <h4 className="font-semibold text-slate-900">{expense.description}</h4>
                          <p className="mt-0.5 text-xs text-slate-500">
                            Paid by <span className="font-medium text-slate-700">{expense.paidByName}</span> •{' '}
                            {formatDate(expense.createdAt)} •{' '}
                            <span className="font-medium text-slate-600 lowercase">{expense.splitType} split</span>
                          </p>

                          {/* Member debt chips */}
                          <div className="mt-2 flex flex-wrap gap-1.5">
                            {expense.shares?.map((s) => (
                              <span
                                key={s.userId}
                                className="rounded-md bg-slate-100 px-2 py-0.5 text-[10px] text-slate-600"
                              >
                                {s.userDisplayName}: {formatCurrency(s.amountOwed, expense.currency)}
                              </span>
                            ))}
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center justify-between sm:justify-end gap-4">
                        <div className="text-left sm:text-right">
                          <p className="text-base font-bold text-slate-900">
                            {formatCurrency(expense.amount, expense.currency)}
                          </p>
                        </div>

                        {canDelete && (
                          <button
                            onClick={() => {
                              if (window.confirm(`Delete "${expense.description}"?`)) {
                                deleteExpenseMutation.mutate(expense.id)
                              }
                            }}
                            title="Delete Expense"
                            className="rounded-lg p-2 text-slate-400 opacity-80 hover:bg-red-50 hover:text-red-600 transition cursor-pointer"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              <div className="rounded-2xl border-2 border-dashed border-slate-200 bg-white p-12 text-center">
                <Receipt className="mx-auto h-8 w-8 text-slate-400" />
                <h3 className="mt-3 text-sm font-semibold text-slate-900">No expenses found</h3>
                <p className="mt-1 text-xs text-slate-500">
                  {expenseSearch || selectedCategoryFilter
                    ? 'No expenses matched your current filters.'
                    : 'Start by adding your first group expense.'}
                </p>
              </div>
            )}
          </div>
        )}

        {/* BALANCES TAB */}
        {activeTab === 'balances' && (
          <div className="space-y-8">
            {/* Visual Debt Graph */}
            {balancesData && suggestedData && (
              <BalanceGraph
                balances={balancesData.balances}
                suggestedSettlements={suggestedData.suggestedTransactions}
                currency={group.defaultCurrency}
              />
            )}

            {/* Settle Up Action Card */}
            {suggestedData && suggestedData.suggestedTransactions?.length > 0 && (
              <div className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-emerald-800">
                    <Zap className="h-5 w-5 text-emerald-600" />
                    <h3 className="text-base font-bold">Smart Debt Simplification</h3>
                  </div>
                  <button
                    onClick={() => {
                      setPrefilledSettlement({})
                      setShowRecordSettlementModal(true)
                    }}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-300 bg-white px-3 py-1.5 text-xs font-bold text-emerald-800 hover:bg-emerald-100 transition cursor-pointer"
                  >
                    <span>Custom Repayment</span>
                  </button>
                </div>
                <p className="mt-1 text-xs text-emerald-700">
                  Optimal path to zero balance: {suggestedData.transactionCount} transactions needed.
                </p>

                <div className="mt-4 space-y-2.5">
                  {suggestedData.suggestedTransactions.map((tx, idx) => {
                    const isCurrentUserPayer = user?.id === tx.fromUserId
                    return (
                      <div
                        key={idx}
                        className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-emerald-200/80 bg-white p-3.5 shadow-xs"
                      >
                        <div className="text-sm">
                          <span className="font-bold text-slate-900">{tx.fromUserName}</span> pays{' '}
                          <span className="font-bold text-slate-900">{tx.toUserName}</span>{' '}
                          <span className="font-bold text-emerald-700">
                            {formatCurrency(tx.amount, tx.currency)}
                          </span>
                        </div>

                        {isCurrentUserPayer && (
                          <button
                            onClick={() =>
                              recordSettlementMutation.mutate({
                                toUserId: tx.toUserId,
                                amount: tx.amount,
                                currency: tx.currency,
                                isSimplified: true,
                              })
                            }
                            disabled={recordSettlementMutation.isPending}
                            className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700 transition cursor-pointer"
                          >
                            <CheckCircle className="h-3.5 w-3.5" />
                            <span>Confirm Repayment</span>
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            )}

            {/* Member Net Balances */}
            {balancesData && (
              <div>
                <h3 className="text-base font-bold text-slate-900">Member Net Positions</h3>
                <p className="text-xs text-slate-500 mb-3">
                  Summary of amounts paid vs share owed per group participant.
                </p>

                <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs">
                  {balancesData.balances.map((b) => (
                    <div key={b.userId} className="flex items-center justify-between p-4">
                      <div>
                        <p className="font-semibold text-slate-900">{b.displayName}</p>
                        <p className="text-xs text-slate-500">
                          Paid {formatCurrency(b.totalPaid, balancesData.currency)} • Owes share of{' '}
                          {formatCurrency(b.totalShare, balancesData.currency)}
                        </p>
                      </div>
                      <div className="text-right">
                        <span
                          className={`text-sm font-bold ${
                            b.netBalance > 0.001
                              ? 'text-emerald-600'
                              : b.netBalance < -0.001
                              ? 'text-red-600'
                              : 'text-slate-500'
                          }`}
                        >
                          {b.netBalance > 0 ? '+' : ''}
                          {formatCurrency(b.netBalance, balancesData.currency)}
                        </span>
                        <span
                          className={`ml-2 inline-block rounded-md px-2 py-0.5 text-[10px] font-bold ${
                            b.status === 'IS_OWED'
                              ? 'bg-emerald-50 text-emerald-700'
                              : b.status === 'OWES'
                              ? 'bg-red-50 text-red-700'
                              : 'bg-slate-100 text-slate-600'
                          }`}
                        >
                          {b.status === 'IS_OWED'
                            ? 'Gets Back'
                            : b.status === 'OWES'
                            ? 'Owes'
                            : 'Settled'}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* SETTLEMENTS LEDGER TAB */}
        {activeTab === 'settlements' && (
          <div>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
              <div>
                <h3 className="text-base font-bold text-slate-900">Settlement Ledger</h3>
                <p className="text-xs text-slate-500">
                  Historical log of debt repayments recorded between group members.
                </p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => exportSettlementsToCsv(group.name, settlements || [])}
                  disabled={!settlements || settlements.length === 0}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 transition cursor-pointer"
                >
                  <Download className="h-3.5 w-3.5 text-slate-500" />
                  <span>Export Ledger</span>
                </button>
                <button
                  onClick={() => {
                    setPrefilledSettlement({})
                    setShowRecordSettlementModal(true)
                  }}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700 transition cursor-pointer"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span>Log Settlement</span>
                </button>
              </div>
            </div>

            {settlements && settlements.length > 0 ? (
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs">
                {settlements.map((s) => (
                  <div key={s.id} className="flex items-center justify-between p-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
                        <CheckCircle className="h-5 w-5" />
                      </div>
                      <div>
                        <p className="text-sm font-semibold text-slate-900">
                          <span className="font-bold">{s.fromUserName}</span> paid{' '}
                          <span className="font-bold">{s.toUserName}</span>
                        </p>
                        <p className="text-xs text-slate-500">
                          {formatDate(s.settledAt)} • {s.simplified ? 'Simplified' : 'Direct'}
                        </p>
                      </div>
                    </div>
                    <p className="font-bold text-emerald-700">
                      {formatCurrency(s.amount, s.currency)}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-2xl border-2 border-dashed border-slate-200 bg-white p-12 text-center text-sm text-slate-500">
                No repayments have been recorded yet.
              </div>
            )}
          </div>
        )}

        {/* RECURRING TAB */}
        {activeTab === 'recurring' && (
          <div>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
              <div>
                <h3 className="text-base font-bold text-slate-900">Recurring Expenses</h3>
                <p className="text-xs text-slate-500">
                  Automated templates that trigger on a scheduled interval (rent, bills, utilities).
                </p>
              </div>

              <button
                onClick={() => setShowAddRecurringModal(true)}
                className="inline-flex items-center gap-1.5 rounded-xl bg-purple-600 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-purple-700 transition cursor-pointer"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>New Template</span>
              </button>
            </div>

            {recurringList && recurringList.length > 0 ? (
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs">
                {recurringList.map((r) => {
                  const canDeactivate = r.paidById === user?.id || isUserAdmin
                  return (
                    <div key={r.id} className="flex items-center justify-between p-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="font-semibold text-slate-900">{r.templateDescription}</p>
                          <span
                            className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${
                              r.active
                                ? 'bg-emerald-50 text-emerald-700'
                                : 'bg-slate-100 text-slate-500'
                            }`}
                          >
                            {r.active ? 'Active' : 'Paused'}
                          </span>
                        </div>
                        <p className="text-xs text-slate-500 mt-0.5">
                          {r.frequency} • Paid by {r.paidByName} • Next run:{' '}
                          {formatDate(r.nextRunAt)}
                        </p>
                      </div>
                      <div className="flex items-center gap-4">
                        <p className="font-bold text-slate-900">
                          {formatCurrency(r.amount, r.currency)}
                        </p>
                        {canDeactivate && r.active && (
                          <button
                            onClick={() => {
                              if (window.confirm(`Deactivate recurring "${r.templateDescription}"?`)) {
                                deactivateRecurringMutation.mutate(r.id)
                              }
                            }}
                            title="Pause / Deactivate Template"
                            className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition cursor-pointer"
                          >
                            <Ban className="h-4 w-4" />
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              <div className="rounded-2xl border-2 border-dashed border-slate-200 bg-white p-12 text-center text-sm text-slate-500">
                No recurring expense templates set up.
              </div>
            )}
          </div>
        )}

        {/* ACTIVITY TAB */}
        {activeTab === 'activity' && (
          <div>
            <h3 className="text-base font-bold text-slate-900">Group Activity Feed</h3>
            <p className="text-xs text-slate-500 mb-4">
              Audit log of all expenses, settlements, and member changes.
            </p>

            {activityData && activityData.content?.length > 0 ? (
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs">
                {activityData.content.map((act) => (
                  <div key={act.id} className="p-4">
                    <div className="flex items-center justify-between text-xs text-slate-500">
                      <span className="font-semibold text-slate-800">{act.actorName}</span>
                      <span>{formatDate(act.createdAt)}</span>
                    </div>
                    <p className="mt-1 text-sm font-medium text-slate-900">
                      {act.action.replace(/_/g, ' ')}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-2xl border-2 border-dashed border-slate-200 bg-white p-12 text-center text-sm text-slate-500">
                No activity recorded yet.
              </div>
            )}
          </div>
        )}
      </div>

      {/* Add Expense Modal */}
      <CreateExpenseModal
        isOpen={showAddExpenseModal}
        onClose={() => setShowAddExpenseModal(false)}
        onSubmit={async (data) => {
          await createExpenseMutation.mutateAsync(data)
        }}
        members={group.members || []}
        defaultCurrency={group.defaultCurrency}
        currentUserId={user?.id}
      />

      {/* Add Recurring Modal */}
      <CreateRecurringExpenseModal
        isOpen={showAddRecurringModal}
        onClose={() => setShowAddRecurringModal(false)}
        onSubmit={async (data) => {
          await createRecurringMutation.mutateAsync(data)
        }}
        defaultCurrency={group.defaultCurrency}
      />

      {/* Record Settlement Modal */}
      <RecordSettlementModal
        isOpen={showRecordSettlementModal}
        onClose={() => setShowRecordSettlementModal(false)}
        onSubmit={async (data) => {
          await recordSettlementMutation.mutateAsync(data)
        }}
        members={group.members || []}
        defaultCurrency={group.defaultCurrency}
        currentUserId={user?.id}
        prefilledToUserId={prefilledSettlement.toUserId}
        prefilledAmount={prefilledSettlement.amount}
      />

      {/* Invite Member Modal */}
      {showAddMember && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl">
            <h2 className="text-xl font-bold text-slate-900">Invite Member to Group</h2>
            <p className="mt-1 text-xs text-slate-500">
              Enter their email address. If they are not yet registered, an invitation will be queued.
            </p>

            <form
              onSubmit={(e) => {
                e.preventDefault()
                addMemberMutation.mutate()
              }}
              className="mt-5 space-y-4"
            >
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Email Address
                </label>
                <input
                  type="email"
                  required
                  value={memberEmail}
                  onChange={(e) => setMemberEmail(e.target.value)}
                  placeholder="friend@example.com"
                  className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="isAdminCheckbox"
                  checked={memberIsAdmin}
                  onChange={(e) => setMemberIsAdmin(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
                />
                <label htmlFor="isAdminCheckbox" className="text-xs text-slate-700">
                  Grant Group Admin privileges
                </label>
              </div>

              {addMemberMutation.isError && (
                <p className="text-xs text-red-600">
                  Failed to add member. Please verify email and permissions.
                </p>
              )}

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddMember(false)}
                  className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={addMemberMutation.isPending || !memberEmail.trim()}
                  className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition"
                >
                  {addMemberMutation.isPending ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span>Inviting...</span>
                    </>
                  ) : (
                    <span>Send Invite</span>
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
