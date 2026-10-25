import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { NotificationProvider } from '../components/common/NotificationProvider';
import { AppThemeProvider } from '../theme/ThemeContext';

export function renderWithProviders(ui: ReactElement, { route = '/', ...options }: RenderOptions & { route?: string } = {}) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <AppThemeProvider>
      <NotificationProvider>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </NotificationProvider>
    </AppThemeProvider>
  );
  return render(ui, { wrapper: Wrapper, ...options });
}
