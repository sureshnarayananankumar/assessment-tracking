import { SearchRounded } from '@mui/icons-material';
import { Box, InputAdornment, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import { auditApi } from '../../api/services';
import type { AuditLog, EmailNotification } from '../../api/types';
import { DataTable, type Column } from '../../components/common/DataTable';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync, useDebounced } from '../../hooks/useAsync';
import { formatDateTime, fromLocalInput } from '../../utils/format';
import { humanize } from '../../utils/labels';

export default function AuditLogsPage() {
  const [tab, setTab] = useState(0);
  return (
    <>
      <PageHeader title="Audit logs" subtitle="Security-relevant and administrative actions, plus e-mail delivery status." />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2.5 }}>
        <Tab label="Audit trail" />
        <Tab label="E-mail log" />
      </Tabs>
      {tab === 0 ? <AuditTrail /> : <EmailLog />}
    </>
  );
}

function AuditTrail() {
  const [search, setSearch] = useState('');
  const [action, setAction] = useState('');
  const [entityType, setEntityType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const q = useDebounced(search);
  const a = useDebounced(action.trim().toUpperCase());
  const e = useDebounced(entityType.trim().toUpperCase());
  const { data, loading, error, reload } = useAsync(
    () => auditApi.list({ search: q, action: a, entityType: e, from: fromLocalInput(from) ?? undefined, to: fromLocalInput(to) ?? undefined, page, size }),
    [q, a, e, from, to, page, size],
  );

  const columns: Column<AuditLog>[] = [
    { key: 'time', label: 'When', render: (l) => <Typography variant="body2" sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(l.createdAt)}</Typography> },
    { key: 'action', label: 'Action', render: (l) => <StatusChip status={l.action.includes('FAILED') || l.action.includes('LOCK') || l.action.includes('REUSE') ? 'FAILED' : 'PENDING'} label={humanize(l.action)} /> },
    { key: 'actor', label: 'Actor', render: (l) => l.actorEmail ?? 'System' },
    { key: 'entity', label: 'Entity', hideBelow: 'sm', render: (l) => (l.entityType ? `${humanize(l.entityType)} ${l.entityId ?? ''}` : '—') },
    { key: 'details', label: 'Details', hideBelow: 'md', render: (l) => <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 420, wordBreak: 'break-word' }}>{l.details ?? ''}</Typography> },
    { key: 'ip', label: 'IP', hideBelow: 'lg', render: (l) => l.ipAddress ?? '—' },
  ];

  return (
    <GlassCard noPadding>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2.5 }} flexWrap="wrap" useFlexGap>
        <TextField size="small" placeholder="Actor e-mail or entity id…" value={search} onChange={(ev) => { setSearch(ev.target.value); setPage(0); }} sx={{ flex: 1, minWidth: 220 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }} />
        <TextField size="small" label="Action" placeholder="LOGIN_FAILED" value={action} onChange={(ev) => { setAction(ev.target.value); setPage(0); }} />
        <TextField size="small" label="Entity type" placeholder="ASSIGNMENT" value={entityType} onChange={(ev) => { setEntityType(ev.target.value); setPage(0); }} />
        <TextField size="small" type="datetime-local" label="From" InputLabelProps={{ shrink: true }} value={from} onChange={(ev) => { setFrom(ev.target.value); setPage(0); }} />
        <TextField size="small" type="datetime-local" label="To" InputLabelProps={{ shrink: true }} value={to} onChange={(ev) => { setTo(ev.target.value); setPage(0); }} />
      </Stack>
      {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
      <DataTable dense columns={columns} rows={data?.content ?? []} getRowId={(l) => l.id} loading={loading}
        total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />
    </GlassCard>
  );
}

function EmailLog() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const { data, loading, error, reload } = useAsync(() => auditApi.emails({ page, size }), [page, size]);
  const columns: Column<EmailNotification>[] = [
    { key: 'time', label: 'Queued', render: (n) => formatDateTime(n.createdAt) },
    { key: 'to', label: 'Recipient', render: (n) => n.recipientEmail },
    { key: 'template', label: 'Type', render: (n) => humanize(n.template) },
    { key: 'subject', label: 'Subject', hideBelow: 'md', render: (n) => n.subject },
    { key: 'status', label: 'Status', render: (n) => <StatusChip status={n.status} /> },
    { key: 'error', label: 'Error', hideBelow: 'lg', render: (n) => <Typography variant="caption" color="error.main">{n.errorMessage ?? ''}</Typography> },
  ];
  return (
    <GlassCard noPadding subtitle="Message bodies and secure links are never stored.">
      {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
      <DataTable dense columns={columns} rows={data?.content ?? []} getRowId={(n) => n.id} loading={loading}
        total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />
    </GlassCard>
  );
}
