import { SaveRounded } from '@mui/icons-material';
import { Alert, Box, Button, FormControlLabel, MenuItem, Stack, Switch, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { settingsApi } from '../../api/services';
import { useAuth } from '../../auth/AuthContext';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { AppearanceOptions } from '../../components/layout/ThemeControls';
import { useAsync } from '../../hooks/useAsync';
import { useThemeSettings } from '../../theme/ThemeContext';
import { errorMessage } from '../../utils/errors';
import { formatDateTime } from '../../utils/format';

const ENUM_OPTIONS: Record<string, string[]> = {
  'monitoring.default.action': ['WARN_ONLY', 'FLAG_FOR_REVIEW', 'AUTO_SUBMIT'],
  'results.default.release.policy': ['AFTER_GRADING', 'MANUAL'],
  'email.reminders.enabled': ['true', 'false'],
};

export default function SettingsPage() {
  const { isStaff, isSuperAdmin } = useAuth();
  const { reduceMotion, setReduceMotion } = useThemeSettings();
  return (
    <>
      <PageHeader title="Settings" subtitle="Personalise the interface and configure platform defaults." />
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '420px 1fr' }, alignItems: 'start' }}>
        <GlassCard title="Appearance" subtitle="Saved in this browser">
          <Stack spacing={2.5}>
            <AppearanceOptions />
            <FormControlLabel control={<Switch checked={reduceMotion} onChange={(e) => setReduceMotion(e.target.checked)} />}
              label="Reduce motion (static background)" />
          </Stack>
        </GlassCard>
        {isStaff && <PlatformSettings editable={isSuperAdmin} />}
      </Box>
    </>
  );
}

function PlatformSettings({ editable }: { editable: boolean }) {
  const notify = useNotify();
  const { data, loading, setData } = useAsync(() => settingsApi.all(), []);
  const [values, setValues] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (data) setValues(Object.fromEntries(data.map((s) => [s.key, s.value])));
  }, [data]);

  if (loading || !data) return <SectionLoader rows={6} />;

  const changed = Object.fromEntries(Object.entries(values).filter(([k, v]) => data.find((s) => s.key === k)?.value !== v));

  const save = async () => {
    setSaving(true);
    try {
      setData(await settingsApi.update(changed));
      notify.success('Settings saved');
    } catch (e) {
      notify.error(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <GlassCard title="Platform defaults" subtitle="Applied to new assessments and invitations"
      action={editable && <Button variant="contained" startIcon={<SaveRounded />} disabled={!Object.keys(changed).length || saving} onClick={save}>Save</Button>}>
      {!editable && <Alert severity="info" sx={{ mb: 2 }}>Only a Super Admin can change platform settings.</Alert>}
      <Stack spacing={2}>
        {data.map((s) => (
          <Box key={s.key} sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: '1fr 260px' }, alignItems: 'center' }}>
            <Box>
              <Typography fontWeight={650}>{s.description ?? s.key}</Typography>
              <Typography variant="caption" color="text.secondary">{s.key}{s.updatedAt ? ` · updated ${formatDateTime(s.updatedAt)}` : ''}</Typography>
            </Box>
            {ENUM_OPTIONS[s.key] ? (
              <TextField select size="small" value={values[s.key] ?? ''} disabled={!editable} onChange={(e) => setValues({ ...values, [s.key]: e.target.value })}>
                {ENUM_OPTIONS[s.key].map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
              </TextField>
            ) : (
              <TextField size="small" value={values[s.key] ?? ''} disabled={!editable} onChange={(e) => setValues({ ...values, [s.key]: e.target.value })} />
            )}
          </Box>
        ))}
      </Stack>
    </GlassCard>
  );
}
