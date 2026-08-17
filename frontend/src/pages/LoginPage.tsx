import React, { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { authApi } from '../api/client'
import { useAuthStore } from '../store/authStore'
import { Wallet, Loader2, ArrowRight, AlertCircle, CheckCircle2 } from 'lucide-react'

const loginSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
})

type LoginFormValues = z.infer<typeof loginSchema>

export const LoginPage: React.FC = () => {
  const [serverError, setServerError] = useState<string | null>(null)
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null)
  const [resendStatus, setResendStatus] = useState<string | null>(null)
  const [isResending, setIsResending] = useState(false)

  const navigate = useNavigate()
  const location = useLocation()
  const { setAuth } = useAuthStore()

  const from = location.state?.from?.pathname || '/groups'

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  })

  const onSubmit = async (values: LoginFormValues) => {
    setServerError(null)
    setUnverifiedEmail(null)
    setResendStatus(null)

    try {
      const res = await authApi.login(values)
      setAuth(res.data.data.user, res.data.data.accessToken)
      navigate(from, { replace: true })
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string; errors?: Record<string, string> } } }
        const msg = axiosErr.response?.data?.message || 'Invalid email or password'
        setServerError(msg)

        if (msg.toLowerCase().includes('verify your email')) {
          setUnverifiedEmail(values.email)
        }
      } else {
        setServerError('Unable to connect to the authentication service. Please check your connection.')
      }
    }
  }

  const handleResend = async () => {
    if (!unverifiedEmail) return
    setIsResending(true)
    try {
      await authApi.resendVerification(unverifiedEmail)
      setResendStatus('Verification email resent! Please check your inbox.')
    } catch {
      setResendStatus('Failed to resend email. Please try again shortly.')
    } finally {
      setIsResending(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-slate-50 px-4 py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-600 text-white shadow-md shadow-emerald-200">
          <Wallet className="h-7 w-7" />
        </div>
        <h2 className="mt-4 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          Welcome back to Settl
        </h2>
        <p className="mt-1.5 text-sm text-slate-600">
          Simplify shared debts, track personal expenses, and settle up easily.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          {serverError && (
            <div className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
              <div className="flex items-start gap-2.5">
                <AlertCircle className="h-5 w-5 shrink-0 text-red-500" />
                <div className="flex-1">
                  <p className="font-medium">{serverError}</p>
                  {unverifiedEmail && (
                    <button
                      type="button"
                      onClick={handleResend}
                      disabled={isResending}
                      className="mt-2 inline-flex items-center text-xs font-semibold text-emerald-700 underline hover:text-emerald-800"
                    >
                      {isResending ? 'Sending...' : 'Click here to resend verification link'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}

          {resendStatus && (
            <div className="mb-5 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3.5 text-xs font-medium text-emerald-800">
              <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
              <span>{resendStatus}</span>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                Email address
              </label>
              <input
                type="email"
                autoComplete="email"
                {...register('email')}
                className={`mt-1.5 block w-full rounded-xl border px-3.5 py-2.5 text-sm transition focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
                  errors.email ? 'border-red-300 bg-red-50/30' : 'border-slate-300'
                }`}
                placeholder="you@example.com"
              />
              {errors.email && (
                <p className="mt-1 text-xs text-red-600">{errors.email.message}</p>
              )}
            </div>

            <div>
              <div className="flex items-center justify-between">
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Password
                </label>
              </div>
              <input
                type="password"
                autoComplete="current-password"
                {...register('password')}
                className={`mt-1.5 block w-full rounded-xl border px-3.5 py-2.5 text-sm transition focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
                  errors.password ? 'border-red-300 bg-red-50/30' : 'border-slate-300'
                }`}
                placeholder="••••••••"
              />
              {errors.password && (
                <p className="mt-1 text-xs text-red-600">{errors.password.message}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2 disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Signing in...</span>
                </>
              ) : (
                <>
                  <span>Sign in</span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          <div className="mt-6 border-t border-slate-100 pt-5 text-center text-sm text-slate-600">
            Don't have an account?{' '}
            <Link to="/register" className="font-semibold text-emerald-600 hover:text-emerald-700">
              Create an account
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
