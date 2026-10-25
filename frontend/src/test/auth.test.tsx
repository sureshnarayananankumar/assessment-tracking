import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { User } from '../api/types';
import { RequireAuth } from '../auth/RequireAuth';
import LoginPage from '../pages/auth/LoginPage';
import { renderWithProviders } from './utils';

const authState = {
  user: null as User | null,
  initializing: false,
  isStaff: false,
  login: vi.fn(),
  hasRole: (...roles: string[]) => roles.some((r) => authState.user?.roles.includes(r as never)),
};

vi.mock('../auth/AuthContext', () => ({ useAuth: () => authState }));

const recruiter: User = {
  id: 1, email: 'r@acme.io', fullName: 'Riley Recruiter', status: 'ACTIVE', emailVerified: true,
  roles: ['RECRUITER'], hasPassword: true, createdAt: '2026-01-01T00:00:00Z',
};

describe('RequireAuth', () => {
  beforeEach(() => {
    authState.user = null;
  });

  const tree = (
    <Routes>
      <Route element={<RequireAuth roles={['SUPER_ADMIN', 'ADMIN']} />}>
        <Route path="/users" element={<div>admin area</div>} />
      </Route>
      <Route path="/login" element={<div>login screen</div>} />
      <Route path="/forbidden" element={<div>forbidden screen</div>} />
    </Routes>
  );

  it('redirects anonymous users to the login page', () => {
    renderWithProviders(tree, { route: '/users' });
    expect(screen.getByText('login screen')).toBeInTheDocument();
  });

  it('blocks users without the required role', () => {
    authState.user = recruiter;
    renderWithProviders(tree, { route: '/users' });
    expect(screen.getByText('forbidden screen')).toBeInTheDocument();
  });

  it('renders the route for permitted roles', () => {
    authState.user = { ...recruiter, roles: ['ADMIN'] };
    renderWithProviders(tree, { route: '/users' });
    expect(screen.getByText('admin area')).toBeInTheDocument();
  });
});

describe('LoginPage', () => {
  beforeEach(() => {
    authState.user = null;
    authState.login.mockReset();
  });

  it('validates input before calling the API', async () => {
    renderWithProviders(<LoginPage />, { route: '/login' });
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('E-mail is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(authState.login).not.toHaveBeenCalled();
  });

  it('submits credentials and shows server errors', async () => {
    authState.login.mockRejectedValueOnce(new Error('Invalid e-mail or password'));
    renderWithProviders(<LoginPage />, { route: '/login' });
    await userEvent.type(screen.getByLabelText(/e-mail/i), 'r@acme.io');
    await userEvent.type(screen.getByLabelText(/^password/i), 'Secret123');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => expect(authState.login).toHaveBeenCalledWith('r@acme.io', 'Secret123'));
    expect(await screen.findByText('Invalid e-mail or password')).toBeInTheDocument();
  });
});
