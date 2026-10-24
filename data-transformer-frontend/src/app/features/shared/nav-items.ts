import { SidebarNavItem } from './sidebar/sidebar';

export const USER_NAV_ITEMS: SidebarNavItem[] = [
  { key: 'dashboard', label: '[01] dashboard', path: '/dashboard', paletteLabel: 'aller au dashboard' },
  { key: 'processes', label: '[02] processes', path: '/processes', paletteLabel: 'aller aux processes' },
  { key: 'executions', label: '[03] executions', path: '/executions', paletteLabel: 'aller aux executions' },
];

export const ADMIN_NAV_ITEMS: SidebarNavItem[] = [
  { key: 'dashboard', label: '[01] dashboard', path: '/admin/dashboard', paletteLabel: 'aller au dashboard' },
  { key: 'users', label: '[02] users', path: '/admin', paletteLabel: 'aller aux users' },
];
