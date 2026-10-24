import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { AdminDashboard } from './admin-dashboard';

describe('AdminDashboard', () => {
  let component: AdminDashboard;
  let fixture: ComponentFixture<AdminDashboard>;
  let httpMock: HttpTestingController;

  const usersFixture = [
    { id: 1, firstName: 'Salma', lastName: 'Ben Ali', email: 'salma@corp.io', role: 'ADMIN', active: true },
    { id: 2, firstName: 'Youssef', lastName: 'Trabelsi', email: 'youssef@corp.io', role: 'USER', active: true },
  ];

  const processesFixture = [
    {
      id: 1, name: 'clients-onboarding', description: '', inputFormat: 'JSON', outputFormat: 'JSON',
      createdAt: '2026-01-01T00:00:00', ruleCount: 2, lastRunStatus: 'SUCCESS',
      lastRunAt: '2026-01-01T00:00:00', lastRunExecutionId: 1, ownerId: 2,
    },
  ];

  const executionsFixture = [
    {
      id: 1, status: 'SUCCESS', errorMessage: null, startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01', processName: 'clients-onboarding', ownerId: 2,
    },
    {
      id: 2, status: 'FAILED', errorMessage: 'boom', startedAt: '2026-01-02T00:00:00',
      finishedAt: '2026-01-02T00:00:01', processName: 'clients-onboarding', ownerId: 2,
    },
  ];

  function flushAll(): void {
    httpMock.expectOne(`${environment.apiUrl}/admin/users`).flush(usersFixture);
    httpMock.expectOne(`${environment.apiUrl}/admin/processes`).flush(processesFixture);
    httpMock.expectOne(`${environment.apiUrl}/admin/executions`).flush(executionsFixture);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminDashboard);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    flushAll();
  });

  it('agrege les stats par utilisateur', () => {
    flushAll();
    const salma = component.usersWithStats().find((r) => r.user.id === 1)!;
    const youssef = component.usersWithStats().find((r) => r.user.id === 2)!;
    expect(salma.procCount).toBe(0);
    expect(youssef.procCount).toBe(1);
    expect(youssef.execCount).toBe(2);
    expect(youssef.successRate).toBe('50%');
  });

  it('filtre par utilisateur selectionne', () => {
    flushAll();
    component.onFilterChange('2');
    expect(component.isAllUsers()).toBe(false);
    expect(component.scopedProcesses().length).toBe(1);
    expect(component.scopedExecutions().length).toBe(2);
    expect(component.kpiActiveUserCount()).toBe('1');
  });

  it('affiche un message si l\'utilisateur n\'est pas administrateur (403)', () => {
    // forkJoin annule les deux autres requetes des que la premiere echoue ;
    // celles qui restent ouvertes doivent quand meme etre nettoyees pour httpMock.verify().
    httpMock.expectOne(`${environment.apiUrl}/admin/users`).flush('acces refuse', { status: 403, statusText: 'Forbidden' });
    httpMock.match(() => true).forEach((req) => {
      if (!req.cancelled) {
        req.flush(null, { status: 0, statusText: 'cancelled' });
      }
    });

    expect(component.errorMessage()).toBeTruthy();
  });
});
