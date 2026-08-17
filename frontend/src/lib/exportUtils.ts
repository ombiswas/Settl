import type { Expense, Settlement } from '../types/api'
import { formatCurrency, formatDate } from './utils'

/**
 * Export group expenses to CSV format and trigger browser download
 */
export function exportExpensesToCsv(groupName: string, expenses: Expense[]): void {
  if (!expenses || expenses.length === 0) return

  const headers = ['Date', 'Description', 'Category', 'Paid By', 'Amount', 'Currency', 'Split Type']
  const rows = expenses.map((exp) => [
    `"${formatDate(exp.createdAt)}"`,
    `"${exp.description.replace(/"/g, '""')}"`,
    `"${exp.categoryDisplayName || exp.category}"`,
    `"${exp.paidByName.replace(/"/g, '""')}"`,
    exp.amount.toFixed(2),
    `"${exp.currency}"`,
    `"${exp.splitType}"`,
  ])

  const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.setAttribute('href', url)
  link.setAttribute('download', `${groupName.toLowerCase().replace(/\s+/g, '_')}_expenses.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * Export settlements ledger to CSV format
 */
export function exportSettlementsToCsv(groupName: string, settlements: Settlement[]): void {
  if (!settlements || settlements.length === 0) return

  const headers = ['Settled At', 'From Member', 'To Member', 'Amount', 'Currency', 'Type']
  const rows = settlements.map((s) => [
    `"${formatDate(s.settledAt)}"`,
    `"${s.fromUserName.replace(/"/g, '""')}"`,
    `"${s.toUserName.replace(/"/g, '""')}"`,
    s.amount.toFixed(2),
    `"${s.currency}"`,
    `"${s.simplified ? 'Simplified Auto-Settlement' : 'Direct Repayment'}"`,
  ])

  const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.setAttribute('href', url)
  link.setAttribute('download', `${groupName.toLowerCase().replace(/\s+/g, '_')}_settlements.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * Generate printable summary view (browser print dialog / PDF)
 */
export function printGroupSummary(groupName: string, currency: string, expenses: Expense[]): void {
  const printWindow = window.open('', '_blank')
  if (!printWindow) return

  const total = expenses.reduce((sum, e) => sum + e.amount, 0)

  const html = `
    <!DOCTYPE html>
    <html>
      <head>
        <title>${groupName} — Expense Statement</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 32px; color: #0f172a; }
          h1 { font-size: 24px; margin-bottom: 4px; }
          p { color: #64748b; font-size: 13px; margin-top: 0; }
          .summary { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; margin: 24px 0; }
          table { width: 100%; border-collapse: collapse; margin-top: 16px; }
          th { text-align: left; font-size: 11px; text-transform: uppercase; color: #64748b; border-bottom: 2px solid #e2e8f0; padding: 10px 8px; }
          td { border-bottom: 1px solid #f1f5f9; padding: 10px 8px; font-size: 13px; }
          .amount { font-weight: bold; text-align: right; }
          .right { text-align: right; }
        </style>
      </head>
      <body>
        <h1>Settl — ${groupName}</h1>
        <p>Generated on ${new Date().toLocaleDateString()} • Official Expense Statement</p>
        <div class="summary">
          <strong>Total Group Spending:</strong> ${formatCurrency(total, currency)} (${expenses.length} expenses)
        </div>
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Description</th>
              <th>Category</th>
              <th>Paid By</th>
              <th class="right">Amount</th>
            </tr>
          </thead>
          <tbody>
            ${expenses
              .map(
                (e) => `
              <tr>
                <td>${formatDate(e.createdAt)}</td>
                <td><strong>${e.description}</strong></td>
                <td>${e.categoryDisplayName || e.category}</td>
                <td>${e.paidByName}</td>
                <td class="amount">${formatCurrency(e.amount, e.currency)}</td>
              </tr>
            `
              )
              .join('')}
          </tbody>
        </table>
      </body>
    </html>
  `

  printWindow.document.write(html)
  printWindow.document.close()
  printWindow.focus()
  setTimeout(() => {
    printWindow.print()
  }, 250)
}
