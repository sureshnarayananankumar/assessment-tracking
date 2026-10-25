import {
  AssignmentTurnedInRounded, BlockRounded, ContentCopyRounded, ContentPasteRounded, FlagRounded, PlayArrowRounded,
  ReplayRounded, TimerOffRounded, VisibilityOffRounded, VisibilityRounded, WarningAmberRounded, WebAssetOffRounded,
  WebAssetRounded,
} from '@mui/icons-material';
import { Box, Chip, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import type { ActivityEventType, ActivityLog } from '../../api/types';
import { formatMs, formatTime } from '../../utils/format';

const META: Record<ActivityEventType, { label: string; icon: ReactNode; color: string }> = {
  STARTED: { label: 'Started attempt', icon: <PlayArrowRounded />, color: 'success.main' },
  RESUMED: { label: 'Resumed (reload / reconnect)', icon: <ReplayRounded />, color: 'info.main' },
  TAB_HIDDEN: { label: 'Assessment tab hidden', icon: <VisibilityOffRounded />, color: 'warning.main' },
  TAB_VISIBLE: { label: 'Returned to tab', icon: <VisibilityRounded />, color: 'text.secondary' },
  WINDOW_BLUR: { label: 'Window lost focus', icon: <WebAssetOffRounded />, color: 'warning.main' },
  WINDOW_FOCUS: { label: 'Window focused again', icon: <WebAssetRounded />, color: 'text.secondary' },
  FULLSCREEN_EXIT: { label: 'Exited full screen', icon: <WebAssetOffRounded />, color: 'warning.main' },
  COPY_ATTEMPT: { label: 'Copy', icon: <ContentCopyRounded />, color: 'text.secondary' },
  PASTE_ATTEMPT: { label: 'Paste', icon: <ContentPasteRounded />, color: 'text.secondary' },
  WARNING_SHOWN: { label: 'Warning shown', icon: <WarningAmberRounded />, color: 'warning.main' },
  SUBMITTED: { label: 'Submitted by candidate', icon: <AssignmentTurnedInRounded />, color: 'success.main' },
  AUTO_SUBMITTED: { label: 'Auto-submitted (time up)', icon: <TimerOffRounded />, color: 'warning.main' },
  TERMINATED: { label: 'Attempt ended by policy / revoke', icon: <BlockRounded />, color: 'error.main' },
  FLAGGED: { label: 'Flagged for review', icon: <FlagRounded />, color: 'error.main' },
};

export function ActivityTimeline({ events }: { events: ActivityLog[] }) {
  if (!events.length) return <Typography color="text.secondary">No activity recorded.</Typography>;
  return (
    <Stack spacing={0} sx={{ position: 'relative' }}>
      {events.map((e, i) => {
        const meta = META[e.eventType];
        return (
          <Stack key={e.id} direction="row" spacing={1.5} sx={{ position: 'relative', pb: i === events.length - 1 ? 0 : 1.75 }}>
            <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <Box sx={{ width: 32, height: 32, borderRadius: '50%', display: 'grid', placeItems: 'center', color: meta.color, bgcolor: 'action.hover', border: 1, borderColor: 'divider', '& svg': { fontSize: 18 } }}>
                {meta.icon}
              </Box>
              {i < events.length - 1 && <Box sx={{ flex: 1, width: 2, bgcolor: 'divider', mt: 0.5 }} />}
            </Box>
            <Box sx={{ pt: 0.5, minWidth: 0 }}>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                <Typography fontWeight={650} variant="body2">{meta.label}</Typography>
                {e.countedAsViolation && <Chip size="small" color="warning" variant="outlined" label="counted" />}
                {e.durationMs ? <Chip size="small" variant="outlined" label={`away ${formatMs(e.durationMs)}`} /> : null}
              </Stack>
              <Typography variant="caption" color="text.secondary">
                {formatTime(e.clientTimestamp ?? e.serverTimestamp)}
                {e.details ? ` · ${e.details}` : ''}
              </Typography>
            </Box>
          </Stack>
        );
      })}
    </Stack>
  );
}
