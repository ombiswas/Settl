import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { authApi } from '../api/client'
import { Wallet, Loader2, ArrowRight, AlertCircle } from 'lucide-react'

const registerSchema = z.object({
  displayName: z
    .string()
    .min(2, 'Name must be at least 2 characters')
    .max(100, 'Name cannot exceed 100 characters'),
  email: z.string().email('Please enter a valid email address'),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .regex(/[A-Z]/, 'Password must include at least one uppercase letter')
    .regex(/[a-z]/, 'Password must include at least one lowercase letter')
    .regex(/[0-9]/, 'Password must include at least one digit'),
})

type RegisterFormValues = z.infer<typeof registerSchema>

export const RegisterPage: React.FC = () => {
  const [serverError, setServerError] = useState<string | null>(null)
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  })

  const passwordVal = watch('password', '')

  const getStrength = (pwd: string) => {
    let score = 0
    if (pwd.length >= 8) score++
    if (/[A-Z]/.test(pwd)) score++
    if (/[0-9]/.test(pwd)) score++
    if (/[^A-Za-z0-9]/.test(pwd)) score++
    return score
  }

  const strength = getStrength(passwordVal)

  const onSubmit = async (values: RegisterFormValues) => {
    setServerError(null)

    try {
      await authApi.register(values)
      navigate(`/check-email?email=${encodeURIComponent(values.email)}`)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setServerError(axiosErr.response?.data?.message || 'Registration failed. Please try again.')
      } else {
        setServerError('Unable to connect to the registration service.')
      }
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-slate-50 px-4 py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-600 text-white shadow-md shadow-emerald-200">
          <Wallet className="h-7 w-7" />
        </div>
        <h2 className="mt-4 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          Create your account
        </h2>
        <p className="mt-1.5 text-sm text-slate-600">
          Join Settl to split group expenses and track your finances effortlessly.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          {serverError && (
            <div className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
              <div className="flex items-center gap-2.5">
                <AlertCircle className="h-5 w-5 shrink-0 text-red-500" />
                <p className="font-medium">{serverError}</p>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                Full Name
              </label>
              <input
                type="text"
                autoComplete="name"
                {...register('displayName')}
                className={`mt-1.5 block w-full rounded-xl border px-3.5 py-2.5 text-sm transition focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
                  errors.displayName ? 'border-red-300 bg-red-50/30' : 'border-slate-300'
                }`}
                placeholder="Jane Doe"
              />
              {errors.displayName && (
                <p className="mt-1 text-xs text-red-600">{errors.displayName.message}</p>
              )}
            </div>

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
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                Password
              </label>
              <input
                type="password"
                autoComplete="new-password"
                {...register('password')}
                className={`mt-1.5 block w-full rounded-xl border px-3.5 py-2.5 text-sm transition focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
                  errors.password ? 'border-red-300 bg-red-50/30' : 'border-slate-300'
                }`}
                placeholder="At least 8 chars, uppercase, digit"
              />
              {errors.password && (
                <p className="mt-1 text-xs text-red-600">{errors.password.message}</p>
              )}

              {/* Password strength indicator */}
              {passwordVal.length > 0 && (
                <div className="mt-2.5">
                  <div className="flex h-1.5 gap-1.5 overflow-hidden rounded-full bg-slate-100">
                    <div className={`h-full flex-1 transition-all ${strength >= 1 ? 'bg-red-500' : 'bg-transparent'}`} />
                    <div className={`h-full flex-1 transition-all ${strength >= 2 ? 'bg-amber-500' : 'bg-transparent'}`} />
                    <div className={`h-full flex-1 transition-all ${strength >= 3 ? 'bg-emerald-500' : 'bg-transparent'}`} />
                    <div className={`h-full flex-1 transition-all ${strength >= 4 ? 'bg-emerald-600' : 'bg-transparent'}`} />
                  </div>
                  <p className="mt-1 text-[11px] text-slate-500">
                    {strength <= 1 ? 'Weak' : strength === 2 ? 'Fair' : strength === 3 ? 'Good' : 'Strong'} password
                  </p>
                </div>
              )}
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2 disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Creating account...</span>
                </>
              ) : (
                <>
                  <span>Create account</span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          <div className="mt-6 border-t border-slate-100 pt-5 text-center text-sm text-slate-600">
            Already have an account?{' '}
            <Link to="/login" className="font-semibold text-emerald-600 hover:text-emerald-700">
              Sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
