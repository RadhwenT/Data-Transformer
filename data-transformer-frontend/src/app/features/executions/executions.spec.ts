import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { Executions } from './executions';

describe('Executions', () => {
  let component: Executions;
  let fixture: ComponentFixture<Executions>;
  let httpMock: HttpTestingController;

  const executionsFixture = [
    {
      id: 1,
      status: 'SUCCESS',
      errorMessage: null,
      startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01',
      processName: 'Nettoyage clients',
    },
    {
      id: 2,
      status: 'FAILED',
      errorMessage: 'Champ absent dans le fichier : nom',
      startedAt: '2026-01-02T00:00:00',
      finishedAt: '2026-01-02T00:00:00',
      processName: 'Autre traitement',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Executions],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();

    fixture = TestBed.createComponent(Executions);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiUrl}/processes`).flush([]);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('affiche les executions renvoyees par l\'API', () => {
    httpMock.expectOne(`${environment.apiUrl}/executions`).flush(executionsFixture);

    expect(component.executions().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('filtre par traitement', () => {
    httpMock.expectOne(`${environment.apiUrl}/executions`).flush(executionsFixture);

    component.processFilter = 'Nettoyage clients';
    const filtered = component.filteredExecutions();

    expect(filtered.length).toBe(1);
    expect(filtered[0].processName).toBe('Nettoyage clients');
  });

  it('n\'affiche que le bouton view sur chaque ligne (le download reste sur l\'ecran de detail)', () => {
    httpMock.expectOne(`${environment.apiUrl}/executions`).flush(executionsFixture);
    fixture.detectChanges();

    const rows: NodeListOf<HTMLTableRowElement> = fixture.nativeElement.querySelectorAll('tbody tr');
    rows.forEach((row) => {
      const labels = Array.from(row.querySelectorAll('button')).map((b) => b.textContent?.trim());
      expect(labels).toEqual(['view']);
    });
  });

  it('expose des commandes de filtre par statut pour la palette', () => {
    httpMock.expectOne(`${environment.apiUrl}/executions`).flush(executionsFixture);

    const labels = component.filterCommands().map((c) => c.label);
    expect(labels).toContain('statut: failed');
    expect(labels).toContain('tous les statuts');
  });
});
