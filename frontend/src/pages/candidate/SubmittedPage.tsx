import { AssignmentTurnedInRounded, HomeRounded, VisibilityRounded } from '@mui/icons-material';
import { Box, Button, Card, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { ReactNode } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { attemptApi } from '../../api/services';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime } from '../../utils/format';
import { ATTEMPT_STATUS_LABEL } from '../../utils/labels';

export default function SubmittedPage() {
  const { attemptId } = useParams();
  const id = Number(attemptId);
  const theme = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const message = (location.state as { message?: string } | null)?.message;
  const { data } = useAsync(() => attemptApi.results(id), [id]);

  return (
    <Stack alignItems="center" sx={{ py: { xs: 3, md: 8 } }}>
      <Card sx={{ p: { xs: 3, md: 5 }, maxWidth: 620, width: '100%', textAlign: 'center', boxShadow: (t) => `${t.glass.shadow}, ${t.brand.glow}` }}>
        <Box sx={{ width: 84, height: 84, mx: 'auto', borderRadius: '50%', display: 'grid', placeItems: 'center', background: theme.brand.gradient, color: '#fff', boxShadow: theme.brand.glow, position: 'relative' }}>
          <AssignmentTurnedInRounded sx={{ fontSize: 42 }} />
        </Box>
        <Typography variant="h4" sx={{ mt: 3 }}>Submission received</Typography>
        <Typography color="text.secondary" sx={{ mt: 1.5 }}>
          {message ?? 'Thank you! Your answers have been recorded.'}
        </Typography>
        {data && (
          <Stack spacing={1} sx={{ mt: 3, p: 2, borderRadius: 3, border: 1, borderColor: 'divider', textAlign: 'left', position: 'relative' }}>
            <Row label="Assessment" value={data.assignmentTitle} />
            <Row label="Status" value={<StatusChip status={data.status} label={ATTEMPT_STATUS_LABEL[data.status]} />} />
            <Row label="Submitted" value={formatDateTime(data.submittedAt)} />
            <Row label="Result" value={data.released ? 'Available' : 'Pending release'} />
          </Stack>
        )}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="center" sx={{ mt: 4, position: 'relative' }}>
          <Button variant="outlined" startIcon={<HomeRounded />} onClick={() => navigate('/candidate')}>My assessments</Button>
          {data?.released && <Button variant="contained" startIcon={<VisibilityRounded />} onClick={() => navigate(`/candidate/attempts/${id}/result`)}>View result</Button>}
        </Stack>
      </Card>
    </Stack>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography component="div" fontWeight={650} textAlign="right">{value}</Typography>
    </Stack>
  );
}
