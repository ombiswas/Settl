import React, { useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { authApi } from '../api/client'
import { Mail, CheckCircle2, AlertCircle, ArrowLeft, RefreshCw } from 'lucide-react'

export const CheckEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams()
  const email = searchParams.get('email') || 'your email'

  const [resendStatus, setResendStatus] = useState<string | null>(null)
  const [isResending, setIsResending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleResend = async () => {
    if (!email || email === 'your email') return
    setIsResending(true)
    setError(null)
    setResendStatus(null)

    try {
      await authApi.resendVerification(email)
      setResendStatus('A new verification email has been dispatched!')
    } catch {
      setError('Could not resend email at this moment. Please check rate limits or try again shortly.')
    } finally {
      setIsResending(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-slate-50 px-4 py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-emerald-100 text-emerald-700 shadow-sm">
          <Mail className="h-8 w-8" />
        </div>
        <h2 className="mt-5 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          Check your email
        </h2>
        <p className="mt-2 text-sm text-slate-600">
          We've sent a verification link to{' '}
          <span className="font-semibold text-slate-900">{email}</span>.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8 text-center space-y-4">
          <p className="text-sm text-slate-600">
            Click the link inside the email to activate your account and access your groups and expenses.
          </p>

          {resendStatus && (
            <div className="flex items-center justify-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs font-medium text-emerald-800">
              <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
              <span>{resendStatus}</span>
            </div>
          )}

          {error && (
            <div className="flex items-center justify-center gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-xs font-medium text-red-800">
              <AlertCircle className="h-4 w-4 shrink-0 text-red-500" />
              <span>{error}</span>
            </div>
          )}

          <div className="pt-2">
            <button
              onClick={handleResend}
              disabled={isResending}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-50 transition"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${isResending ? 'animate-spin' : ''}`} />
              <span>{isResending ? 'Resending...' : 'Resend verification link'}</span>
            </button>
          </div>

          <div className="border-t border-slate-100 pt-4">
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-600 hover:text-emerald-700"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back to sign in</span>
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
