import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { Processes } from './processes';

describe('Processes', () => {
  let component: Processes;
  let fixture: ComponentFixture<Processes>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Processes],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();

    fixture = TestBed.createComponent(Processes);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    httpMock.expectOne(`${environment.apiUrl}/processes`).flush([]);
  });

  it('affiche les traitements renvoyes par l\'API', () => {
    httpMock.expectOne(`${environment.apiUrl}/processes`).flush([
      {
        id: 1,
        name: 'Nettoyage clients',
        description: '',
        inputFormat: 'JSON',
        outputFormat: 'JSON',
        createdAt: '2026-01-01T00:00:00',
        ruleCount: 3,
        lastRunStatus: 'SUCCESS',
        lastRunAt: '2026-01-02T00:00:00',
      },
    ]);

    expect(component.processes().length).toBe(1);
    expect(component.loading()).toBe(false);
  });

  it('affiche une erreur si le chargement echoue (hors 401)', () => {
    httpMock.expectOne(`${environment.apiUrl}/processes`).flush('erreur', { status: 500, statusText: 'Server Error' });

    expect(component.errorMessage()).toBeTruthy();
  });
});
