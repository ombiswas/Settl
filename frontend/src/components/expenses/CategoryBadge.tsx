import React from 'react'
import type { ExpenseCategory } from '../../types/api'
import {
  Utensils,
  Car,
  Home,
  Film,
  ShoppingBag,
  HeartPulse,
  Plane,
  GraduationCap,
  Sparkles,
  HelpCircle,
  type LucideIcon,
} from 'lucide-react'

export interface CategoryMeta {
  code: ExpenseCategory
  label: string
  icon: LucideIcon
  bg: string
  text: string
  border: string
  pillBg: string
}

export const CATEGORY_MAP: Record<ExpenseCategory, CategoryMeta> = {
  FOOD_AND_DINING: {
    code: 'FOOD_AND_DINING',
    label: 'Food & Dining',
    icon: Utensils,
    bg: 'bg-amber-50',
    text: 'text-amber-700',
    border: 'border-amber-200',
    pillBg: 'bg-amber-100/70',
  },
  TRANSPORTATION: {
    code: 'TRANSPORTATION',
    label: 'Transportation',
    icon: Car,
    bg: 'bg-blue-50',
    text: 'text-blue-700',
    border: 'border-blue-200',
    pillBg: 'bg-blue-100/70',
  },
  HOUSING_AND_UTILITIES: {
    code: 'HOUSING_AND_UTILITIES',
    label: 'Housing & Utilities',
    icon: Home,
    bg: 'bg-indigo-50',
    text: 'text-indigo-700',
    border: 'border-indigo-200',
    pillBg: 'bg-indigo-100/70',
  },
  ENTERTAINMENT: {
    code: 'ENTERTAINMENT',
    label: 'Entertainment',
    icon: Film,
    bg: 'bg-purple-50',
    text: 'text-purple-700',
    border: 'border-purple-200',
    pillBg: 'bg-purple-100/70',
  },
  SHOPPING: {
    code: 'SHOPPING',
    label: 'Shopping',
    icon: ShoppingBag,
    bg: 'bg-pink-50',
    text: 'text-pink-700',
    border: 'border-pink-200',
    pillBg: 'bg-pink-100/70',
  },
  HEALTHCARE: {
    code: 'HEALTHCARE',
    label: 'Healthcare',
    icon: HeartPulse,
    bg: 'bg-red-50',
    text: 'text-red-700',
    border: 'border-red-200',
    pillBg: 'bg-red-100/70',
  },
  TRAVEL: {
    code: 'TRAVEL',
    label: 'Travel',
    icon: Plane,
    bg: 'bg-cyan-50',
    text: 'text-cyan-700',
    border: 'border-cyan-200',
    pillBg: 'bg-cyan-100/70',
  },
  EDUCATION: {
    code: 'EDUCATION',
    label: 'Education',
    icon: GraduationCap,
    bg: 'bg-emerald-50',
    text: 'text-emerald-700',
    border: 'border-emerald-200',
    pillBg: 'bg-emerald-100/70',
  },
  PERSONAL_CARE: {
    code: 'PERSONAL_CARE',
    label: 'Personal Care',
    icon: Sparkles,
    bg: 'bg-teal-50',
    text: 'text-teal-700',
    border: 'border-teal-200',
    pillBg: 'bg-teal-100/70',
  },
  OTHER: {
    code: 'OTHER',
    label: 'Other',
    icon: HelpCircle,
    bg: 'bg-slate-50',
    text: 'text-slate-700',
    border: 'border-slate-200',
    pillBg: 'bg-slate-100',
  },
}

export const CategoryBadge: React.FC<{
  category: ExpenseCategory | string
  size?: 'sm' | 'md'
}> = ({ category, size = 'sm' }) => {
  const meta = CATEGORY_MAP[category as ExpenseCategory] || CATEGORY_MAP.OTHER
  const Icon = meta.icon

  if (size === 'sm') {
    return (
      <span
        className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-semibold ${meta.bg} ${meta.text} ${meta.border}`}
      >
        <Icon className="h-3 w-3" />
        <span>{meta.label}</span>
      </span>
    )
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-semibold ${meta.bg} ${meta.text} ${meta.border}`}
    >
      <Icon className="h-3.5 w-3.5" />
      <span>{meta.label}</span>
    </span>
  )
}
