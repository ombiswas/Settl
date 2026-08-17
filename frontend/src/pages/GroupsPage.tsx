import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { groupsApi } from '../api/client'
import {
  Users,
  Plus,
  ArrowRight,
  Loader2,
  Calendar,
  Layers,
  Sparkles,
} from 'lucide-react'

export const GroupsPage: React.FC = () => {
  const queryClient = useQueryClient()
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [groupName, setGroupName] = useState('')
  const [currency, setCurrency] = useState('USD')

  const { data: groups, isLoading, error } = useQuery({
    queryKey: ['groups'],
    queryFn: async () => {
      const res = await groupsApi.list()
      return res.data.data
    },
  })

  const createGroupMutation = useMutation({
    mutationFn: async () => {
      const res = await groupsApi.create({ name: groupName, defaultCurrency: currency })
      return res.data.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] })
      setShowCreateModal(false)
      setGroupName('')
      setCurrency('USD')
    },
  })

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            My Expense Groups
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Organize shared trip costs, room shares, household bills, and events.
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700 transition"
        >
          <Plus className="h-4 w-4" />
          <span>New Group</span>
        </button>
      </div>

      {/* Group List / Grid */}
      {isLoading ? (
        <div className="mt-12 flex flex-col items-center justify-center gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
          <p className="text-sm text-slate-500">Loading your groups...</p>
        </div>
      ) : error ? (
        <div className="mt-8 rounded-2xl border border-red-200 bg-red-50 p-6 text-center text-sm text-red-800">
          Failed to load expense groups. Please try refreshing.
        </div>
      ) : groups && groups.length > 0 ? (
        <div className="mt-8 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {groups.map((group) => (
            <Link
              key={group.id}
              to={`/groups/${group.id}`}
              className="group relative flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-xs transition hover:border-emerald-300 hover:shadow-md hover:shadow-emerald-500/5"
            >
              <div>
                <div className="flex items-center justify-between">
                  <span className="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 uppercase">
                    {group.defaultCurrency}
                  </span>
                  <div className="flex items-center gap-1.5 text-xs text-slate-400">
                    <Calendar className="h-3.5 w-3.5" />
                    <span>{new Date(group.createdAt).toLocaleDateString()}</span>
                  </div>
                </div>

                <h3 className="mt-3 text-lg font-bold text-slate-900 group-hover:text-emerald-700 transition">
                  {group.name}
                </h3>
              </div>

              <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-3 text-xs text-slate-500">
                <div className="flex items-center gap-1.5 font-medium">
                  <Users className="h-4 w-4 text-slate-400" />
                  <span>{group.memberCount || group.members?.length || 1} members</span>
                </div>
                <div className="flex items-center gap-1 font-semibold text-emerald-600 group-hover:translate-x-0.5 transition">
                  <span>View</span>
                  <ArrowRight className="h-3.5 w-3.5" />
                </div>
              </div>
            </Link>
          ))}
        </div>
      ) : (
        /* Empty state */
        <div className="mt-12 rounded-3xl border-2 border-dashed border-slate-200 bg-slate-50/50 p-10 text-center sm:p-16">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-700">
            <Layers className="h-7 w-7" />
          </div>
          <h3 className="mt-4 text-lg font-semibold text-slate-900">No groups created yet</h3>
          <p className="mt-1.5 text-sm text-slate-500 max-w-sm mx-auto">
            Create your first group for a shared trip, flatmates, or an event to start tracking and simplifying expenses.
          </p>
          <button
            onClick={() => setShowCreateModal(true)}
            className="mt-6 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700 transition"
          >
            <Sparkles className="h-4 w-4" />
            <span>Create First Group</span>
          </button>
        </div>
      )}

      {/* Create Group Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl">
            <h2 className="text-xl font-bold text-slate-900">Create New Group</h2>
            <p className="mt-1 text-xs text-slate-500">
              Set up a shared pool for friends, roommates, or travel buddies.
            </p>

            <form
              onSubmit={(e) => {
                e.preventDefault()
                createGroupMutation.mutate()
              }}
              className="mt-5 space-y-4"
            >
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Group Name
                </label>
                <input
                  type="text"
                  required
                  value={groupName}
                  onChange={(e) => setGroupName(e.target.value)}
                  placeholder="e.g. Ski Trip Colorado, Apartment 402"
                  className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Default Currency
                </label>
                <select
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                  className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white"
                >
                  <option value="USD">USD ($ - US Dollar)</option>
                  <option value="EUR">EUR (€ - Euro)</option>
                  <option value="GBP">GBP (£ - British Pound)</option>
                  <option value="INR">INR (₹ - Indian Rupee)</option>
                  <option value="CAD">CAD ($ - Canadian Dollar)</option>
                  <option value="AUD">AUD ($ - Australian Dollar)</option>
                  <option value="JPY">JPY (¥ - Japanese Yen)</option>
                </select>
              </div>

              {createGroupMutation.isError && (
                <p className="text-xs text-red-600">
                  Failed to create group. Please check name and try again.
                </p>
              )}

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createGroupMutation.isPending || !groupName.trim()}
                  className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition"
                >
                  {createGroupMutation.isPending ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span>Creating...</span>
                    </>
                  ) : (
                    <span>Create Group</span>
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
