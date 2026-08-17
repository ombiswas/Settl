import React from 'react'
import type { SuggestedSettlement, UserBalance } from '../../types/api'
import { formatCurrency } from '../../lib/utils'
import { CheckCircle2, ArrowRight } from 'lucide-react'

interface BalanceGraphProps {
  balances: UserBalance[]
  suggestedSettlements: SuggestedSettlement[]
  currency: string
}

export const BalanceGraph: React.FC<BalanceGraphProps> = ({
  balances,
  suggestedSettlements,
  currency,
}) => {
  if (!balances || balances.length === 0) {
    return null
  }

  // All settled state
  const isAllSettled = suggestedSettlements.length === 0

  if (isAllSettled) {
    return (
      <div className="flex flex-col items-center justify-center rounded-2xl border border-emerald-200 bg-emerald-50/40 p-8 text-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
          <CheckCircle2 className="h-6 w-6" />
        </div>
        <h4 className="mt-3 text-base font-bold text-slate-900">All Settled Up!</h4>
        <p className="mt-1 text-xs text-slate-500 max-w-sm">
          No outstanding debts in this group. Everyone is squared away.
        </p>
      </div>
    )
  }

  // Group members into Debtors (owes money) and Creditors (is owed money)
  const debtors = balances.filter((b) => b.netBalance < -0.001)
  const creditors = balances.filter((b) => b.netBalance > 0.001)
  const settled = balances.filter((b) => Math.abs(b.netBalance) <= 0.001)

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="text-base font-bold text-slate-900">Debt Settlement Flow</h3>
          <p className="text-xs text-slate-500">
            Visual map of direct payments needed to completely settle all group accounts.
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">
          {suggestedSettlements.length} payment{suggestedSettlements.length === 1 ? '' : 's'} required
        </span>
      </div>

      {/* Visual Debt Flow Cards / Graph */}
      <div className="mt-6 space-y-3">
        {suggestedSettlements.map((tx, idx) => (
          <div
            key={idx}
            className="group relative flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl border border-slate-200 bg-gradient-to-r from-red-50/30 via-slate-50 to-emerald-50/30 p-4 transition hover:border-slate-300 hover:shadow-xs"
          >
            {/* Debtor (Payer) */}
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-100 font-bold text-red-700 border-2 border-red-200">
                {tx.fromUserName?.charAt(0) || 'U'}
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900">{tx.fromUserName}</p>
                <p className="text-[11px] font-medium text-red-600">Payer (Owes)</p>
              </div>
            </div>

            {/* Transfer Arrow & Amount Pill */}
            <div className="flex flex-col items-center justify-center px-4">
              <div className="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3.5 py-1 text-xs font-bold text-slate-900 shadow-2xs">
                <span className="text-emerald-700 font-extrabold">
                  {formatCurrency(tx.amount, tx.currency || currency)}
                </span>
                <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:translate-x-1 transition text-emerald-600" />
              </div>
              <span className="mt-1 text-[10px] text-slate-400">Optimal direct transfer</span>
            </div>

            {/* Creditor (Receiver) */}
            <div className="flex items-center gap-3 sm:text-right">
              <div className="order-2 sm:order-1">
                <p className="text-sm font-bold text-slate-900">{tx.toUserName}</p>
                <p className="text-[11px] font-medium text-emerald-600">Recipient (Gets paid)</p>
              </div>
              <div className="order-1 sm:order-2 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-emerald-100 font-bold text-emerald-700 border-2 border-emerald-200">
                {tx.toUserName?.charAt(0) || 'U'}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Summary Chips */}
      <div className="mt-6 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4 text-xs text-slate-500">
        <span className="font-semibold text-slate-700">Group Status:</span>
        <span className="rounded-md bg-red-50 px-2 py-0.5 font-medium text-red-700">
          {debtors.length} Paying
        </span>
        <span className="rounded-md bg-emerald-50 px-2 py-0.5 font-medium text-emerald-700">
          {creditors.length} Receiving
        </span>
        {settled.length > 0 && (
          <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium text-slate-600">
            {settled.length} Settled
          </span>
        )}
      </div>
    </div>
  )
}
