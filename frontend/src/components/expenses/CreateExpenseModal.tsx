import React, { useState, useMemo } from 'react'
import type {
  GroupMember,
  SplitType,
  ExpenseCategory,
  CreateExpenseRequest,
  ExpenseSplit,
} from '../../types/api'
import { CategoryPicker } from './CategoryPicker'
import { formatCurrency } from '../../lib/utils'
import {
  X,
  Loader2,
  DollarSign,
  Users,
  Percent,
  Divide,
  Scale,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react'

interface CreateExpenseModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (data: CreateExpenseRequest) => Promise<void>
  members: GroupMember[]
  defaultCurrency: string
  currentUserId?: string
}

export const CreateExpenseModal: React.FC<CreateExpenseModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  members,
  defaultCurrency,
  currentUserId,
}) => {
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState(defaultCurrency || 'USD')
  const [paidByUserId, setPaidByUserId] = useState(currentUserId || members[0]?.userId || '')
  const [category, setCategory] = useState<ExpenseCategory>('FOOD_AND_DINING')
  const [splitType, setSplitType] = useState<SplitType>('EQUAL')

  // Split state per member
  const [equalSelected, setEqualSelected] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {}
    members.forEach((m) => {
      initial[m.userId] = true
    })
    return initial
  })

  const [exactAmounts, setExactAmounts] = useState<Record<string, string>>({})
  const [percentages, setPercentages] = useState<Record<string, string>>({})
  const [shares, setShares] = useState<Record<string, number>>(() => {
    const initial: Record<string, number> = {}
    members.forEach((m) => {
      initial[m.userId] = 1
    })
    return initial
  })

  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const numAmount = parseFloat(amount) || 0

  // Calculations for validation & previews
  const splitCalculations = useMemo(() => {
    if (splitType === 'EQUAL') {
      const selectedMembers = members.filter((m) => equalSelected[m.userId])
      const count = selectedMembers.length
      const perPerson = count > 0 ? numAmount / count : 0
      return {
        isValid: count > 0 && numAmount > 0,
        perPerson,
        count,
        difference: 0,
      }
    }

    if (splitType === 'EXACT') {
      const totalEntered = Object.values(exactAmounts).reduce(
        (sum, val) => sum + (parseFloat(val) || 0),
        0
      )
      const diff = Math.round((numAmount - totalEntered) * 100) / 100
      return {
        isValid: Math.abs(diff) < 0.001 && numAmount > 0,
        totalEntered,
        difference: diff,
      }
    }

    if (splitType === 'PERCENTAGE') {
      const totalPercent = Object.values(percentages).reduce(
        (sum, val) => sum + (parseFloat(val) || 0),
        0
      )
      const diff = Math.round((100 - totalPercent) * 100) / 100
      return {
        isValid: Math.abs(diff) < 0.001 && numAmount > 0,
        totalPercent,
        difference: diff,
      }
    }

    if (splitType === 'SHARES') {
      const totalShares = Object.values(shares).reduce((sum, val) => sum + (val || 0), 0)
      return {
        isValid: totalShares > 0 && numAmount > 0,
        totalShares,
        difference: 0,
      }
    }

    return { isValid: numAmount > 0, difference: 0 }
  }, [splitType, equalSelected, exactAmounts, percentages, shares, numAmount, members])

  if (!isOpen) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)

    if (numAmount <= 0) {
      setErrorMessage('Please enter an amount greater than 0.')
      return
    }

    if (!splitCalculations.isValid) {
      if (splitType === 'EXACT') {
        setErrorMessage(
          `Exact amounts must sum to ${formatCurrency(numAmount, currency)}. Difference is ${formatCurrency(
            splitCalculations.difference,
            currency
          )}.`
        )
      } else if (splitType === 'PERCENTAGE') {
        setErrorMessage(
          `Percentages must sum to 100%. Current sum: ${splitCalculations.totalPercent}%.`
        )
      } else if (splitType === 'EQUAL') {
        setErrorMessage('At least one member must be selected for equal split.')
      }
      return
    }

    // Prepare splits payload
    const splits: ExpenseSplit[] = []

    if (splitType === 'EQUAL') {
      members.forEach((m) => {
        if (equalSelected[m.userId]) {
          splits.push({ userId: m.userId })
        }
      })
    } else if (splitType === 'EXACT') {
      members.forEach((m) => {
        const val = parseFloat(exactAmounts[m.userId] || '0')
        if (val > 0) {
          splits.push({ userId: m.userId, amount: val })
        }
      })
    } else if (splitType === 'PERCENTAGE') {
      members.forEach((m) => {
        const val = parseFloat(percentages[m.userId] || '0')
        if (val > 0) {
          splits.push({ userId: m.userId, percentage: val })
        }
      })
    } else if (splitType === 'SHARES') {
      members.forEach((m) => {
        const val = shares[m.userId] || 0
        if (val > 0) {
          splits.push({ userId: m.userId, shares: val })
        }
      })
    }

    const payload: CreateExpenseRequest = {
      description,
      amount: numAmount,
      currency,
      category,
      splitType,
      paidByUserId,
      splits,
    }

    setSubmitting(true)
    try {
      await onSubmit(payload)
      onClose()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setErrorMessage(axiosErr.response?.data?.message || 'Failed to save expense.')
      } else {
        setErrorMessage('An error occurred while creating the expense.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4 overflow-y-auto">
      <div className="my-8 w-full max-w-2xl rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
              <DollarSign className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Add Group Expense</h2>
              <p className="text-xs text-slate-500">Record a new shared bill or receipt</p>
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
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-800">
            <AlertCircle className="h-4 w-4 shrink-0 text-red-500" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-5 space-y-5">
          {/* Description & Amount */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                Description
              </label>
              <input
                type="text"
                required
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="e.g. Dinner at Luigi's, AirBnB cabin, Groceries"
                className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                Amount
              </label>
              <div className="relative mt-1.5">
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                  className="block w-full rounded-xl border border-slate-300 pl-3.5 pr-14 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
                <div className="absolute inset-y-0 right-0 flex items-center pr-2">
                  <select
                    value={currency}
                    onChange={(e) => setCurrency(e.target.value)}
                    className="h-full rounded-lg border-0 bg-transparent py-0 pl-1 pr-2 text-xs font-bold text-slate-600 focus:ring-0"
                  >
                    <option value="USD">USD</option>
                    <option value="EUR">EUR</option>
                    <option value="GBP">GBP</option>
                    <option value="INR">INR</option>
                    <option value="CAD">CAD</option>
                    <option value="AUD">AUD</option>
                    <option value="JPY">JPY</option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          {/* Paid By */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
              Paid By
            </label>
            <select
              value={paidByUserId}
              onChange={(e) => setPaidByUserId(e.target.value)}
              className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              {members.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.displayName} ({m.email})
                </option>
              ))}
            </select>
          </div>

          {/* Category Picker */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-2">
              Category
            </label>
            <CategoryPicker selected={category} onSelect={setCategory} />
          </div>

          {/* Split Mode Selector */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-2">
              Split Strategy
            </label>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {[
                { id: 'EQUAL', label: 'Equally', icon: Divide, desc: 'Split evenly' },
                { id: 'EXACT', label: 'Exact ($)', icon: DollarSign, desc: 'Specific amounts' },
                { id: 'PERCENTAGE', label: 'Percent (%)', icon: Percent, desc: 'By % ratio' },
                { id: 'SHARES', label: 'Shares', icon: Scale, desc: 'By parts/ratio' },
              ].map((mode) => {
                const Icon = mode.icon
                const isSelected = splitType === mode.id
                return (
                  <button
                    key={mode.id}
                    type="button"
                    onClick={() => setSplitType(mode.id as SplitType)}
                    className={`flex flex-col items-center justify-center gap-1 rounded-xl border p-2.5 text-xs font-medium transition cursor-pointer ${
                      isSelected
                        ? 'border-emerald-600 bg-emerald-50 text-emerald-800 ring-2 ring-emerald-500/20'
                        : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    <Icon className="h-4 w-4" />
                    <span className="font-bold">{mode.label}</span>
                    <span className="text-[10px] text-slate-400">{mode.desc}</span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Split Strategy Member Breakdown Input */}
          <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
            <div className="mb-3 flex items-center justify-between text-xs">
              <span className="font-bold text-slate-800 uppercase tracking-wider">
                Member Breakdown
              </span>
              {splitType === 'EXACT' && (
                <span
                  className={`font-semibold ${
                    splitCalculations.isValid ? 'text-emerald-700' : 'text-red-600'
                  }`}
                >
                  {splitCalculations.isValid ? (
                    <span className="inline-flex items-center gap-1">
                      <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> Matches total
                    </span>
                  ) : (
                    `Diff: ${formatCurrency(splitCalculations.difference, currency)} remaining`
                  )}
                </span>
              )}
              {splitType === 'PERCENTAGE' && (
                <span
                  className={`font-semibold ${
                    splitCalculations.isValid ? 'text-emerald-700' : 'text-red-600'
                  }`}
                >
                  {splitCalculations.isValid ? (
                    <span className="inline-flex items-center gap-1">
                      <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> 100% complete
                    </span>
                  ) : (
                    `Sum: ${splitCalculations.totalPercent}% (Diff: ${splitCalculations.difference}%)`
                  )}
                </span>
              )}
            </div>

            <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
              {members.map((m) => {
                return (
                  <div
                    key={m.userId}
                    className="flex items-center justify-between gap-3 rounded-lg bg-white p-2.5 border border-slate-200 text-xs shadow-2xs"
                  >
                    <span className="font-semibold text-slate-900 truncate max-w-[180px]">
                      {m.displayName}
                    </span>

                    {/* EQUAL SPLIT CHECKBOX */}
                    {splitType === 'EQUAL' && (
                      <div className="flex items-center gap-3">
                        {equalSelected[m.userId] && (
                          <span className="font-bold text-emerald-700">
                            {formatCurrency(splitCalculations.perPerson || 0, currency)}
                          </span>
                        )}
                        <input
                          type="checkbox"
                          checked={equalSelected[m.userId] || false}
                          onChange={(e) =>
                            setEqualSelected({ ...equalSelected, [m.userId]: e.target.checked })
                          }
                          className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
                        />
                      </div>
                    )}

                    {/* EXACT SPLIT INPUT */}
                    {splitType === 'EXACT' && (
                      <div className="relative w-28">
                        <span className="absolute inset-y-0 left-2.5 flex items-center text-slate-400 font-semibold">
                          $
                        </span>
                        <input
                          type="number"
                          step="0.01"
                          placeholder="0.00"
                          value={exactAmounts[m.userId] || ''}
                          onChange={(e) =>
                            setExactAmounts({ ...exactAmounts, [m.userId]: e.target.value })
                          }
                          className="w-full rounded-lg border border-slate-200 py-1 pl-6 pr-2 text-right font-semibold text-xs focus:ring-2 focus:ring-emerald-500"
                        />
                      </div>
                    )}

                    {/* PERCENTAGE SPLIT INPUT */}
                    {splitType === 'PERCENTAGE' && (
                      <div className="flex items-center gap-2">
                        {percentages[m.userId] && (
                          <span className="text-[11px] text-slate-500">
                            {formatCurrency(
                              (numAmount * (parseFloat(percentages[m.userId]) || 0)) / 100,
                              currency
                            )}
                          </span>
                        )}
                        <div className="relative w-20">
                          <input
                            type="number"
                            step="0.01"
                            placeholder="0"
                            value={percentages[m.userId] || ''}
                            onChange={(e) =>
                              setPercentages({ ...percentages, [m.userId]: e.target.value })
                            }
                            className="w-full rounded-lg border border-slate-200 py-1 pl-2 pr-6 text-right font-semibold text-xs focus:ring-2 focus:ring-emerald-500"
                          />
                          <span className="absolute inset-y-0 right-2 flex items-center text-slate-400 font-semibold">
                            %
                          </span>
                        </div>
                      </div>
                    )}

                    {/* SHARES SPLIT INPUT */}
                    {splitType === 'SHARES' && (
                      <div className="flex items-center gap-2">
                        {(splitCalculations.totalShares || 0) > 0 && (
                          <span className="text-[11px] text-slate-500">
                            {formatCurrency(
                              (numAmount * (shares[m.userId] || 0)) / (splitCalculations.totalShares || 1),
                              currency
                            )}
                          </span>
                        )}
                        <div className="flex items-center gap-1">
                          <input
                            type="number"
                            min="0"
                            step="1"
                            value={shares[m.userId] ?? 1}
                            onChange={(e) =>
                              setShares({ ...shares, [m.userId]: parseInt(e.target.value) || 0 })
                            }
                            className="w-16 rounded-lg border border-slate-200 py-1 px-2 text-center font-semibold text-xs focus:ring-2 focus:ring-emerald-500"
                          />
                          <span className="text-slate-400 text-[11px]">share(s)</span>
                        </div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Action Buttons */}
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
              disabled={submitting || !splitCalculations.isValid || !description.trim()}
              className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50 transition"
            >
              {submitting ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>Saving Expense...</span>
                </>
              ) : (
                <span>Save Expense</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
