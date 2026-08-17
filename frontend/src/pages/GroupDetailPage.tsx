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
  ShieldCheck,
  Zap,
} from 'lucide-react'

export const GroupDetailPage: React.FC = () => {
  const { groupId } = useParams<{ groupId: string }>()
  const queryClient = useQueryClient()
  const { user } = useAuthStore()

  const [activeTab, setActiveTab] = useState<'expenses' | 'balances' | 'settlements' | 'recurring' | 'activity'>('expenses')
  const [showAddMember, setShowAddMember] = useState(false)
  const [memberEmail, setMemberEmail] = useState('')
  const [memberIsAdmin, setMemberIsAdmin] = useState(false)

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
    enabled: !!groupId && activeTab === 'expenses',
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

  // Suggested Settlements (Simplified debt transactions)
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

  // Settle Up Mutation (1-click settlement confirmation)
  const recordSettlementMutation = useMutation({
    mutationFn: async ({ toUserId, amount }: { toUserId: string; amount: number }) => {
      if (!groupId) return
      await settlementsApi.record(groupId, {
        toUserId,
        amount,
        currency: group?.defaultCurrency || 'USD',
        isSimplified: true,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['balances', groupId] })
      queryClient.invalidateQueries({ queryKey: ['suggestedSettlements', groupId] })
      queryClient.invalidateQueries({ queryKey: ['settlements', groupId] })
      queryClient.invalidateQueries({ queryKey: ['activity', groupId] })
    },
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

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Back button & Group Header */}
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

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => setShowAddMember(true)}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 transition"
          >
            <UserPlus className="h-4 w-4 text-slate-500" />
            <span>Invite Member</span>
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
              className={`flex shrink-0 items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold transition ${
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
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-900">Group Expenses</h2>
            </div>

            {expensesLoading ? (
              <div className="py-12 text-center">
                <Loader2 className="mx-auto h-6 w-6 animate-spin text-emerald-600" />
              </div>
            ) : expenses && expenses.length > 0 ? (
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
                {expenses.map((expense) => (
                  <div key={expense.id} className="flex items-center justify-between p-4 hover:bg-slate-50/50 transition">
                    <div>
                      <h4 className="font-semibold text-slate-900">{expense.description}</h4>
                      <p className="text-xs text-slate-500">
                        Paid by <span className="font-medium text-slate-700">{expense.paidByName}</span> •{' '}
                        {formatDate(expense.createdAt)} • {expense.splitType} split
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="font-bold text-slate-900">
                        {formatCurrency(expense.amount, expense.currency)}
                      </p>
                      <span className="inline-block rounded-md bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-600">
                        {expense.categoryDisplayName || expense.category}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-2xl border-2 border-dashed border-slate-200 p-8 text-center text-sm text-slate-500">
                No expenses logged in this group yet.
              </div>
            )}
          </div>
        )}

        {/* BALANCES & SETTLE UP TAB */}
        {activeTab === 'balances' && (
          <div className="space-y-8">
            {/* Suggested Debt Simplifications (Greedy algorithm results) */}
            {suggestedData && suggestedData.suggestedTransactions?.length > 0 && (
              <div className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5">
                <div className="flex items-center gap-2 text-emerald-800">
                  <Zap className="h-5 w-5 text-emerald-600" />
                  <h3 className="text-base font-bold">Smart Debt Simplification</h3>
                </div>
                <p className="mt-1 text-xs text-emerald-700">
                  Calculated using Settl's greedy max-heap debt simplifier algorithm (minimized to{' '}
                  {suggestedData.transactionCount} transactions).
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
                          <span className="font-bold text-slate-900">{tx.fromUserName}</span> owes{' '}
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
                              })
                            }
                            disabled={recordSettlementMutation.isPending}
                            className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700 transition"
                          >
                            <CheckCircle className="h-3.5 w-3.5" />
                            <span>Confirm Payment</span>
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            )}

            {/* Balances list */}
            {balancesData && (
              <div>
                <h3 className="text-base font-bold text-slate-900">Member Net Balances</h3>
                <div className="mt-3 divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
                  {balancesData.balances.map((b) => (
                    <div key={b.userId} className="flex items-center justify-between p-4">
                      <div>
                        <p className="font-semibold text-slate-900">{b.displayName}</p>
                        <p className="text-xs text-slate-500">
                          Paid {formatCurrency(b.totalPaid, balancesData.currency)} • Share{' '}
                          {formatCurrency(b.totalShare, balancesData.currency)}
                        </p>
                      </div>
                      <div className="text-right">
                        <span
                          className={`font-bold ${
                            b.netBalance > 0
                              ? 'text-emerald-600'
                              : b.netBalance < 0
                              ? 'text-red-600'
                              : 'text-slate-500'
                          }`}
                        >
                          {b.netBalance > 0 ? '+' : ''}
                          {formatCurrency(b.netBalance, balancesData.currency)}
                        </span>
                        <p className="text-[11px] font-semibold text-slate-400 uppercase">
                          {b.status}
                        </p>
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
            <h3 className="text-base font-bold text-slate-900">Settlement Ledger</h3>
            <p className="text-xs text-slate-500 mb-4">
              Historical record of all debt repayments recorded in this group.
            </p>

            {settlements && settlements.length > 0 ? (
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
                {settlements.map((s) => (
                  <div key={s.id} className="flex items-center justify-between p-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
                        <CheckCircle className="h-4 w-4" />
                      </div>
                      <div>
                        <p className="text-sm font-semibold text-slate-900">
                          {s.fromUserName} paid {s.toUserName}
                        </p>
                        <p className="text-xs text-slate-500">{formatDate(s.settledAt)}</p>
                      </div>
                    </div>
                    <p className="font-bold text-emerald-700">
                      {formatCurrency(s.amount, s.currency)}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-2xl border-2 border-dashed border-slate-200 p-8 text-center text-sm text-slate-500">
                No settlements recorded yet.
              </div>
            )}
          </div>
        )}

        {/* RECURRING TAB */}
        {activeTab === 'recurring' && (
          <div>
            <h3 className="text-base font-bold text-slate-900">Recurring Expenses</h3>
            <p className="text-xs text-slate-500 mb-4">
              Automated recurring expense templates (rent, subscriptions, utilities).
            </p>

            {recurringList && recurringList.length > 0 ? (
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
                {recurringList.map((r) => (
                  <div key={r.id} className="flex items-center justify-between p-4">
                    <div>
                      <p className="font-semibold text-slate-900">{r.templateDescription}</p>
                      <p className="text-xs text-slate-500">
                        {r.frequency} • Paid by {r.paidByName} • Next run: {formatDate(r.nextRunAt)}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="font-bold text-slate-900">
                        {formatCurrency(r.amount, r.currency)}
                      </p>
                      <span className="text-[11px] font-medium text-emerald-700">
                        {r.active ? 'Active' : 'Paused'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-2xl border-2 border-dashed border-slate-200 p-8 text-center text-sm text-slate-500">
                No recurring expenses configured.
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
              <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
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
              <div className="rounded-2xl border-2 border-dashed border-slate-200 p-8 text-center text-sm text-slate-500">
                No activity recorded yet.
              </div>
            )}
          </div>
        )}
      </div>

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
