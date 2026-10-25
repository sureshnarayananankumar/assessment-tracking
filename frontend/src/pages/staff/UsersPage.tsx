import { AddRounded, BlockRounded, EditRounded, MailRounded, SearchRounded } from '@mui/icons-material';
import {
  Avatar, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton,
  InputAdornment, ListItemText, MenuItem, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import { useState } from 'react';
import { userApi } from '../../api/services';
import type { RoleName, User, UserPayload, UserStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, sortParam, type Column, type SortState } from '../../components/common/DataTable';
import { ConfirmDialog, ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync, useDebounced } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { fromNow, initials } from '../../utils/format';
import { ROLE_LABEL } from '../../utils/labels';

const ALL_ROLES: RoleName[] = ['SUPER_ADMIN', 'ADMIN', 'RECRUITER', 'CANDIDATE'];

export default function UsersPage() {
  const notify = useNotify();
  const { user: me, isSuperAdmin } = useAuth();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<RoleName | ''>('');
  const [status, setStatus] = useState<UserStatus | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ field: 'createdAt', direction: 'desc' });
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [disable, setDisable] = useState<User | null>(null);
  const q = useDebounced(search);
  const { data, loading, error, reload } = useAsync(
    () => userApi.list({ search: q, role, status, page, size, sort: sortParam(sort) }),
    [q, role, status, page, size, sort.field, sort.direction],
  );
  const grantable = isSuperAdmin ? ALL_ROLES : (['RECRUITER', 'CANDIDATE'] as RoleName[]);
  const canManage = (u: User) => isSuperAdmin || !u.roles.some((r) => r === 'SUPER_ADMIN' || r === 'ADMIN');

  const columns: Column<User>[] = [
    {
      key: 'user', label: 'User', sortField: 'fullName', render: (u) => (
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Avatar sx={{ width: 34, height: 34, fontSize: 13, background: (t) => t.brand.gradient }}>{initials(u.fullName)}</Avatar>
          <Box>
            <Typography fontWeight={650}>{u.fullName}</Typography>
            <Typography variant="caption" color="text.secondary">{u.email}</Typography>
          </Box>
        </Stack>
      ),
    },
    { key: 'roles', label: 'Roles', render: (u) => <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>{u.roles.map((r) => <StatusChip key={r} status={r === 'CANDIDATE' ? 'PENDING' : 'ACTIVE'} label={ROLE_LABEL[r]} />)}</Stack> },
    { key: 'status', label: 'Status', sortField: 'status', render: (u) => <StatusChip status={u.status} /> },
    { key: 'dept', label: 'Department', hideBelow: 'lg', render: (u) => u.department ?? '—' },
    { key: 'login', label: 'Last sign-in', hideBelow: 'md', sortField: 'lastLoginAt', render: (u) => (u.lastLoginAt ? fromNow(u.lastLoginAt) : 'Never') },
    {
      key: 'actions', label: '', align: 'right', render: (u) => canManage(u) && (
        <Stack direction="row" spacing={0.5} justifyContent="flex-end">
          <Tooltip title="Edit"><IconButton size="small" onClick={() => setEditing(u)}><EditRounded fontSize="small" /></IconButton></Tooltip>
          {u.status !== 'DISABLED' && (
            <Tooltip title={u.hasPassword ? 'Send password reset' : 'Re-send set-up e-mail'}>
              <IconButton size="small" onClick={async () => {
                try { notify.success((await userApi.sendSetup(u.id)) ?? 'E-mail sent'); } catch (e) { notify.error(errorMessage(e)); }
              }}><MailRounded fontSize="small" /></IconButton>
            </Tooltip>
          )}
          {u.status !== 'DISABLED' && u.id !== me?.id && (
            <Tooltip title="Deactivate"><IconButton size="small" color="error" onClick={() => setDisable(u)}><BlockRounded fontSize="small" /></IconButton></Tooltip>
          )}
        </Stack>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Users" subtitle={isSuperAdmin ? 'Manage administrators, recruiters and candidates.' : 'Manage recruiters and candidates.'}
        actions={<Button variant="contained" startIcon={<AddRounded />} onClick={() => setEditing('new')}>Add user</Button>} />
      <GlassCard noPadding>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2.5 }}>
          <TextField size="small" placeholder="Search name or e-mail…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} sx={{ flex: 1 }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }} />
          <TextField select size="small" label="Role" value={role} onChange={(e) => { setRole(e.target.value as RoleName | ''); setPage(0); }} sx={{ minWidth: 160 }}>
            <MenuItem value="">All roles</MenuItem>
            {ALL_ROLES.map((r) => <MenuItem key={r} value={r}>{ROLE_LABEL[r]}</MenuItem>)}
          </TextField>
          <TextField select size="small" label="Status" value={status} onChange={(e) => { setStatus(e.target.value as UserStatus | ''); setPage(0); }} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="ACTIVE">Active</MenuItem><MenuItem value="INVITED">Invited</MenuItem><MenuItem value="DISABLED">Disabled</MenuItem>
          </TextField>
        </Stack>
        {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
        <DataTable columns={columns} rows={data?.content ?? []} getRowId={(u) => u.id} loading={loading}
          total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }}
          sort={sort} onSortChange={setSort} />
      </GlassCard>
      {editing && (
        <UserDialog user={editing === 'new' ? null : editing} grantable={grantable} onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); reload(); }} />
      )}
      <ConfirmDialog open={Boolean(disable)} title="Deactivate user?" destructive confirmLabel="Deactivate"
        message={<>{disable?.fullName} will be signed out and can no longer sign in. Their history is kept.</>}
        onClose={() => setDisable(null)}
        onConfirm={async () => {
          try { await userApi.deactivate(disable!.id); notify.success('User deactivated'); reload(); } catch (e) { notify.error(errorMessage(e)); }
        }} />
    </>
  );
}

