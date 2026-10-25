import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { refreshSession, tokenStore } from '../api/client';
import { authApi } from '../api/services';
import type { AuthResponse, RoleName, User } from '../api/types';

interface AuthContextValue {
  user: User | null;
  initializing: boolean;
  isStaff: boolean;
  isAdmin: boolean;
  isSuperAdmin: boolean;
  hasRole: (...roles: RoleName[]) => boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  setSession: (auth: AuthResponse) => void;
  reloadUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);

  const setSession = useCallback((auth: AuthResponse) => {
    tokenStore.set(auth.accessToken);
    setUser(auth.user);
  }, []);

  // Restore the session from the HttpOnly refresh cookie on page load.
  useEffect(() => {
    let active = true;
    refreshSession().then((auth) => {
      if (!active) return;
      setUser(auth?.user ?? null);
      setInitializing(false);
    });
    const unsubscribe = tokenStore.subscribe((auth) => {
      if (auth === null) setUser(null);
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  // Proactively refresh the short-lived access token before it expires.
  useEffect(() => {
    if (!user) return;
    const id = window.setInterval(() => void refreshSession(), 10 * 60 * 1000);
    return () => window.clearInterval(id);
  }, [user]);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await authApi.login(email, password);
    setSession(auth);
    return auth.user;
  }, [setSession]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      tokenStore.set(null);
      setUser(null);
    }
  }, []);

  const reloadUser = useCallback(async () => {
    setUser(await authApi.me());
  }, []);

  const value = useMemo<AuthContextValue>(() => {
    const roles = user?.roles ?? [];
    const hasRole = (...r: RoleName[]) => r.some((x) => roles.includes(x));
    return {
      user,
      initializing,
      isStaff: hasRole('SUPER_ADMIN', 'ADMIN', 'RECRUITER'),
      isAdmin: hasRole('SUPER_ADMIN', 'ADMIN'),
      isSuperAdmin: hasRole('SUPER_ADMIN'),
      hasRole,
      login,
      logout,
      setSession,
      reloadUser,
    };
  }, [user, initializing, login, logout, setSession, reloadUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
