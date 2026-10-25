import { Button, Stack, Typography } from '@mui/material';
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('UI error', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: '60vh', textAlign: 'center', p: 3 }}>
          <Typography variant="h5">Something went wrong on this page.</Typography>
          <Typography color="text.secondary">Your saved work is safe on the server. Reload to continue.</Typography>
          <Button variant="contained" onClick={() => window.location.reload()}>Reload</Button>
        </Stack>
      );
    }
    return this.props.children;
  }
}
