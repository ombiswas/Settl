import React, { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { authApi } from '../api/client'
import { CheckCircle, AlertCircle, Loader2, ArrowRight } from 'lucide-react'

export const VerifyEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [loading, setLoading] = useState(true)
  const [success, setSuccess] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      setLoading(false)
      setErrorMessage('No verification token provided in the URL.')
      return
    }

    authApi
      .verifyEmail(token)
      .then(() => {
        setSuccess(true)
      })
      .catch((err: unknown) => {
        if (err && typeof err === 'object' && 'response' in err) {
          const axiosErr = err as { response?: { data?: { message?: string } } }
          setErrorMessage(
            axiosErr.response?.data?.message || 'Verification link is invalid or has expired.'
          )
        } else {
          setErrorMessage('Unable to verify email. Please try again.')
        }
      })
      .finally(() => {
        setLoading(false)
      })
  }, [token])

  return (
    <div className="flex min-h-screen flex-col justify-center bg-slate-50 px-4 py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        {loading ? (
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-emerald-100 text-emerald-700">
            <Loader2 className="h-8 w-8 animate-spin" />
          </div>
        ) : success ? (
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-emerald-100 text-emerald-700 shadow-md shadow-emerald-200">
            <CheckCircle className="h-8 w-8" />
          </div>
        ) : (
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-red-100 text-red-700 shadow-md shadow-red-200">
            <AlertCircle className="h-8 w-8" />
          </div>
        )}

        <h2 className="mt-5 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          {loading
            ? 'Verifying your email...'
            : success
            ? 'Email verified successfully!'
            : 'Verification failed'}
        </h2>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8 text-center space-y-4">
          {loading && (
            <p className="text-sm text-slate-600">
              Please wait while we validate your verification token...
            </p>
          )}

          {success && (
            <>
              <p className="text-sm text-slate-600">
                Your email has been confirmed. You can now sign in to access all group splitting features.
              </p>
              <div className="pt-2">
                <Link
                  to="/login"
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700 transition"
                >
                  <span>Continue to sign in</span>
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            </>
          )}

          {errorMessage && (
            <>
              <p className="text-sm text-red-700 font-medium">
                {errorMessage}
              </p>
              <div className="pt-2">
                <Link
                  to="/login"
                  className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 transition"
                >
                  <span>Go to sign in</span>
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
