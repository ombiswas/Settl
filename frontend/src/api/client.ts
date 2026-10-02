import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../store/authStore'
import type {
  ApiResponse,
  AuthResponse,
  CategoryInfo,
  CreateExpenseRequest,
  CreateGroupRequest,
  UpdateGroupRequest,
  CreatePersonalExpenseRequest,
  UpdatePersonalExpenseRequest,
  CreateRecurringExpenseRequest,
  CreateSettlementRequest,
  Expense,
  Group,
  GroupBalancesResponse,
  Page,
  PageResponse,
  AuditLogEntry,
  PersonalAnalyticsResponse,
  PersonalExpense,
  RecurringExpense,
  Settlement,
  SuggestedSettlementsResponse,
  AddMemberRequest,
  GroupInvitation,
  InvitationPreview,
} from '../types/api'

const apiBase = ((import.meta as { env?: { VITE_API_BASE_URL?: string } }).env?.VITE_API_BASE_URL)?.replace(/\/+$/, '') || '/api'

export const apiClient = axios.create({
  baseURL: apiBase,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach in-memory access token
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Silent refresh interceptor on 401
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value?: unknown) => void
  reject: (reason?: unknown) => void
}> = []

const processQueue = (error: AxiosError | null, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // If 401 and not already retried and not an auth endpoint
    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/')
    ) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        })
          .then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`
            }
            return apiClient(originalRequest)
          })
          .catch((err) => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const { data } = await axios.post<ApiResponse<AuthResponse>>(
          `${apiBase}/auth/refresh`,
          {},
          { withCredentials: true }
        )

        const newAccessToken = data.data.accessToken
        useAuthStore.getState().setAuth(data.data.user, newAccessToken)

        processQueue(null, newAccessToken)

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        }
        return apiClient(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError as AxiosError, null)
        useAuthStore.getState().clearAuth()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

/* =========================================================================
   TYPED API ENDPOINTS
   ========================================================================= */

export const authApi = {
  register: (data: { email: string; password: string; displayName: string; inviteToken?: string }) =>
    apiClient.post<ApiResponse<{ userId: string; email: string; displayName: string; emailVerified: boolean }>>('/auth/register', data),

  login: (data: { email: string; password: string }) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/login', data),

  refresh: () =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh'),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/auth/logout'),

  verifyEmail: (token: string) =>
    apiClient.get<ApiResponse<string>>('/auth/verify', { params: { token } }),

  resendVerification: (email: string) =>
    apiClient.post<ApiResponse<string>>('/auth/resend-verification', { email }),
}

export const usersApi = {
  deleteAccount: (data: { password: string; confirmation: string }) =>
    apiClient.delete<ApiResponse<void>>('/users/me', { data }),
}

export const groupsApi = {
  list: () =>
    apiClient.get<ApiResponse<Group[]>>('/groups'),

  get: (groupId: string) =>
    apiClient.get<ApiResponse<Group>>(`/groups/${groupId}`),

  create: (data: CreateGroupRequest) =>
    apiClient.post<ApiResponse<Group>>('/groups', data),

  addMember: (groupId: string, data: AddMemberRequest) =>
    apiClient.post<ApiResponse<{ userId: string | null; email: string; displayName: string | null; registered: boolean; admin: boolean; message: string }>>(`/groups/${groupId}/members`, data),

  removeMember: (groupId: string, memberId: string) =>
    apiClient.delete<ApiResponse<void>>(`/groups/${groupId}/members/${memberId}`),

  delete: (groupId: string) =>
    apiClient.delete<ApiResponse<void>>(`/groups/${groupId}`),

  update: (groupId: string, data: UpdateGroupRequest) =>
    apiClient.put<ApiResponse<Group>>(`/groups/${groupId}`, data),

  getInvitations: (groupId: string) =>
    apiClient.get<ApiResponse<GroupInvitation[]>>(`/groups/${groupId}/invitations`),

  resendInvitation: (groupId: string, invitationId: string) =>
    apiClient.post<ApiResponse<GroupInvitation>>(`/groups/${groupId}/invitations/${invitationId}/resend`),

  revokeInvitation: (groupId: string, invitationId: string) =>
    apiClient.delete<ApiResponse<void>>(`/groups/${groupId}/invitations/${invitationId}`),
}

export const invitationsApi = {
  preview: (token: string) =>
    apiClient.get<ApiResponse<InvitationPreview>>('/invitations/preview', { params: { token } }),

  accept: (token: string) =>
    apiClient.post<ApiResponse<Group>>('/invitations/accept', null, { params: { token } }),
}

export const expensesApi = {
  listGroup: (groupId: string, params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<Expense>>>(`/groups/${groupId}/expenses`, { params }),

  getGroup: (groupId: string, expenseId: string) =>
    apiClient.get<ApiResponse<Expense>>(`/groups/${groupId}/expenses/${expenseId}`),

  createGroup: (groupId: string, data: CreateExpenseRequest) =>
    apiClient.post<ApiResponse<Expense>>(`/groups/${groupId}/expenses`, data),

  updateGroup: (groupId: string, expenseId: string, data: CreateExpenseRequest) =>
    apiClient.put<ApiResponse<Expense>>(`/groups/${groupId}/expenses/${expenseId}`, data),

  deleteGroup: (groupId: string, expenseId: string) =>
    apiClient.delete<ApiResponse<void>>(`/groups/${groupId}/expenses/${expenseId}`),

  listPersonal: (params?: { category?: string; startDate?: string; endDate?: string }) =>
    apiClient.get<ApiResponse<PersonalExpense[]>>('/expenses/personal', { params }),

  createPersonal: (data: CreatePersonalExpenseRequest) =>
    apiClient.post<ApiResponse<PersonalExpense>>('/expenses/personal', data),

  updatePersonal: (expenseId: string, data: UpdatePersonalExpenseRequest) =>
    apiClient.put<ApiResponse<PersonalExpense>>(`/expenses/personal/${expenseId}`, data),

  deletePersonal: (expenseId: string) =>
    apiClient.delete<ApiResponse<void>>(`/expenses/personal/${expenseId}`),

  getPersonalAnalytics: (params?: { startDate?: string; endDate?: string }) =>
    apiClient.get<ApiResponse<PersonalAnalyticsResponse>>('/expenses/personal/analytics', { params }),

  getCategories: () =>
    apiClient.get<ApiResponse<CategoryInfo[]>>('/categories'),
}

export const balancesApi = {
  getGroupBalances: (groupId: string) =>
    apiClient.get<ApiResponse<GroupBalancesResponse>>(`/groups/${groupId}/balances`),

  getSuggestedSettlements: (groupId: string) =>
    apiClient.get<ApiResponse<SuggestedSettlementsResponse>>(`/groups/${groupId}/settlements/suggested`),
}

export const settlementsApi = {
  list: (groupId: string, params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<Settlement>>>(`/groups/${groupId}/settlements`, { params }),

  record: (groupId: string, data: CreateSettlementRequest) =>
    apiClient.post<ApiResponse<Settlement>>(`/groups/${groupId}/settlements`, data),
}

export const recurringApi = {
  list: (groupId: string) =>
    apiClient.get<ApiResponse<RecurringExpense[]>>(`/groups/${groupId}/recurring-expenses`),

  create: (groupId: string, data: CreateRecurringExpenseRequest) =>
    apiClient.post<ApiResponse<RecurringExpense>>(`/groups/${groupId}/recurring-expenses`, data),

  deactivate: (groupId: string, recurringId: string) =>
    apiClient.delete<ApiResponse<void>>(`/groups/${groupId}/recurring-expenses/${recurringId}`),
}

export const activityApi = {
  list: (groupId: string, page: number = 0, size: number = 20) =>
    apiClient.get<ApiResponse<Page<AuditLogEntry>>>(`/groups/${groupId}/activity`, {
      params: { page, size, sort: 'createdAt,desc' },
    }),
}
