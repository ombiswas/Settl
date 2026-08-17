import React from 'react'
import type { ExpenseCategory } from '../../types/api'
import { CATEGORY_MAP } from './CategoryBadge'

interface CategoryPickerProps {
  selected: ExpenseCategory
  onSelect: (category: ExpenseCategory) => void
}

export const CategoryPicker: React.FC<CategoryPickerProps> = ({ selected, onSelect }) => {
  const categories = Object.values(CATEGORY_MAP)

  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
      {categories.map((cat) => {
        const Icon = cat.icon
        const isSelected = selected === cat.code
        return (
          <button
            key={cat.code}
            type="button"
            onClick={() => onSelect(cat.code)}
            className={`flex flex-col items-center justify-center gap-1.5 rounded-xl border p-2.5 text-xs font-medium transition cursor-pointer ${
              isSelected
                ? 'border-emerald-600 bg-emerald-50/80 text-emerald-800 ring-2 ring-emerald-500/20 shadow-xs'
                : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'
            }`}
          >
            <div
              className={`flex h-7 w-7 items-center justify-center rounded-lg ${
                isSelected ? 'bg-emerald-600 text-white' : `${cat.bg} ${cat.text}`
              }`}
            >
              <Icon className="h-4 w-4" />
            </div>
            <span className="truncate max-w-full text-[11px] text-center">{cat.label}</span>
          </button>
        )
      })}
    </div>
  )
}
