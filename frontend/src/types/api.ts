export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  errors?: Record<string, string>
  timestamp: string
}

export interface User {
  id: string
  email: string
  displayName: string
  emailVerified?: boolean
}

export interface AuthResponse {
  user: User
  accessToken: string
}

export interface GroupMember {
  userId: string
  email: string
  displayName: string
  admin: boolean
  joinedAt: string
}

export interface Group {
  id: string
  name: string
  defaultCurrency: string
  createdBy: string
  createdAt: string
  members: GroupMember[]
  memberCount: number
}

export interface CreateGroupRequest {
  name: string
  defaultCurrency: string
}

export interface AddMemberRequest {
  email: string
  isAdmin?: boolean
}

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENTAGE' | 'SHARES' | 'PERSONAL'

export type ExpenseCategory =
  | 'FOOD_AND_DINING'
  | 'TRANSPORTATION'
  | 'HOUSING_AND_UTILITIES'
  | 'ENTERTAINMENT'
  | 'SHOPPING'
  | 'HEALTHCARE'
  | 'TRAVEL'
  | 'EDUCATION'
  | 'PERSONAL_CARE'
  | 'OTHER'

export interface CategoryInfo {
  code: ExpenseCategory
  displayName: string
}

export interface ExpenseSplit {
  userId: string
  amount?: number
  percentage?: number
  shares?: number
}

export interface ExpenseShare {
  userId: string
  userDisplayName: string
  userEmail: string
  amountOwed: number
}

export interface Expense {
  id: string
  groupId?: string | null
  groupName?: string | null
  paidById: string
  paidByName: string
  paidByEmail: string
  description: string
  amount: number
  currency: string
  category: ExpenseCategory
  categoryDisplayName: string
  splitType: SplitType
  receiptUrl?: string | null
  shares: ExpenseShare[]
  createdAt: string
}

export interface CreateExpenseRequest {
  description: string
  amount: number
  currency?: string
  category: ExpenseCategory
  splitType: SplitType
  paidByUserId?: string
  receiptUrl?: string
  splits?: ExpenseSplit[]
}

export interface PersonalExpense {
  id: string
  description: string
  amount: number
  currency: string
  category: ExpenseCategory
  categoryDisplayName: string
  receiptUrl?: string | null
  createdAt: string
}

export interface CreatePersonalExpenseRequest {
  description: string
  amount: number
  currency?: string
  category: ExpenseCategory
  receiptUrl?: string
}

export interface CategorySpending {
  category: ExpenseCategory
  categoryDisplayName: string
  totalAmount: number
  percentage: number
  count: number
}

export interface MonthlySpending {
  month: string
  totalAmount: number
  count: number
}

export interface PersonalAnalyticsResponse {
  totalSpent: number
  totalExpenseCount: number
  currency: string
  categoryBreakdown: CategorySpending[]
  monthlyBreakdown: MonthlySpending[]
}

export interface UserBalance {
  userId: string
  displayName: string
  email: string
  netBalance: number
  status: 'OWES' | 'IS_OWED' | 'SETTLED'
  totalPaid: number
  totalShare: number
}

export interface GroupBalancesResponse {
  groupId: string
  groupName: string
  currency: string
  totalGroupSpend: number
  balances: UserBalance[]
}

export interface SuggestedSettlement {
  fromUserId: string
  fromUserName: string
  fromUserEmail: string
  toUserId: string
  toUserName: string
  toUserEmail: string
  amount: number
  currency: string
}

export interface SuggestedSettlementsResponse {
  groupId: string
  groupName: string
  currency: string
  transactionCount: number
  totalSettledAmount: number
  suggestedTransactions: SuggestedSettlement[]
}

export interface Settlement {
  id: string
  groupId: string
  fromUserId: string
  fromUserName: string
  fromUserEmail: string
  toUserId: string
  toUserName: string
  toUserEmail: string
  amount: number
  currency: string
  simplified: boolean
  settledAt: string
}

export interface CreateSettlementRequest {
  toUserId: string
  amount: number
  currency?: string
  isSimplified?: boolean
}

export type RecurringFrequency = 'WEEKLY' | 'MONTHLY'

export interface RecurringExpense {
  id: string
  groupId: string
  templateDescription: string
  amount: number
  currency: string
  category: ExpenseCategory
  splitType: SplitType
  paidById: string
  paidByName: string
  paidByEmail: string
  frequency: RecurringFrequency
  nextRunAt: string
  active: boolean
  createdAt: string
}

export interface CreateRecurringExpenseRequest {
  templateDescription: string
  amount: number
  currency?: string
  category: ExpenseCategory
  splitType: SplitType
  frequency: RecurringFrequency
  nextRunAt: string
}

export type AuditAction =
  | 'GROUP_CREATED'
  | 'GROUP_UPDATED'
  | 'MEMBER_JOINED'
  | 'MEMBER_REMOVED'
  | 'EXPENSE_CREATED'
  | 'EXPENSE_UPDATED'
  | 'EXPENSE_DELETED'
  | 'SETTLEMENT_RECORDED'
  | 'RECURRING_EXPENSE_CREATED'
  | 'RECURRING_EXPENSE_TRIGGERED'

export interface AuditLogEntry {
  id: string
  groupId: string
  actorId?: string | null
  actorName: string
  actorEmail: string
  action: AuditAction
  details?: Record<string, unknown>
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}
