import { Alert, Snackbar, type AlertColor } from '@mui/material';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

interface Notice {
  id: number;
  message: string;
  severity: AlertColor;
  duration: number;
}

interface NotifyApi {
  notify: (message: string, severity?: AlertColor, duration?: number) => void;
  success: (message: string) => void;
  error: (message: string) => void;
  warning: (message: string) => void;
  info: (message: string) => void;
}

const NotificationContext = createContext<NotifyApi | null>(null);

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [queue, setQueue] = useState<Notice[]>([]);
  const current = queue[0];

  const notify = useCallback((message: string, severity: AlertColor = 'info', duration = 4500) => {
    setQueue((q) => [...q, { id: Date.now() + Math.random(), message, severity, duration }]);
  }, []);

  const api = useMemo<NotifyApi>(
    () => ({
      notify,
      success: (m) => notify(m, 'success'),
      error: (m) => notify(m, 'error', 6500),
      warning: (m) => notify(m, 'warning', 6500),
      info: (m) => notify(m, 'info'),
    }),
    [notify],
  );

  const close = () => setQueue((q) => q.slice(1));

  return (
    <NotificationContext.Provider value={api}>
      {children}
      <Snackbar
        key={current?.id}
        open={Boolean(current)}
        autoHideDuration={current?.duration}
        onClose={(_, reason) => reason !== 'clickaway' && close()}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {current ? (
          <Alert onClose={close} severity={current.severity} variant="filled" sx={{ minWidth: 300, boxShadow: 6 }}>
            {current.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotificationContext.Provider>
  );
}

export function useNotify(): NotifyApi {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error('useNotify must be used inside NotificationProvider');
  return ctx;
}
