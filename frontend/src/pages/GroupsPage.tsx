import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { createPortal } from 'react-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { groupsApi } from '../api/client'
import { useAuthStore } from '../store/authStore'
import type { Group } from '../types/api'
import {
  Users,
  Plus,
  ArrowRight,
  Loader2,
  Calendar,
  Layers,
  Sparkles,
  Pencil,
  Trash2,
  AlertTriangle,
  X,
} from 'lucide-react'

export const GroupsPage: React.FC = () => {
  const queryClient = useQueryClient()
  const { user } = useAuthStore()
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [groupName, setGroupName] = useState('')
  const [currency, setCurrency] = useState('USD')

  // Edit & Delete Group State
  const [editingGroup, setEditingGroup] = useState<Group | null>(null)
  const [editGroupName, setEditGroupName] = useState('')
  const [editGroupCurrency, setEditGroupCurrency] = useState('USD')
  const [editGroupError, setEditGroupError] = useState<string | null>(null)

  const [deletingGroup, setDeletingGroup] = useState<Group | null>(null)
  const [deleteConfirmText, setDeleteConfirmText] = useState('')
  const [deleteGroupError, setDeleteGroupError] = useState<string | null>(null)

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

  const updateGroupMutation = useMutation({
    mutationFn: async () => {
      if (!editingGroup) return
      await groupsApi.update(editingGroup.id, {
        name: editGroupName.trim(),
        defaultCurrency: editGroupCurrency,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] })
      setEditingGroup(null)
      setEditGroupError(null)
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

  const deleteGroupMutation = useMutation({
    mutationFn: async () => {
      if (!deletingGroup) return
      await groupsApi.delete(deletingGroup.id)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] })
      setDeletingGroup(null)
      setDeleteGroupError(null)
    },
    onError: (err: unknown) => {
      let message = 'Failed to delete group. Please make sure all debts are fully settled.'
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        message = axiosErr.response?.data?.message || message
      }
      setDeleteGroupError(message)
    },
  })

  const isGroupAdmin = (g: Group) =>
    g.createdBy === user?.id ||
    !!g.members?.some((m) => m.userId === user?.id && (m.admin || m.isAdmin))

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
                <div className="flex items-center gap-1">
                  {isGroupAdmin(group) && (
                    <>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          setEditingGroup(group)
                          setEditGroupName(group.name)
                          setEditGroupCurrency(group.defaultCurrency)
                          setEditGroupError(null)
                        }}
                        title="Edit group details"
                        className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          setDeletingGroup(group)
                          setDeleteConfirmText('')
                          setDeleteGroupError(null)
                        }}
                        title="Delete group"
                        className="rounded-lg p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600 transition"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </>
                  )}
                  <span className="flex items-center gap-1 font-semibold text-emerald-600 group-hover:translate-x-0.5 transition ml-1">
                    <span>View</span>
                    <ArrowRight className="h-3.5 w-3.5" />
                  </span>
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

      {/* Edit Group Modal */}
      {editingGroup && createPortal(
        <div
          onClick={(e) => {
            if (e.target === e.currentTarget && !updateGroupMutation.isPending) {
              setEditingGroup(null)
            }
          }}
          className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-xs p-4 sm:p-6"
        >
          <div className="flex min-h-full items-center justify-center">
            <div
              onClick={(e) => e.stopPropagation()}
              className="relative w-full max-w-md my-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl transition-all"
            >
              <div className="flex items-start justify-between border-b border-slate-100 pb-4">
                <div className="flex items-center gap-2.5">
                  <div className="rounded-xl bg-emerald-100 p-2 text-emerald-700">
                    <Pencil className="h-5 w-5" />
                  </div>
                  <div>
                    <h2 className="text-lg font-bold text-slate-900">Edit Group Details</h2>
                    <p className="text-xs text-slate-500">Update group name and currency preferences</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setEditingGroup(null)}
                  disabled={updateGroupMutation.isPending}
                  className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  updateGroupMutation.mutate()
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
                    onClick={() => setEditingGroup(null)}
                    disabled={updateGroupMutation.isPending}
                    className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={updateGroupMutation.isPending || !editGroupName.trim()}
                    className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition"
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

      {/* Delete Group Modal */}
      {deletingGroup && createPortal(
        <div
          onClick={(e) => {
            if (e.target === e.currentTarget && !deleteGroupMutation.isPending) {
              setDeletingGroup(null)
            }
          }}
          className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-xs p-4 sm:p-6"
        >
          <div className="flex min-h-full items-center justify-center">
            <div
              onClick={(e) => e.stopPropagation()}
              className="relative w-full max-w-lg my-8 rounded-2xl border border-red-200 bg-white p-6 shadow-2xl transition-all"
            >
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
                  onClick={() => setDeletingGroup(null)}
                  disabled={deleteGroupMutation.isPending}
                  className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              <div className="mt-4 space-y-4">
                <p className="text-xs text-slate-600 leading-relaxed">
                  This will permanently delete the group <strong className="text-slate-900">{deletingGroup.name}</strong>,
                  including all recorded shared expenses, settlement ledgers, and recurring schedules.
                </p>

                <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-3.5 text-xs text-amber-900 leading-relaxed">
                  <strong>Notice:</strong> All group debts must be fully settled (each member's balance must be <strong>0.00</strong>)
                  before this group can be deleted.
                </div>

                {deleteGroupError && (
                  <div className="rounded-xl border border-red-200 bg-red-50 p-3.5 space-y-2">
                    <p className="text-xs text-red-800">{deleteGroupError}</p>
                    {deleteGroupError.toLowerCase().includes('settled') && (
                      <Link
                        to={`/groups/${deletingGroup.id}`}
                        onClick={() => setDeletingGroup(null)}
                        className="text-xs font-semibold text-red-700 underline hover:text-red-900 block"
                      >
                        Open Group to Settle Up Balances &rarr;
                      </Link>
                    )}
                  </div>
                )}

                <form
                  onSubmit={(e) => {
                    e.preventDefault()
                    if (deleteConfirmText.trim() === deletingGroup.name.trim()) {
                      deleteGroupMutation.mutate()
                    }
                  }}
                  className="space-y-4 pt-1"
                >
                  <div>
                    <label className="block text-xs font-semibold text-slate-800 mb-1.5">
                      Type <span className="font-mono text-red-600 font-bold select-all">{deletingGroup.name}</span> to confirm:
                    </label>
                    <input
                      type="text"
                      required
                      value={deleteConfirmText}
                      onChange={(e) => setDeleteConfirmText(e.target.value)}
                      placeholder={deletingGroup.name}
                      className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-xs font-mono text-slate-900 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500"
                    />
                  </div>

                  <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
                    <button
                      type="button"
                      onClick={() => setDeletingGroup(null)}
                      disabled={deleteGroupMutation.isPending}
                      className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={deleteGroupMutation.isPending || deleteConfirmText.trim() !== deletingGroup.name.trim()}
                      className="inline-flex items-center gap-2 rounded-xl bg-red-600 px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                    >
                      {deleteGroupMutation.isPending ? (
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
    </div>
  )
}
