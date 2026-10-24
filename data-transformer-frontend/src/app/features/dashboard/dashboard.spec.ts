import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;

  const processesFixture = [
    {
      id: 1, name: 'clients-onboarding', description: '', inputFormat: 'JSON', outputFormat: 'JSON',
      createdAt: '2026-01-01T00:00:00', ruleCount: 2, lastRunStatus: 'SUCCESS',
      lastRunAt: '2026-01-01T00:00:00', lastRunExecutionId: 1, ownerId: 1,
    },
  ];

  const now = new Date();
  const recent = new Date(now.getTime() - 60 * 60 * 1000).toISOString();
  const old = new Date(now.getTime() - 48 * 60 * 60 * 1000).toISOString();

  const executionsFixture = [
    {
      id: 2, status: 'SUCCESS', errorMessage: null, startedAt: recent,
      finishedAt: new Date(new Date(recent).getTime() + 1000).toISOString(), processName: 'clients-onboarding', ownerId: 1,
    },
    {
      id: 1, status: 'FAILED', errorMessage: 'boom', startedAt: old,
      finishedAt: new Date(new Date(old).getTime() + 2000).toISOString(), processName: 'clients-onboarding', ownerId: 1,
    },
  ];

  function flushAll(): void {
    httpMock.expectOne(`${environment.apiUrl}/processes`).flush(processesFixture);
    httpMock.expectOne(`${environment.apiUrl}/executions`).flush(executionsFixture);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
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

  it('calcule les KPIs a partir des processes et executions du user connecte', () => {
    flushAll();
    expect(component.activeProcessCount()).toBe(1);
    expect(component.executions24h()).toBe(1);
    expect(component.successRate()).toBe('50%');
  });

  it('ne garde que les executions les plus recentes dans le tableau', () => {
    flushAll();
    expect(component.recentExecutions()[0].id).toBe(2);
  });

  it('affiche un message d\'erreur si le chargement echoue', () => {
    // forkJoin annule l'autre requete des qu'une des deux echoue.
    httpMock.expectOne(`${environment.apiUrl}/processes`).flush('erreur serveur', { status: 500, statusText: 'Server Error' });
    httpMock.match(() => true).forEach((req) => {
      if (!req.cancelled) {
        req.flush(null, { status: 0, statusText: 'cancelled' });
      }
    });

    expect(component.errorMessage()).toBeTruthy();
  });
});