function UserDialog({ user, grantable, onClose, onSaved }: { user: User | null; grantable: RoleName[]; onClose: () => void; onSaved: () => void }) {
  const notify = useNotify();
  const [form, setForm] = useState<UserPayload>({
    email: user?.email ?? '', fullName: user?.fullName ?? '', roles: user?.roles ?? ['RECRUITER'], status: user?.status ?? 'ACTIVE',
    department: user?.department ?? '', jobTitle: user?.jobTitle ?? '', sendSetupEmail: true,
  });
  const [busy, setBusy] = useState(false);
  const emailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email ?? '');
  const valid = form.fullName.trim().length > 0 && form.roles.length > 0 && (user || emailValid);

  const save = async () => {
    setBusy(true);
    try {
      if (user) await userApi.update(user.id, { fullName: form.fullName, roles: form.roles, status: form.status, department: form.department, jobTitle: form.jobTitle });
      else await userApi.create(form);
      notify.success(user ? 'User updated' : form.sendSetupEmail ? 'User created — set-up e-mail sent' : 'User created');
      onSaved();
    } catch (e) {
      notify.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{user ? 'Edit user' : 'Add user'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="E-mail" value={form.email} disabled={Boolean(user)} onChange={(e) => setForm({ ...form, email: e.target.value })}
            error={!user && Boolean(form.email) && !emailValid} />
          <TextField label="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
          <TextField select label="Roles" SelectProps={{ multiple: true, renderValue: (v) => (v as RoleName[]).map((r) => ROLE_LABEL[r]).join(', ') }}
            value={form.roles} onChange={(e) => setForm({ ...form, roles: e.target.value as unknown as RoleName[] })}>
            {ALL_ROLES.map((r) => (
              <MenuItem key={r} value={r} disabled={!grantable.includes(r)}>
                <Checkbox checked={form.roles.includes(r)} size="small" />
                <ListItemText primary={ROLE_LABEL[r]} />
              </MenuItem>
            ))}
          </TextField>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            <TextField label="Department" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} />
            <TextField label="Job title" value={form.jobTitle} onChange={(e) => setForm({ ...form, jobTitle: e.target.value })} />
          </Box>
          {user ? (
            <TextField select label="Status" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as UserStatus })}>
              <MenuItem value="ACTIVE">Active</MenuItem><MenuItem value="INVITED">Invited</MenuItem><MenuItem value="DISABLED">Disabled</MenuItem>
            </TextField>
          ) : (
            <FormControlLabel control={<Checkbox checked={form.sendSetupEmail} onChange={(e) => setForm({ ...form, sendSetupEmail: e.target.checked })} />}
              label="E-mail an account set-up link (the user chooses their own password)" />
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={!valid || busy}>{busy ? 'Saving…' : 'Save'}</Button>
      </DialogActions>
    </Dialog>
  );
}
