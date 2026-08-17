import React, { useState } from 'react'
import type { CreateSettlementRequest, GroupMember } from '../../types/api'
import { X, Loader2, CheckCircle2, DollarSign, AlertCircle } from 'lucide-react'

interface RecordSettlementModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (data: CreateSettlementRequest) => Promise<void>
  members: GroupMember[]
  defaultCurrency: string
  currentUserId?: string
  prefilledToUserId?: string
  prefilledAmount?: number
}

export const RecordSettlementModal: React.FC<RecordSettlementModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  members,
  defaultCurrency,
  currentUserId,
  prefilledToUserId,
  prefilledAmount,
}) => {
  const otherMembers = members.filter((m) => m.userId !== currentUserId)
  const [toUserId, setToUserId] = useState(
    prefilledToUserId || otherMembers[0]?.userId || ''
  )
  const [amount, setAmount] = useState(prefilledAmount ? prefilledAmount.toString() : '')
  const [currency, setCurrency] = useState(defaultCurrency || 'USD')
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (!isOpen) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)

    const num = parseFloat(amount)
    if (isNaN(num) || num <= 0) {
      setErrorMessage('Please enter a valid positive repayment amount.')
      return
    }

    if (!toUserId) {
      setErrorMessage('Please select a recipient.')
      return
    }

    setSubmitting(true)
    try {
      await onSubmit({
        toUserId,
        amount: num,
        currency,
        isSimplified: false,
      })
      onClose()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setErrorMessage(axiosErr.response?.data?.message || 'Failed to record repayment.')
      } else {
        setErrorMessage('An unexpected error occurred.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4 overflow-y-auto">
      <div className="my-8 w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
              <CheckCircle2 className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Record Settlement</h2>
              <p className="text-xs text-slate-500">Log an off-platform payment (cash, Venmo, UPI)</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {errorMessage && (
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-800">
            <AlertCircle className="h-4 w-4 shrink-0 text-red-500" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
              Recipient (Who received the money?)
            </label>
            <select
              value={toUserId}
              onChange={(e) => setToUserId(e.target.value)}
              className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              {otherMembers.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.displayName} ({m.email})
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                Amount Paid
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
                className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
              >
                <option value="USD">USD ($)</option>
                <option value="EUR">EUR (€)</option>
                <option value="GBP">GBP (£)</option>
                <option value="INR">INR (₹)</option>
                <option value="CAD">CAD ($)</option>
              </select>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
            <p>
              💰 Recording this settlement will immediately adjust both members' computed net balances and generate an audit log entry.
            </p>
          </div>

          <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !toUserId || !amount}
              className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition"
            >
              {submitting ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>Recording...</span>
                </>
              ) : (
                <span>Record Settlement</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
