import React, { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { authApi } from '../../api/client'
import {
  Wallet,
  Users,
  PieChart,
  LogOut,
  Menu,
  X,
  CreditCard,
} from 'lucide-react'

export const Navbar: React.FC = () => {
  const { user, isAuthenticated, clearAuth } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch {
      // ignore
    } finally {
      clearAuth()
      navigate('/login')
    }
  }

  const navLinks = [
    { name: 'Groups', path: '/groups', icon: Users },
    { name: 'Personal Expenses', path: '/expenses/personal', icon: CreditCard },
    { name: 'Spending Analytics', path: '/analytics', icon: PieChart },
  ]

  if (!isAuthenticated) {
    return null
  }

  return (
    <header className="sticky top-0 z-40 w-full border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3 sm:px-6">
        {/* Brand */}
        <div className="flex items-center gap-8">
          <Link to="/groups" className="flex items-center gap-2 text-slate-900 transition hover:opacity-90">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-sm shadow-emerald-200">
              <Wallet className="h-5 w-5" />
            </div>
            <span className="text-xl font-bold tracking-tight text-slate-900">
              Settl<span className="text-emerald-600">.</span>
            </span>
          </Link>

          {/* Desktop Navigation */}
          <nav className="hidden md:flex items-center gap-1">
            {navLinks.map((link) => {
              const Icon = link.icon
              const isActive = location.pathname.startsWith(link.path)
              return (
                <Link
                  key={link.path}
                  to={link.path}
                  className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition ${
                    isActive
                      ? 'bg-emerald-50 text-emerald-700'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {link.name}
                </Link>
              )
            })}
          </nav>
        </div>

        {/* User Profile & Actions */}
        <div className="hidden md:flex items-center gap-3">
          <div className="flex items-center gap-2.5 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs text-slate-700">
            <div className="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 font-semibold text-emerald-800 uppercase">
              {user?.displayName?.charAt(0) || 'U'}
            </div>
            <span className="font-medium">{user?.displayName}</span>
          </div>

          <button
            onClick={handleLogout}
            title="Log out"
            className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 hover:text-red-600"
          >
            <LogOut className="h-3.5 w-3.5" />
            <span>Logout</span>
          </button>
        </div>

        {/* Mobile Menu Toggle */}
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="flex p-2 text-slate-600 md:hidden hover:text-slate-900"
          aria-label="Toggle Navigation"
        >
          {mobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
        </button>
      </div>

      {/* Mobile Drawer Menu */}
      {mobileMenuOpen && (
        <div className="border-b border-slate-200 bg-white px-4 py-3 md:hidden">
          <div className="mb-3 flex items-center gap-3 border-b border-slate-100 pb-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-100 font-semibold text-emerald-800 uppercase">
              {user?.displayName?.charAt(0) || 'U'}
            </div>
            <div>
              <p className="text-sm font-semibold text-slate-900">{user?.displayName}</p>
              <p className="text-xs text-slate-500">{user?.email}</p>
            </div>
          </div>

          <nav className="flex flex-col gap-1">
            {navLinks.map((link) => {
              const Icon = link.icon
              const isActive = location.pathname.startsWith(link.path)
              return (
                <Link
                  key={link.path}
                  to={link.path}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium ${
                    isActive
                      ? 'bg-emerald-50 text-emerald-700'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {link.name}
                </Link>
              )
            })}

            <button
              onClick={() => {
                setMobileMenuOpen(false)
                handleLogout()
              }}
              className="mt-2 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-red-600 hover:bg-red-50"
            >
              <LogOut className="h-4 w-4" />
              Sign Out
            </button>
          </nav>
        </div>
      )}
    </header>
  )
}
