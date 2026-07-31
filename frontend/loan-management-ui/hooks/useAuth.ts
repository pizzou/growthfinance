'use client';
import { useState, useEffect, createContext, useContext } from 'react';
import { AuthResponse } from '@/types';

interface AuthCtx {
  user: AuthResponse | null;
  token: string | null;
  login: (user: AuthResponse, token: string) => void;
  logout: () => void;
  loading: boolean;
  isAdmin: boolean;
  isOfficer: boolean;
  currency: string;
  locale: string;
  mustChangePassword: boolean;
}

export const AuthContext = createContext<AuthCtx>({
  user: null, token: null,
  login: () => {}, logout: () => {},
  loading: true, isAdmin: false, isOfficer: false,
  currency: 'USD', locale: 'en-US', mustChangePassword: false,
});

export function useAuth() { return useContext(AuthContext); }

export function useAuthState() {
  const [user, setUser]   = useState<AuthResponse | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const stored = localStorage.getItem('user');
    const storedToken = localStorage.getItem('token');
    if (stored && storedToken) {
      setUser(JSON.parse(stored));
      setToken(storedToken);
    }
    setLoading(false);
  }, []);

  const login = (userData: AuthResponse, tok: string) => {
    if (!tok || typeof tok !== 'string' || tok.split('.').length !== 3) {
      // Never persist a malformed value — a stray "undefined"/"null" string here
      // gets sent as a real Bearer token on every request afterward and silently
      // breaks the whole session. Fail loudly during development instead.
      console.error('useAuth.login() called with an invalid token:', tok);
      return;
    }
    localStorage.setItem('token', tok);
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData); setToken(tok);
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null); setToken(null);
  };

  return {
    user, token, loading, login, logout,
    isAdmin:   user?.role === 'ADMIN',
    isOfficer: ['ADMIN','LOAN_OFFICER','CREDIT_ANALYST','MANAGER'].includes(user?.role || ''),
    currency:  user?.currency  || 'USD',
    locale:    user?.locale    || 'en-US',
    mustChangePassword: !!user?.mustChangePassword,
  };
}