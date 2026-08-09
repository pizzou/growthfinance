import { post } from './api';
import { AuthResponse } from '../types/index';

export async function login(email: string, password: string): Promise<AuthResponse> {
  // post() already unwraps ApiResponse.data, so we get the inner object directly
  const data = await post('/auth/login', { email, password }) as AuthResponse;
  if (typeof window !== 'undefined') {
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data));
    document.cookie = `token=${data.token}; path=/; max-age=86400; SameSite=Lax`;
  }
  return data;
}

export function logout() {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    document.cookie = 'token=; path=/; max-age=0';
    window.location.href = '/login';
  }
}

export function getCurrentUser(): AuthResponse | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw) : null;
  } catch { return null; }
}

export function hasRole(...roles: string[]): boolean {
  const user = getCurrentUser();
  return user ? roles.includes(user.role) : false;
}
