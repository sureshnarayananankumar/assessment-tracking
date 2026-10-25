import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { RoleName } from '../api/types';
import { FullPageLoader } from '../components/common/Loaders';
import { useAuth } from './AuthContext';

/**
 * Client-side route guard for UX only. Every API call is authorised again on the server.
 */
export function RequireAuth({ roles }: { roles?: RoleName[] }) {
  const { user, initializing, hasRole } = useAuth();
  const location = useLocation();

  if (initializing) return <FullPageLoader />;
  if (!user) {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }
  if (roles && !hasRole(...roles)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <Outlet />;
}

/** Sends a signed-in user to the right home page for their role. */
export function HomeRedirect() {
  const { user, initializing, isStaff } = useAuth();
  if (initializing) return <FullPageLoader />;
  if (!user) return <Navigate to="/login" replace />;
  return <Navigate to={isStaff ? '/dashboard' : '/candidate'} replace />;
}
