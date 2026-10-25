import {
  AssignmentRounded, DashboardRounded, GroupRounded, HistoryRounded, InsightsRounded, LogoutRounded, MenuRounded,
  MonitorHeartRounded, PersonRounded, QuizRounded, SchoolRounded, SettingsRounded,
} from '@mui/icons-material';
import {
  AppBar, Avatar, Box, Chip, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, ListSubheader,
  Menu, MenuItem, Stack, Toolbar, Tooltip, Typography, useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState, type ReactNode } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { LiquidBackground } from '../../theme/LiquidBackground';
import { initials } from '../../utils/format';
import { ROLE_LABEL } from '../../utils/labels';
import { BrandLogo } from '../common/BrandLogo';
import { ErrorBoundary } from '../common/ErrorBoundary';
import { AppearancePicker, ThemeModeToggle } from './ThemeControls';

const DRAWER_WIDTH = 268;

interface NavItem {
  label: string;
  to: string;
  icon: ReactNode;
  show: boolean;
}

export function AppShell() {
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenu, setUserMenu] = useState<HTMLElement | null>(null);
  const { user, isStaff, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const sections: { title: string; items: NavItem[] }[] = [
    {
      title: 'Workspace',
      items: [
        { label: 'Dashboard', to: '/dashboard', icon: <DashboardRounded />, show: isStaff },
        { label: 'Assessments', to: '/assignments', icon: <AssignmentRounded />, show: isStaff },
        { label: 'Question bank', to: '/questions', icon: <QuizRounded />, show: isStaff },
        { label: 'Live monitoring', to: '/monitoring', icon: <MonitorHeartRounded />, show: isStaff },
        { label: 'Results & reports', to: '/results', icon: <InsightsRounded />, show: isStaff },
      ],
    },
    {
      title: 'Administration',
      items: [
        { label: 'Users', to: '/users', icon: <GroupRounded />, show: isAdmin },
        { label: 'Audit logs', to: '/audit-logs', icon: <HistoryRounded />, show: isAdmin },
      ],
    },
    {
      title: 'Me',
      items: [
        { label: 'My assessments', to: '/candidate', icon: <SchoolRounded />, show: true },
        { label: 'Settings', to: '/settings', icon: <SettingsRounded />, show: true },
        { label: 'Profile', to: '/profile', icon: <PersonRounded />, show: true },
      ],
    },
  ];

  const isActive = (to: string) => location.pathname === to || location.pathname.startsWith(`${to}/`);

  const drawer = (
    <Stack sx={{ height: '100%' }}>
      <Box sx={{ px: 2.5, py: 2.5 }}>
        <BrandLogo size={38} />
      </Box>
      <Box sx={{ flex: 1, overflowY: 'auto', px: 1.5 }}>
        {sections.map((section) => {
          const visible = section.items.filter((i) => i.show);
          if (!visible.length) return null;
          return (
            <List
              key={section.title}
              dense
              subheader={<ListSubheader disableSticky sx={{ bgcolor: 'transparent', lineHeight: '32px', fontWeight: 700, letterSpacing: '.08em', fontSize: 11, textTransform: 'uppercase' }}>{section.title}</ListSubheader>}
            >
              {visible.map((item) => (
                <ListItemButton
                  key={item.to}
                  selected={isActive(item.to)}
                  onClick={() => {
                    navigate(item.to);
                    setMobileOpen(false);
                  }}
                  sx={{ mb: 0.5, py: 1 }}
                >
                  <ListItemIcon sx={{ minWidth: 38, color: isActive(item.to) ? 'primary.light' : 'text.secondary' }}>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.label} primaryTypographyProps={{ fontWeight: isActive(item.to) ? 700 : 550, fontSize: 14.5 }} />
                </ListItemButton>
              ))}
            </List>
          );
        })}
      </Box>
      <Divider />
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ p: 2 }}>
        <Avatar sx={{ width: 36, height: 36, background: theme.brand.gradient, fontSize: 14, fontWeight: 700 }}>{initials(user?.fullName)}</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography variant="body2" fontWeight={700} noWrap>{user?.fullName}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap component="div">
            {user?.roles.map((r) => ROLE_LABEL[r]).join(', ')}
          </Typography>
        </Box>
      </Stack>
    </Stack>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <LiquidBackground />
      <a href="#main" className="skip-link" style={{ position: 'absolute', left: -9999 }}>Skip to content</a>
      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        {desktop ? (
          <Drawer variant="permanent" open sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}>
            {drawer}
          </Drawer>
        ) : (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={() => setMobileOpen(false)}
            ModalProps={{ keepMounted: true }}
            sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
          >
            {drawer}
          </Drawer>
        )}
      </Box>

      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <AppBar position="sticky">
          <Toolbar sx={{ gap: 1, minHeight: { xs: 60, md: 68 } }}>
            {!desktop && (
              <IconButton edge="start" onClick={() => setMobileOpen(true)} aria-label="Open navigation">
                <MenuRounded />
              </IconButton>
            )}
            {!desktop && <BrandLogo size={26} product={false} />}
            <Box sx={{ flex: 1 }} />
            {user?.roles.includes('SUPER_ADMIN') && desktop && <Chip size="small" label="Super Admin" color="primary" variant="outlined" />}
            <AppearancePicker />
            <ThemeModeToggle />
            <Tooltip title="Account">
              <IconButton onClick={(e) => setUserMenu(e.currentTarget)} aria-label="Account menu">
                <Avatar sx={{ width: 32, height: 32, background: theme.brand.gradient, fontSize: 13, fontWeight: 700 }}>{initials(user?.fullName)}</Avatar>
              </IconButton>
            </Tooltip>
            <Menu anchorEl={userMenu} open={Boolean(userMenu)} onClose={() => setUserMenu(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
              <Box sx={{ px: 2, py: 1 }}>
                <Typography fontWeight={700}>{user?.fullName}</Typography>
                <Typography variant="body2" color="text.secondary">{user?.email}</Typography>
              </Box>
              <Divider sx={{ my: 1 }} />
              <MenuItem onClick={() => { setUserMenu(null); navigate('/profile'); }}>
                <ListItemIcon><PersonRounded fontSize="small" /></ListItemIcon>Profile & password
              </MenuItem>
              <MenuItem onClick={async () => { setUserMenu(null); await logout(); navigate('/login'); }}>
                <ListItemIcon><LogoutRounded fontSize="small" /></ListItemIcon>Sign out
              </MenuItem>
            </Menu>
          </Toolbar>
        </AppBar>
        <Box component="main" id="main" sx={{ flex: 1, p: { xs: 2, sm: 3, lg: 4 }, maxWidth: 1480, width: '100%', mx: 'auto' }}>
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </Box>
      </Box>
    </Box>
  );
}
