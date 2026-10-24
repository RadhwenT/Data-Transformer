import { Routes } from '@angular/router';

import { Admin } from './features/admin/admin';
import { AdminDashboard } from './features/admin-dashboard/admin-dashboard';
import { Dashboard } from './features/dashboard/dashboard';
import { ExecutionDetail } from './features/execution-detail/execution-detail';
import { Executions } from './features/executions/executions';
import { Login } from './features/login/login';
import { Processes } from './features/processes/processes';
import { ProcessDetail } from './features/process-detail/process-detail';
import { Run } from './features/run/run';
import { Settings } from './features/settings/settings';
import { adminGuard, authGuard, userOnlyGuard } from './services/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'settings', component: Settings, canActivate: [authGuard] },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard, userOnlyGuard] },
  { path: 'processes', component: Processes, canActivate: [authGuard, userOnlyGuard] },
  { path: 'processes/new', component: ProcessDetail, canActivate: [authGuard, userOnlyGuard] },
  { path: 'processes/:id/run', component: Run, canActivate: [authGuard, userOnlyGuard] },
  { path: 'processes/:id', component: ProcessDetail, canActivate: [authGuard, userOnlyGuard] },
  { path: 'executions', component: Executions, canActivate: [authGuard, userOnlyGuard] },
  { path: 'executions/:id', component: ExecutionDetail, canActivate: [authGuard, userOnlyGuard] },
  { path: 'admin/dashboard', component: AdminDashboard, canActivate: [authGuard, adminGuard] },
  { path: 'admin', component: Admin, canActivate: [authGuard, adminGuard] },
  { path: '', redirectTo: '/login', pathMatch: 'full' }
];
