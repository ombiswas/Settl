import React, { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { authApi } from '../../api/client'
import { Loader2 } from 'lucide-react'

export const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isInitializing, setAuth, clearAuth, setInitializing } = useAuthStore()
  const location = useLocation()

  useEffect(() => {
    if (isInitializing) {
      // Attempt silent session recovery via httpOnly refresh cookie
      authApi
        .refresh()
        .then((res) => {
          setAuth(res.data.data.user, res.data.data.accessToken)
        })
        .catch(() => {
          clearAuth()
        })
        .finally(() => {
          setInitializing(false)
        })
    }
  }, [isInitializing, setAuth, clearAuth, setInitializing])

  if (isInitializing) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
          <p className="text-sm font-medium text-slate-500">Initializing session...</p>
        </div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
