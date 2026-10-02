import React, { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { createPortal } from 'react-dom'
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
  AlertTriangle,
  X,
  Pencil,
  Settings,
  Check,
  Mail,
} from 'lucide-react'

export const GroupDetailPage: React.FC = () => {
  const { groupId } = useParams<{ groupId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user } = useAuthStore()

  const [activeTab, setActiveTab] = useState<'expenses' | 'balances' | 'settlements' | 'recurring' | 'members' | 'activity' | 'settings'>('expenses')
  const [showAddExpenseModal, setShowAddExpenseModal] = useState(false)
  const [showAddRecurringModal, setShowAddRecurringModal] = useState(false)
  const [showRecordSettlementModal, setShowRecordSettlementModal] = useState(false)
  const [prefilledSettlement, setPrefilledSettlement] = useState<{ toUserId?: string; amount?: number }>({})

  const [showAddMember, setShowAddMember] = useState(false)
  const [memberEmail, setMemberEmail] = useState('')
  const [memberIsAdmin, setMemberIsAdmin] = useState(false)

  // Edit Group State (Header Modal)
  const [showEditGroupModal, setShowEditGroupModal] = useState(false)
  const [editGroupName, setEditGroupName] = useState('')
  const [editGroupCurrency, setEditGroupCurrency] = useState('USD')
  const [editGroupError, setEditGroupError] = useState<string | null>(null)
  const [editGroupSuccess, setEditGroupSuccess] = useState<string | null>(null)

  // Settings Tab State
  const [settingsName, setSettingsName] = useState('')
  const [settingsCurrency, setSettingsCurrency] = useState('USD')

  // Delete Group State
  const [showDeleteGroupModal, setShowDeleteGroupModal] = useState(false)
  const [deleteGroupConfirmText, setDeleteGroupConfirmText] = useState('')
  const [deleteGroupError, setDeleteGroupError] = useState<string | null>(null)
  const [isDeletingGroup, setIsDeletingGroup] = useState(false)

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

  useEffect(() => {
    if (group) {
      setSettingsName(group.name)
      setSettingsCurrency(group.defaultCurrency)
      setEditGroupName(group.name)
      setEditGroupCurrency(group.defaultCurrency)
    }
  }, [group])

  const isGroupAdmin =
    group?.createdBy === user?.id ||
    !!group?.members?.some((m) => m.userId === user?.id && (m.admin || m.isAdmin))

  const updateGroupMutation = useMutation({
    mutationFn: async (data: { name: string; defaultCurrency: string }) => {
      if (!groupId) return
      await groupsApi.update(groupId, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] })
      queryClient.invalidateQueries({ queryKey: ['groups'] })
      queryClient.invalidateQueries({ queryKey: ['userGroups'] })
      setShowEditGroupModal(false)
      setEditGroupError(null)
      setEditGroupSuccess('Group details updated successfully.')
      setTimeout(() => setEditGroupSuccess(null), 4000)
    },
    onError: (err: unknown) => {
      let message = 'Failed to update group. Please try again.'
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        message = axiosErr.response?.data?.message || message
      }
      setEditGroupError(message)
    },
  })

  const handleDeleteGroup = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!groupId || !group) return
    if (deleteGroupConfirmText.trim() !== group.name.trim()) return

    setDeleteGroupError(null)
    setIsDeletingGroup(true)

    try {
      await groupsApi.delete(groupId)
      queryClient.invalidateQueries({ queryKey: ['groups'] })
      queryClient.invalidateQueries({ queryKey: ['userGroups'] })
      navigate('/groups')
    } catch (err: unknown) {
      let message = 'Failed to delete group. Please make sure all debts are fully settled.'
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        message = axiosErr.response?.data?.message || message
      }
      setDeleteGroupError(message)
    } finally {
      setIsDeletingGroup(false)
    }
  }

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
      queryClient.invalidateQueries({ queryKey: ['groupInvitations', groupId] })
      setShowAddMember(false)
      setMemberEmail('')
      setMemberIsAdmin(false)
    },
  })

  // Pending Invitations Query
  const { data: invitationsData } = useQuery({
    queryKey: ['groupInvitations', groupId],
    queryFn: async () => {
      if (!groupId) return []
      const res = await groupsApi.getInvitations(groupId)
      return res.data.data || []
    },
    enabled: !!groupId,
  })

  // Resend Invitation Mutation
  const resendInvitationMutation = useMutation({
    mutationFn: async (invitationId: string) => {
      if (!groupId) return
      await groupsApi.resendInvitation(groupId, invitationId)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupInvitations', groupId] })
    },
  })

  // Revoke Invitation Mutation
  const revokeInvitationMutation = useMutation({
    mutationFn: async (invitationId: string) => {
      if (!groupId) return
      await groupsApi.revokeInvitation(groupId, invitationId)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupInvitations', groupId] })
    },
  })

  // Remove Member Mutation
  const removeMemberMutation = useMutation({
    mutationFn: async (memberId: string) => {
      if (!groupId) return
      await groupsApi.removeMember(groupId, memberId)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] })
      queryClient.invalidateQueries({ queryKey: ['activity', groupId] })
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

  const isUserAdmin = isGroupAdmin

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
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
              {group.name}
            </h1>
            <span className="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700 uppercase">
              {group.defaultCurrency}
            </span>
            {isUserAdmin && (
              <button
                type="button"
                onClick={() => {
                  setEditGroupName(group.name)
                  setEditGroupCurrency(group.defaultCurrency)
                  setEditGroupError(null)
                  setShowEditGroupModal(true)
                }}
                title="Edit Group Details"
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition cursor-pointer"
              >
                <Pencil className="h-4 w-4" />
              </button>
            )}
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

          {isGroupAdmin && (
            <>
              <button
                onClick={() => {
                  setEditGroupName(group.name)
                  setEditGroupCurrency(group.defaultCurrency)
                  setEditGroupError(null)
                  setShowEditGroupModal(true)
                }}
                title="Edit Group"
                className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 transition cursor-pointer"
              >
                <Pencil className="h-4 w-4 text-slate-500" />
                <span className="hidden sm:inline">Edit Details</span>
              </button>
              <button
                onClick={() => {
                  setDeleteGroupConfirmText('')
                  setDeleteGroupError(null)
                  setShowDeleteGroupModal(true)
                }}
                title="Delete Group"
                className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl border border-red-200 bg-red-50/70 px-3 py-2 text-xs font-semibold text-red-700 shadow-xs hover:bg-red-100 hover:border-red-300 transition cursor-pointer"
              >
                <Trash2 className="h-4 w-4 text-red-600" />
                <span className="hidden sm:inline">Delete Group</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="mt-6 flex overflow-x-auto border-b border-slate-200 pb-px">
        {[
          { id: 'expenses', label: 'Expenses', icon: Receipt },
          { id: 'balances', label: 'Balances & Settle Up', icon: DollarSign },
          { id: 'settlements', label: 'Settlement Ledger', icon: CheckCircle },
          { id: 'recurring', label: 'Recurring', icon: Repeat },
          { id: 'members', label: 'Members & Invites', icon: Users, badge: invitationsData?.length },
          { id: 'activity', label: 'Activity Feed', icon: History },
          { id: 'settings', label: 'Settings', icon: Settings },
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
              {!!tab.badge && tab.badge > 0 && (
                <span className="ml-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">
                  {tab.badge}
                </span>
              )}
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

        {/* MEMBERS & INVITATIONS TAB */}
        {activeTab === 'members' && (
          <div className="space-y-8 max-w-4xl">
            {/* Active Members Card */}
            <div>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
                <div>
                  <h3 className="text-base font-bold text-slate-900">Group Members</h3>
                  <p className="text-xs text-slate-500">
                    Participants who can split expenses and record settlements in this group.
                  </p>
                </div>
                <button
                  onClick={() => setShowAddMember(true)}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3.5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 transition cursor-pointer self-start sm:self-auto"
                >
                  <UserPlus className="h-4 w-4" />
                  <span>Invite New Member</span>
                </button>
              </div>

              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs overflow-hidden">
                {group.members?.map((m) => {
                  const isCreator = group.createdBy === m.userId
                  const isAdminMember = m.admin || m.isAdmin || isCreator
                  const isCurrentUser = user?.id === m.userId
                  return (
                    <div key={m.userId} className="flex items-center justify-between p-4 hover:bg-slate-50/50 transition">
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 text-emerald-800 font-bold text-sm">
                          {m.displayName?.charAt(0) || 'U'}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-900 text-sm">{m.displayName}</span>
                            {isCurrentUser && (
                              <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-600">
                                You
                              </span>
                            )}
                            {isCreator ? (
                              <span className="rounded-md bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-700 border border-amber-200">
                                Owner
                              </span>
                            ) : isAdminMember ? (
                              <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700 border border-emerald-200">
                                Admin
                              </span>
                            ) : null}
                          </div>
                          <p className="text-xs text-slate-500 mt-0.5">
                            {m.email} • Joined {formatDate(m.joinedAt)}
                          </p>
                        </div>
                      </div>

                      {/* Remove member button for admin/creator */}
                      {isUserAdmin && !isCurrentUser && !isCreator && (
                        <button
                          type="button"
                          onClick={() => {
                            if (window.confirm(`Are you sure you want to remove ${m.displayName} from this group? All their debts must be settled first.`)) {
                              removeMemberMutation.mutate(m.userId)
                            }
                          }}
                          disabled={removeMemberMutation.isPending}
                          title="Remove member"
                          className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 transition cursor-pointer"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>

            {/* Pending Invitations Section */}
            <div>
              <div className="mb-4">
                <h3 className="text-base font-bold text-slate-900">Pending Invitations</h3>
                <p className="text-xs text-slate-500">
                  Invited friends who haven't registered or accepted their invite link yet.
                </p>
              </div>

              {invitationsData && invitationsData.length > 0 ? (
                <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white shadow-xs overflow-hidden">
                  {invitationsData.map((inv) => (
                    <div key={inv.id} className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 hover:bg-slate-50/50 transition">
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-100 text-amber-700">
                          <Mail className="h-5 w-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-900 text-sm">{inv.email}</span>
                            <span className="rounded-md bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-700 border border-amber-200">
                              Pending
                            </span>
                            {inv.isAdmin && (
                              <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700 border border-emerald-200">
                                Invited as Admin
                              </span>
                            )}
                          </div>
                          <p className="text-xs text-slate-500 mt-0.5">
                            Invited by {inv.invitedByName} on {formatDate(inv.createdAt)} • Link expires in 7 days
                          </p>
                        </div>
                      </div>

                      {/* Admin controls: Resend & Revoke */}
                      {isUserAdmin && (
                        <div className="flex items-center gap-2 self-end sm:self-auto">
                          <button
                            type="button"
                            onClick={() => resendInvitationMutation.mutate(inv.id)}
                            disabled={resendInvitationMutation.isPending}
                            title="Resend invitation link"
                            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition cursor-pointer"
                          >
                            <Repeat className="h-3.5 w-3.5 text-slate-500" />
                            <span>Resend</span>
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              if (window.confirm(`Revoke invitation for ${inv.email}? The invite link will become invalid.`)) {
                                revokeInvitationMutation.mutate(inv.id)
                              }
                            }}
                            disabled={revokeInvitationMutation.isPending}
                            title="Cancel / Revoke invite"
                            className="inline-flex items-center gap-1.5 rounded-lg border border-red-200 bg-red-50/70 px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100 transition cursor-pointer"
                          >
                            <Ban className="h-3.5 w-3.5 text-red-600" />
                            <span>Revoke</span>
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-2xl border-2 border-dashed border-slate-200 bg-white p-8 text-center text-xs text-slate-500">
                  No pending invitations. All invited participants have accepted and joined.
                </div>
              )}
            </div>
          </div>
        )}

        {/* SETTINGS TAB */}
        {activeTab === 'settings' && (
          <div className="max-w-3xl space-y-8">
            <div>
              <h2 className="text-xl font-bold tracking-tight text-slate-900">Group Settings</h2>
              <p className="mt-1 text-xs text-slate-500">
                Manage group profile details, default currency, and group lifecycle.
              </p>
            </div>

            {/* Notification messages */}
            {editGroupSuccess && (
              <div className="flex items-center gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-xs font-semibold text-emerald-800">
                <Check className="h-4 w-4 text-emerald-600 shrink-0" />
                <span>{editGroupSuccess}</span>
              </div>
            )}
            {editGroupError && (
              <div className="flex items-center gap-2.5 rounded-xl border border-red-200 bg-red-50 p-4 text-xs font-semibold text-red-800">
                <AlertTriangle className="h-4 w-4 text-red-600 shrink-0" />
                <span>{editGroupError}</span>
              </div>
            )}

            {/* General Settings Card */}
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-slate-100 gap-2">
                <div>
                  <h3 className="text-base font-bold text-slate-900">General Information</h3>
                  <p className="text-xs text-slate-500">
                    Group display name and default settlement currency.
                  </p>
                </div>
                <div>
                  {isUserAdmin ? (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                      <span className="h-1.5 w-1.5 rounded-full bg-emerald-600" />
                      Admin Access
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
                      Member (Read-only)
                    </span>
                  )}
                </div>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (!isUserAdmin || !settingsName.trim()) return
                  updateGroupMutation.mutate({
                    name: settingsName.trim(),
                    defaultCurrency: settingsCurrency,
                  })
                }}
                className="mt-6 space-y-5"
              >
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                    Group Name
                  </label>
                  <input
                    type="text"
                    required
                    maxLength={150}
                    disabled={!isUserAdmin || updateGroupMutation.isPending}
                    value={settingsName}
                    onChange={(e) => setSettingsName(e.target.value)}
                    placeholder="Group name"
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm font-medium text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500 disabled:bg-slate-50 disabled:text-slate-500"
                  />
                  <p className="mt-1.5 text-xs text-slate-400">
                    Maximum 150 characters.
                  </p>
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                    Default Currency
                  </label>
                  <select
                    disabled={!isUserAdmin || updateGroupMutation.isPending}
                    value={settingsCurrency}
                    onChange={(e) => setSettingsCurrency(e.target.value)}
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm font-medium text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white disabled:bg-slate-50 disabled:text-slate-500"
                  >
                    <option value="USD">USD ($) - US Dollar</option>
                    <option value="EUR">EUR (€) - Euro</option>
                    <option value="GBP">GBP (£) - British Pound</option>
                    <option value="INR">INR (₹) - Indian Rupee</option>
                    <option value="CAD">CAD ($) - Canadian Dollar</option>
                    <option value="AUD">AUD ($) - Australian Dollar</option>
                    <option value="JPY">JPY (¥) - Japanese Yen</option>
                  </select>
                  <p className="mt-1.5 text-xs text-slate-400">
                    Used as the default currency for new expenses, debt calculations, and summary exports.
                  </p>
                </div>

                {isUserAdmin && (
                  <div className="flex items-center justify-end pt-3 border-t border-slate-100">
                    <button
                      type="submit"
                      disabled={updateGroupMutation.isPending || !settingsName.trim()}
                      className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition cursor-pointer"
                    >
                      {updateGroupMutation.isPending ? (
                        <>
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          <span>Saving Changes...</span>
                        </>
                      ) : (
                        <span>Save Changes</span>
                      )}
                    </button>
                  </div>
                )}
              </form>
            </div>

            {/* Danger Zone Card */}
            <div className="rounded-2xl border border-red-200 bg-red-50/30 p-6 shadow-xs">
              <div className="flex items-start gap-3 border-b border-red-100 pb-4">
                <div className="rounded-xl bg-red-100 p-2 text-red-600">
                  <AlertTriangle className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-red-900">Danger Zone</h3>
                  <p className="text-xs text-red-700 mt-0.5">
                    Irreversible actions that affect all members of this group.
                  </p>
                </div>
              </div>

              <div className="mt-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="max-w-lg">
                  <h4 className="text-sm font-bold text-slate-900">Delete this group</h4>
                  <p className="mt-1 text-xs text-slate-600 leading-relaxed">
                    Once deleted, all group expenses, settlements, balances, and recurring plans will be permanently lost.
                    All debts must be settled (balance 0.00 for all members) prior to deletion.
                  </p>
                </div>

                <div>
                  {isUserAdmin ? (
                    <button
                      type="button"
                      onClick={() => {
                        setDeleteGroupConfirmText('')
                        setDeleteGroupError(null)
                        setShowDeleteGroupModal(true)
                      }}
                      className="inline-flex min-h-[44px] items-center gap-2 rounded-xl border border-red-300 bg-red-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-red-700 transition cursor-pointer"
                    >
                      <Trash2 className="h-4 w-4" />
                      <span>Delete Group</span>
                    </button>
                  ) : (
                    <span className="text-xs text-slate-400 italic">
                      Admins only
                    </span>
                  )}
                </div>
              </div>
            </div>
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

      {/* Delete Group Modal */}
      {showDeleteGroupModal && group && createPortal(
        <div
          onClick={(e) => {
            if (e.target === e.currentTarget && !isDeletingGroup) {
              setShowDeleteGroupModal(false)
            }
          }}
          className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-xs p-4 sm:p-6"
        >
          <div className="flex min-h-full items-center justify-center">
            <div
              onClick={(e) => e.stopPropagation()}
              className="relative w-full max-w-lg my-8 rounded-2xl border border-red-200 bg-white p-6 shadow-2xl transition-all"
            >
              {/* Header */}
              <div className="flex items-start justify-between border-b border-slate-100 pb-4">
                <div className="flex items-start gap-3">
                  <div className="rounded-xl bg-red-100 p-2 text-red-600">
                    <AlertTriangle className="h-5 w-5" />
                  </div>
                  <div>
                    <h2 className="text-lg font-bold text-slate-900">Delete Group</h2>
                    <p className="text-xs text-red-600 font-medium">Permanent and irreversible action</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setShowDeleteGroupModal(false)}
                  disabled={isDeletingGroup}
                  className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {/* Warning Content */}
              <div className="mt-4 space-y-4">
                <p className="text-xs text-slate-600 leading-relaxed">
                  This will permanently delete the group <strong className="text-slate-900">{group.name}</strong>,
                  including all recorded shared expenses, settlement ledgers, recurring schedules, and activity feeds.
                </p>

                <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-3.5 text-xs text-amber-900 leading-relaxed">
                  <strong>Notice:</strong> All group debts must be fully settled (each member's balance must be <strong>0.00</strong>)
                  before this group can be deleted.
                </div>

                {deleteGroupError && (
                  <div className="rounded-xl border border-red-200 bg-red-50 p-3.5 space-y-2">
                    <p className="text-xs text-red-800">{deleteGroupError}</p>
                    {deleteGroupError.toLowerCase().includes('settled') && (
                      <button
                        type="button"
                        onClick={() => {
                          setShowDeleteGroupModal(false)
                          setActiveTab('balances')
                        }}
                        className="text-xs font-semibold text-red-700 underline hover:text-red-900 block"
                      >
                        Switch to Balances & Settle Up tab &rarr;
                      </button>
                    )}
                  </div>
                )}

                <form onSubmit={handleDeleteGroup} className="space-y-4 pt-1">
                  <div>
                    <label className="block text-xs font-semibold text-slate-800 mb-1.5">
                      Type <span className="font-mono text-red-600 font-bold select-all">{group.name}</span> to confirm:
                    </label>
                    <input
                      type="text"
                      required
                      value={deleteGroupConfirmText}
                      onChange={(e) => setDeleteGroupConfirmText(e.target.value)}
                      placeholder={group.name}
                      className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-xs font-mono text-slate-900 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500"
                    />
                  </div>

                  <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
                    <button
                      type="button"
                      onClick={() => setShowDeleteGroupModal(false)}
                      disabled={isDeletingGroup}
                      className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={isDeletingGroup || deleteGroupConfirmText.trim() !== group.name.trim()}
                      className="inline-flex items-center gap-2 rounded-xl bg-red-600 px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                    >
                      {isDeletingGroup ? (
                        <>
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          <span>Deleting Group...</span>
                        </>
                      ) : (
                        <>
                          <Trash2 className="h-3.5 w-3.5" />
                          <span>Permanently Delete Group</span>
                        </>
                      )}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Edit Group Modal */}
      {showEditGroupModal && group && createPortal(
        <div
          onClick={(e) => {
            if (e.target === e.currentTarget && !updateGroupMutation.isPending) {
              setShowEditGroupModal(false)
            }
          }}
          className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-xs p-4 sm:p-6"
        >
          <div className="flex min-h-full items-center justify-center">
            <div
              onClick={(e) => e.stopPropagation()}
              className="relative w-full max-w-md my-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl transition-all"
            >
              <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                <h2 className="text-xl font-bold text-slate-900">Edit Group Details</h2>
                <button
                  type="button"
                  onClick={() => setShowEditGroupModal(false)}
                  disabled={updateGroupMutation.isPending}
                  className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition cursor-pointer"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (!editGroupName.trim()) return
                  updateGroupMutation.mutate({
                    name: editGroupName.trim(),
                    defaultCurrency: editGroupCurrency,
                  })
                }}
                className="mt-5 space-y-4"
              >
                {editGroupError && (
                  <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                    {editGroupError}
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                    Group Name
                  </label>
                  <input
                    type="text"
                    required
                    maxLength={150}
                    value={editGroupName}
                    onChange={(e) => setEditGroupName(e.target.value)}
                    placeholder="e.g. Ski Trip, Flat 4B"
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                    Default Currency
                  </label>
                  <select
                    value={editGroupCurrency}
                    onChange={(e) => setEditGroupCurrency(e.target.value)}
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white"
                  >
                    <option value="USD">USD ($)</option>
                    <option value="EUR">EUR (€)</option>
                    <option value="GBP">GBP (£)</option>
                    <option value="INR">INR (₹)</option>
                    <option value="CAD">CAD ($)</option>
                    <option value="AUD">AUD ($)</option>
                    <option value="JPY">JPY (¥)</option>
                  </select>
                </div>

                <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={() => setShowEditGroupModal(false)}
                    disabled={updateGroupMutation.isPending}
                    className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={updateGroupMutation.isPending || !editGroupName.trim()}
                    className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition cursor-pointer"
                  >
                    {updateGroupMutation.isPending ? (
                      <>
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        <span>Updating...</span>
                      </>
                    ) : (
                      <span>Save Changes</span>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
