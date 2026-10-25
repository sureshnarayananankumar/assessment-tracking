import { Button, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useNavigate } from 'react-router-dom';

export default function StatusPage({ code }: { code: 403 | 404 }) {
  const theme = useTheme();
  const navigate = useNavigate();
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: '60vh', textAlign: 'center' }}>
      <Typography sx={{ fontSize: 110, fontWeight: 900, lineHeight: 1, background: theme.brand.gradient, WebkitBackgroundClip: 'text', backgroundClip: 'text', color: 'transparent' }}>
        {code}
      </Typography>
      <Typography variant="h5">{code === 403 ? 'You do not have access to this page' : 'Page not found'}</Typography>
      <Typography color="text.secondary">{code === 403 ? 'Ask an administrator if you believe you should.' : 'The link may be broken or the page may have moved.'}</Typography>
      <Button variant="contained" onClick={() => navigate('/')}>Go home</Button>
    </Stack>
  );
}
