import React, { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { useAuthStore } from '../../store/authStore'
import { usersApi } from '../../api/client'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import {
  X,
  User as UserIcon,
  Mail,
  ShieldCheck,
  AlertTriangle,
  Trash2,
  Lock,
  Loader2,
  AlertCircle,
} from 'lucide-react'

interface AccountSettingsModalProps {
  isOpen: boolean
  onClose: () => void
}

export const AccountSettingsModal: React.FC<AccountSettingsModalProps> = ({ isOpen, onClose }) => {
  const { user, clearAuth } = useAuthStore()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const resetDeleteState = () => {
    setShowDeleteConfirm(false)
    setPassword('')
    setConfirmation('')
    setErrorMessage(null)
  }

  const handleModalClose = () => {
    resetDeleteState()
    onClose()
  }

  useEffect(() => {
    if (!isOpen) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        handleModalClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    const originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = originalOverflow
    }
  }, [isOpen])

  if (!isOpen) return null

  const handleDeleteAccount = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)
    setIsLoading(true)

    try {
      await usersApi.deleteAccount({
        password,
        confirmation,
      })

      // Clean up auth state and queries
      clearAuth()
      queryClient.clear()
      onClose()
      navigate('/login?deleted=true')
    } catch (err: unknown) {
      let message = 'Failed to delete account. Please verify your password and group statuses.'
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string; error?: string } } }
        message = axiosErr.response?.data?.message || axiosErr.response?.data?.error || message
      }
      setErrorMessage(message)
    } finally {
      setIsLoading(false)
    }
  }

  return createPortal(
    <div
      onClick={(e) => {
        if (e.target === e.currentTarget) handleModalClose()
      }}
      className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-xs p-4 sm:p-6"
    >
      <div className="flex min-h-full items-center justify-center">
        <div
          onClick={(e) => e.stopPropagation()}
          className="relative w-full max-w-lg my-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl transition-all"
        >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
              <UserIcon className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Account Settings</h2>
              <p className="text-xs text-slate-500">Manage your profile and account lifecycle</p>
            </div>
          </div>
          <button
            onClick={handleModalClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        <div className="mt-5 space-y-6">
          {/* User Profile Card */}
          <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-4">
            <div className="flex items-center gap-4">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-700 text-xl font-bold text-white shadow-md shadow-emerald-200 uppercase">
                {user?.displayName?.charAt(0) || 'U'}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <h3 className="text-base font-bold text-slate-900 truncate">{user?.displayName}</h3>
                  <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-800">
                    <ShieldCheck className="h-3 w-3" />
                    Active
                  </span>
                </div>
                <div className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                  <Mail className="h-3.5 w-3.5 text-slate-400" />
                  <span className="truncate">{user?.email}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Danger Zone */}
          <div className="rounded-xl border border-red-200 bg-red-50/40 p-4">
            <div className="flex items-start gap-3">
              <div className="rounded-lg bg-red-100 p-2 text-red-600">
                <AlertTriangle className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h4 className="text-sm font-bold text-red-950">Danger Zone</h4>
                <p className="mt-1 text-xs text-red-700/90 leading-relaxed">
                  Permanently delete your account, private personal expenses, and all active sessions. 
                  All group debts must be fully settled (<span className="font-semibold">0.00 balance</span>) 
                  and sole admin roles transferred before deletion.
                </p>

                {!showDeleteConfirm ? (
                  <button
                    type="button"
                    onClick={() => setShowDeleteConfirm(true)}
                    className="mt-3.5 inline-flex items-center gap-2 rounded-lg bg-red-600 px-3.5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-red-700 transition"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    Delete Account
                  </button>
                ) : (
                  <form onSubmit={handleDeleteAccount} className="mt-4 space-y-3.5 border-t border-red-200/80 pt-3.5">
                    {errorMessage && (
                      <div className="flex items-start gap-2.5 rounded-lg border border-red-300 bg-white p-3 text-xs text-red-800 shadow-xs">
                        <AlertCircle className="h-4 w-4 shrink-0 text-red-600 mt-0.5" />
                        <div className="flex-1 leading-relaxed whitespace-pre-wrap">{errorMessage}</div>
                      </div>
                    )}

                    <div>
                      <label className="block text-xs font-semibold text-slate-800">
                        Confirm Current Password
                      </label>
                      <div className="relative mt-1">
                        <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                          <Lock className="h-3.5 w-3.5" />
                        </div>
                        <input
                          type="password"
                          required
                          value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          placeholder="Your account password"
                          className="w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 py-2 text-xs text-slate-900 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500"
                        />
                      </div>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-800">
                        Type <span className="font-mono text-red-600 font-bold">DELETE</span> to confirm
                      </label>
                      <input
                        type="text"
                        required
                        value={confirmation}
                        onChange={(e) => setConfirmation(e.target.value)}
                        placeholder="DELETE"
                        className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-mono text-slate-900 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500"
                      />
                    </div>

                    <div className="flex items-center gap-2 pt-1">
                      <button
                        type="submit"
                        disabled={isLoading || confirmation.trim().toUpperCase() !== 'DELETE' || !password}
                        className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                      >
                        {isLoading ? (
                          <>
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            <span>Deleting Account...</span>
                          </>
                        ) : (
                          <>
                            <Trash2 className="h-3.5 w-3.5" />
                            <span>Permanently Delete</span>
                          </>
                        )}
                      </button>
                      <button
                        type="button"
                        onClick={resetDeleteState}
                        disabled={isLoading}
                        className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="mt-6 flex justify-end border-t border-slate-100 pt-3">
          <button
            type="button"
            onClick={handleModalClose}
            className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 transition"
          >
            Close
          </button>
        </div>
        </div>
      </div>
    </div>,
    document.body
  )
}
