import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { ExecutionDetail } from './execution-detail';

describe('ExecutionDetail', () => {
  let component: ExecutionDetail;
  let fixture: ComponentFixture<ExecutionDetail>;
  let httpMock: HttpTestingController;

  async function setUp(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ExecutionDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ExecutionDetail);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('affiche input, output et les regles pour une execution reussie', async () => {
    await setUp();

    httpMock.expectOne(`${environment.apiUrl}/executions/1`).flush({
      id: 1,
      status: 'SUCCESS',
      errorMessage: null,
      startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01',
      processName: 'Nettoyage clients',
      processId: 1,
      inputFormat: 'JSON',
      outputFormat: 'JSON',
      durationMs: 1000,
      inputFileName: 'clients.json',
      hasOutput: true,
      rules: [
        { id: 1, executionOrder: 1, ruleName: 'Concatener deux valeurs', ruleType: 'CONCATENATE', sourceField: 'prenom', sourceField2: 'nom', targetField: 'nomComplet' },
      ],
    });
    httpMock.expectOne(`${environment.apiUrl}/executions/1/content`).flush({
      input: '{"prenom":"sarah","nom":"martin"}',
      output: '{"nomComplet":"SARAH MARTIN"}',
      inputTruncated: false,
      outputTruncated: false,
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('SARAH MARTIN');
    expect(text).toContain('Concatener deux valeurs — prenom + nom → nomComplet');
    expect(fixture.nativeElement.querySelector('button.primary')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('button.secondary')).toBeTruthy();
  });

  it('affiche le message d\'erreur et masque le bouton download pour une execution echouee', async () => {
    await setUp();

    httpMock.expectOne(`${environment.apiUrl}/executions/1`).flush({
      id: 1,
      status: 'FAILED',
      errorMessage: 'Champ absent dans le fichier : nom',
      startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01',
      processName: 'Nettoyage clients',
      processId: 1,
      inputFormat: 'JSON',
      outputFormat: 'JSON',
      durationMs: 1000,
      inputFileName: 'clients.json',
      hasOutput: false,
      rules: [
        { id: 1, executionOrder: 1, ruleName: 'Concatener deux valeurs', ruleType: 'CONCATENATE', sourceField: 'prenom', sourceField2: 'nom', targetField: 'nomComplet' },
      ],
    });
    httpMock.expectOne(`${environment.apiUrl}/executions/1/content`).flush({
      input: '{"prenom":"sarah"}',
      output: null,
      inputTruncated: false,
      outputTruncated: false,
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Champ absent dans le fichier : nom');
    expect(fixture.nativeElement.querySelector('button.primary')).toBeFalsy();
    // le fichier d'entree reste telechargeable meme si l'execution a echoue.
    expect(fixture.nativeElement.querySelector('button.secondary')).toBeTruthy();

    const failedRuleLine = fixture.nativeElement.querySelector('.rules-list li.failed');
    expect(failedRuleLine?.textContent).toContain('nomComplet');
  });
});
