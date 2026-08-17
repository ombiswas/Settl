import { create } from 'zustand'
import type { User } from '../types/api'

interface AuthState {
  user: User | null
  accessToken: string | null
  isAuthenticated: boolean
  isInitializing: boolean
  setAuth: (user: User, accessToken: string) => void
  setAccessToken: (accessToken: string) => void
  clearAuth: () => void
  setInitializing: (isInitializing: boolean) => void
}

/**
 * Access tokens are stored IN-MEMORY in Zustand state, never in localStorage,
 * preventing persistent token theft via XSS. Refresh tokens are secured in
 * httpOnly cookies and handled automatically by the browser with each refresh call.
 */
export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  isAuthenticated: false,
  isInitializing: true,

  setAuth: (user: User, accessToken: string) =>
    set({
      user,
      accessToken,
      isAuthenticated: true,
      isInitializing: false,
    }),

  setAccessToken: (accessToken: string) =>
    set({
      accessToken,
      isAuthenticated: true,
    }),

  clearAuth: () =>
    set({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isInitializing: false,
    }),

  setInitializing: (isInitializing: boolean) =>
    set({
      isInitializing,
    }),
}))
