import React, { useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { invitationsApi, authApi } from '../api/client'
import { useAuthStore } from '../store/authStore'
import {
  Users,
  ShieldCheck,
  Loader2,
  CheckCircle,
  AlertTriangle,
  Lock,
  ArrowRight,
  ArrowLeft,
  Mail,
  User as UserIcon,
} from 'lucide-react'

const joinSchema = z.object({
  displayName: z
    .string()
    .min(2, 'Name must be at least 2 characters')
    .max(100, 'Name cannot exceed 100 characters'),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .regex(/[A-Z]/, 'Password must include at least one uppercase letter')
    .regex(/[a-z]/, 'Password must include at least one lowercase letter')
    .regex(/[0-9]/, 'Password must include at least one digit'),
})

type JoinFormValues = z.infer<typeof joinSchema>

export const JoinGroupPage: React.FC = () => {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''
  const navigate = useNavigate()
  const { user, setAuth } = useAuthStore()
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    data: previewData,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['invitationPreview', token],
    queryFn: async () => {
      if (!token) throw new Error('No invitation token provided')
      const res = await invitationsApi.preview(token)
      return res.data.data
    },
    enabled: !!token,
    retry: false,
  })

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<JoinFormValues>({
    resolver: zodResolver(joinSchema),
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

  // Acceptance for already authenticated users
  const acceptExistingMutation = useMutation({
    mutationFn: async () => {
      const res = await invitationsApi.accept(token)
      return res.data.data
    },
    onSuccess: (joinedGroup) => {
      navigate(`/groups/${joinedGroup.id}`)
    },
    onError: (err: unknown) => {
      let msg = 'Failed to accept invitation. Please try again.'
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        msg = axiosErr.response?.data?.message || msg
      }
      setServerError(msg)
    },
  })

  // Submit registration with token for new users
  const onNewUserSubmit = async (values: JoinFormValues) => {
    if (!previewData) return
    setServerError(null)

    try {
      // 1. Register with inviteToken
      await authApi.register({
        email: previewData.email,
        password: values.password,
        displayName: values.displayName,
        inviteToken: token,
      })

      // 2. Automatically log in
      const loginRes = await authApi.login({
        email: previewData.email,
        password: values.password,
      })

      const authData = loginRes.data.data
      setAuth(authData.user, authData.accessToken)

      // 3. Navigate directly to group
      navigate(`/groups/${previewData.groupId}`)
    } catch (err: unknown) {
      let msg = 'Failed to create account. Please try again.'
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        msg = axiosErr.response?.data?.message || msg
      }
      setServerError(msg)
    }
  }

  if (!token) {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-100 text-amber-600 mb-4">
            <AlertTriangle className="h-6 w-6" />
          </div>
          <h2 className="text-xl font-bold text-slate-900">Missing Invitation Token</h2>
          <p className="mt-2 text-sm text-slate-600">
            This invitation link is invalid or incomplete. Please check your invitation email and click the complete link.
          </p>
          <Link
            to="/login"
            className="mt-6 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 transition"
          >
            Go to Login
          </Link>
        </div>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <div className="text-center">
          <Loader2 className="mx-auto h-8 w-8 animate-spin text-emerald-600 mb-2" />
          <p className="text-sm font-medium text-slate-600">Loading invitation details...</p>
        </div>
      </div>
    )
  }

  if (isError || !previewData) {
    let errorMsg = 'This invitation could not be found or has been revoked.'
    if (error && typeof error === 'object' && 'response' in error) {
      const axiosErr = error as { response?: { data?: { message?: string } } }
      errorMsg = axiosErr.response?.data?.message || errorMsg
    }

    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600 mb-4">
            <AlertTriangle className="h-6 w-6" />
          </div>
          <h2 className="text-xl font-bold text-slate-900">Invitation Unavailable</h2>
          <p className="mt-2 text-sm text-slate-600">{errorMsg}</p>
          <div className="mt-6 flex justify-center gap-3">
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
            >
              Sign In
            </Link>
            <Link
              to="/register"
              className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-700 transition"
            >
              Register New Account
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (previewData.isExpired) {
    return (
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-100 text-amber-600 mb-4">
            <AlertTriangle className="h-6 w-6" />
          </div>
          <h2 className="text-xl font-bold text-slate-900">Invitation Expired</h2>
          <p className="mt-2 text-sm text-slate-600">
            This invitation to join <strong>{previewData.groupName}</strong> expired. Please contact{' '}
            <strong>{previewData.inviterName}</strong> to request a new invite link.
          </p>
          <Link
            to="/login"
            className="mt-6 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 transition"
          >
            Go to Settl
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-4 sm:p-6 lg:p-8">
      <div className="w-full max-w-lg">
        {/* Main Card */}
        <div className="overflow-hidden rounded-3xl border border-slate-200/80 bg-white shadow-xl">
          {/* Header Banner */}
          <div className="bg-gradient-to-br from-emerald-600 via-emerald-700 to-teal-800 p-8 text-white text-center relative overflow-hidden">
            <div className="absolute inset-0 opacity-10 bg-[radial-gradient(#fff_1px,transparent_1px)] [background-size:16px_16px]" />
            <div className="relative z-10">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-white/10 backdrop-blur-sm text-white mb-4 border border-white/20 shadow-inner">
                <Users className="h-7 w-7" />
              </div>
              <p className="text-xs font-semibold uppercase tracking-wider text-emerald-200">
                You're Invited!
              </p>
              <h1 className="mt-1 text-2xl sm:text-3xl font-bold tracking-tight">
                {previewData.groupName}
              </h1>
              <p className="mt-2 text-xs sm:text-sm text-emerald-100 max-w-md mx-auto">
                <strong>{previewData.inviterName}</strong> invited you to join this shared expense group in{' '}
                <span className="font-bold uppercase tracking-wider">{previewData.defaultCurrency}</span>.
              </p>
              {previewData.isAdmin && (
                <div className="mt-3 inline-flex items-center gap-1 rounded-full bg-emerald-500/30 px-3 py-1 text-[11px] font-bold text-white border border-emerald-300/30">
                  <ShieldCheck className="h-3.5 w-3.5" />
                  <span>Includes Group Admin Privileges</span>
                </div>
              )}
            </div>
          </div>

          <div className="p-6 sm:p-8">
            {/* If user is ALREADY logged in */}
            {user ? (
              <div className="space-y-6 text-center">
                <div className="rounded-2xl border border-slate-200 bg-slate-50/50 p-4 text-left text-xs">
                  <p className="text-slate-500">Currently signed in as:</p>
                  <p className="font-semibold text-slate-900 mt-0.5">
                    {user.displayName} ({user.email})
                  </p>
                </div>

                {serverError && (
                  <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs font-semibold text-red-700">
                    {serverError}
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => acceptExistingMutation.mutate()}
                  disabled={acceptExistingMutation.isPending}
                  className="w-full inline-flex min-h-[46px] items-center justify-center gap-2 rounded-xl bg-emerald-600 px-5 py-3 text-sm font-semibold text-white shadow-md hover:bg-emerald-700 disabled:opacity-50 transition cursor-pointer"
                >
                  {acceptExistingMutation.isPending ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      <span>Joining Group...</span>
                    </>
                  ) : (
                    <>
                      <span>Accept Invitation & View Group</span>
                      <ArrowRight className="h-4 w-4" />
                    </>
                  )}
                </button>
              </div>
            ) : (
              /* If user is NEW / NOT logged in */
              <div>
                <div className="mb-6">
                  <h2 className="text-base font-bold text-slate-900">
                    Create Your Account to Accept
                  </h2>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Enter your details below. You'll be joined to <strong>{previewData.groupName}</strong> immediately.
                  </p>
                </div>

                {serverError && (
                  <div className="mb-5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs font-semibold text-red-700">
                    {serverError}
                  </div>
                )}

                <form onSubmit={handleSubmit(onNewUserSubmit)} className="space-y-4">
                  {/* Email (Readonly) */}
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                      Invited Email
                    </label>
                    <div className="relative mt-1.5">
                      <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                        <Mail className="h-4 w-4" />
                      </div>
                      <input
                        type="email"
                        value={previewData.email}
                        disabled
                        className="block w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-10 py-2.5 text-sm font-medium text-slate-600 cursor-not-allowed"
                      />
                      <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3 text-slate-400" title="Locked to invited email">
                        <Lock className="h-3.5 w-3.5 text-slate-400" />
                      </div>
                    </div>
                  </div>

                  {/* Display Name */}
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                      Your Full Name
                    </label>
                    <div className="relative mt-1.5">
                      <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                        <UserIcon className="h-4 w-4" />
                      </div>
                      <input
                        type="text"
                        {...register('displayName')}
                        placeholder="e.g. Alex Johnson"
                        className={`block w-full rounded-xl border pl-10 pr-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
                          errors.displayName ? 'border-red-300 bg-red-50/30' : 'border-slate-300'
                        }`}
                      />
                    </div>
                    {errors.displayName && (
                      <p className="mt-1 text-xs text-red-600">{errors.displayName.message}</p>
                    )}
                  </div>

                  {/* Password */}
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                      Create Password
                    </label>
                    <div className="relative mt-1.5">
                      <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                        <Lock className="h-4 w-4" />
                      </div>
                      <input
                        type="password"
                        {...register('password')}
                        placeholder="At least 8 characters"
                        className={`block w-full rounded-xl border pl-10 pr-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
                          errors.password ? 'border-red-300 bg-red-50/30' : 'border-slate-300'
                        }`}
                      />
                    </div>
                    {errors.password && (
                      <p className="mt-1 text-xs text-red-600">{errors.password.message}</p>
                    )}

                    {/* Password Strength Indicator */}
                    {passwordVal && (
                      <div className="mt-2">
                        <div className="flex gap-1">
                          {[1, 2, 3, 4].map((step) => (
                            <div
                              key={step}
                              className={`h-1.5 flex-1 rounded-full transition-colors ${
                                strength >= step
                                  ? strength <= 2
                                    ? 'bg-amber-400'
                                    : 'bg-emerald-500'
                                  : 'bg-slate-200'
                              }`}
                            />
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="mt-6 w-full inline-flex min-h-[46px] items-center justify-center gap-2 rounded-xl bg-emerald-600 px-5 py-3 text-sm font-semibold text-white shadow-md hover:bg-emerald-700 disabled:opacity-50 transition cursor-pointer"
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" />
                        <span>Setting up your account...</span>
                      </>
                    ) : (
                      <>
                        <span>Join {previewData.groupName}</span>
                        <ArrowRight className="h-4 w-4" />
                      </>
                    )}
                  </button>
                </form>

                <div className="mt-6 text-center text-xs text-slate-500 border-t border-slate-100 pt-4">
                  Already have an account under a different email?{' '}
                  <Link
                    to={`/login?redirect=${encodeURIComponent(`/join?token=${token}`)}`}
                    className="font-semibold text-emerald-600 hover:text-emerald-700"
                  >
                    Log In Here
                  </Link>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
