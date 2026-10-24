import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { Run } from './run';

describe('Run', () => {
  let component: Run;
  let fixture: ComponentFixture<Run>;
  let httpMock: HttpTestingController;
  let router: Router;

  function jsonFile(name: string, content = '{}'): File {
    return new File([content], name, { type: 'application/json' });
  }

  function selectFile(file: File): void {
    component.onFileInputChange({ target: { files: [file] } } as unknown as Event);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Run],
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

    fixture = TestBed.createComponent(Run);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();

    // GET /api/processes/{id} renvoie ProcessDetailResponse : rules[], pas
    // ruleCount (qui n'existe que sur la reponse de la liste).
    httpMock.expectOne(`${environment.apiUrl}/processes/1`).flush({
      id: 1,
      name: 'Nettoyage clients',
      description: '',
      inputFormat: 'JSON',
      outputFormat: 'JSON',
      rules: [
        { id: 1, executionOrder: 1, ruleName: 'Concatener deux valeurs', ruleType: 'CONCATENATE', sourceField: 'prenom', sourceField2: 'nom', targetField: 'nomComplet' },
        { id: 2, executionOrder: 2, ruleName: 'Mettre en majuscules', ruleType: 'UPPERCASE', sourceField: 'nomComplet', sourceField2: null, targetField: null },
      ],
    });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('affiche les regles du traitement en lecture seule', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('1. CONCATENATE');
    expect(text).toContain('prenom + nom → nomComplet');
    expect(text).toContain('2. UPPERCASE');
  });

  it('rejette un fichier .txt quand le traitement attend du JSON', () => {
    selectFile(new File(['x'], 'notes.txt'));
    expect(component.fileError()).toContain('.json');
    expect(component.selectedFile()).toBeNull();
  });

  it('desactive le bouton run tant qu\'aucun fichier n\'est charge, sans appel reseau', () => {
    expect(component.canRun()).toBe(false);
  });

  it('active le bouton run une fois un fichier valide charge, sans requete preview', () => {
    selectFile(jsonFile('clients.json'));
    expect(component.canRun()).toBe(true);
  });

  it('redirige vers /executions/:id apres un run reussi', () => {
    selectFile(jsonFile('clients.json'));

    const navigateSpy = vi.spyOn(router, 'navigate');
    component.run();

    httpMock.expectOne(`${environment.apiUrl}/executions`).flush({
      id: 42,
      status: 'SUCCESS',
      errorMessage: null,
      startedAt: '2026-01-01T00:00:00',
      finishedAt: '2026-01-01T00:00:01',
      processName: 'Nettoyage clients',
    });

    expect(navigateSpy).toHaveBeenCalledWith(['/executions', 42]);
  });
});
