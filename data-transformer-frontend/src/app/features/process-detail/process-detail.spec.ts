import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { ProcessDetail } from './process-detail';

describe('ProcessDetail', () => {
  let component: ProcessDetail;
  let fixture: ComponentFixture<ProcessDetail>;
  let httpMock: HttpTestingController;

  const rulesCatalogFixture = [
    { id: 1, name: 'Concatener deux valeurs', type: 'CONCATENATE', description: 'Joint deux champs' },
    { id: 2, name: 'Mettre en majuscules', type: 'UPPERCASE', description: 'Majuscules' },
  ];

  const processFixture = {
    id: 1,
    name: 'Nettoyage clients',
    description: 'Scenario nominal',
    inputFormat: 'JSON',
    outputFormat: 'JSON',
    rules: [
      {
        id: 10,
        executionOrder: 1,
        ruleName: 'Concatener deux valeurs',
        ruleType: 'CONCATENATE',
        sourceField: 'prenom',
        sourceField2: 'nom',
        targetField: 'nomComplet',
      },
      {
        id: 11,
        executionOrder: 2,
        ruleName: 'Mettre en majuscules',
        ruleType: 'UPPERCASE',
        sourceField: 'nomComplet',
        sourceField2: null,
        targetField: null,
      },
    ],
  };

  async function setUp(idParam: string | null) {
    await TestBed.configureTestingModule({
      imports: [ProcessDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(idParam ? { id: idParam } : {}) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProcessDetail);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    if (idParam === null) {
      httpMock.expectOne(`${environment.apiUrl}/rules`).flush(rulesCatalogFixture);
    }
  }

  /** Flush du chargement initial (catalogue + traitement + executions) pour un traitement existant. */
  function flushInitialLoad(): void {
    httpMock.expectOne(`${environment.apiUrl}/rules`).flush(rulesCatalogFixture);
    httpMock.expectOne(`${environment.apiUrl}/processes/1`).flush(processFixture);
    httpMock.expectOne(`${environment.apiUrl}/executions?processId=1`).flush([]);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('cree un formulaire vide en mode "nouveau traitement"', async () => {
    await setUp(null);

    expect(component.isNew()).toBe(true);
    expect(component.loading()).toBe(false);
  });

  it('charge le traitement existant et initialise le brouillon de regles', async () => {
    await setUp('1');
    flushInitialLoad();

    expect(component.isNew()).toBe(false);
    expect(component.name).toBe('Nettoyage clients');
    expect(component.savedRules().length).toBe(2);
    expect(component.draftRules().length).toBe(2);
    expect(component.draftRules()[0].ruleType).toBe('CONCATENATE');
  });

  it('affiche les executions recentes du traitement', async () => {
    await setUp('1');

    httpMock.expectOne(`${environment.apiUrl}/rules`).flush(rulesCatalogFixture);
    httpMock.expectOne(`${environment.apiUrl}/processes/1`).flush({ ...processFixture, rules: [] });
    httpMock.expectOne(`${environment.apiUrl}/executions?processId=1`).flush([
      {
        id: 7,
        status: 'SUCCESS',
        errorMessage: null,
        startedAt: '2026-01-01T00:00:00',
        finishedAt: '2026-01-01T00:00:01',
        processName: 'Nettoyage clients',
      },
    ]);

    expect(component.recentExecutions().length).toBe(1);
    expect(component.recentExecutions()[0].id).toBe(7);
  });

  it('reordonne le brouillon avec les fleches haut/bas, sans appel reseau', async () => {
    await setUp('1');
    flushInitialLoad();

    component.moveRuleDown(0);

    expect(component.draftRules()[0].ruleType).toBe('UPPERCASE');
    expect(component.draftRules()[1].ruleType).toBe('CONCATENATE');
  });

  it('ajoute une regle au brouillon sans appel reseau immediat', async () => {
    await setUp('1');
    flushInitialLoad();

    component.selectRuleType(2); // UPPERCASE : pas besoin de source2/target
    component.ruleSourceField = 'email';
    component.submitRuleForm();

    expect(component.draftRules().length).toBe(3);
    expect(component.draftRules()[2].ruleType).toBe('UPPERCASE');
    expect(component.draftRules()[2].id).toBeNull();
  });

  it('annuler restaure le brouillon et les champs du traitement depuis le dernier etat sauvegarde', async () => {
    await setUp('1');
    flushInitialLoad();

    component.name = 'nom modifie';
    component.removeDraftRule(component.draftRules()[0]);
    expect(component.draftRules().length).toBe(1);

    component.cancelAll();

    expect(component.name).toBe('Nettoyage clients');
    expect(component.draftRules().length).toBe(2);
  });

  it('save changes met a jour le traitement puis reconcilie les regles (suppression + reordonnancement + creation)', async () => {
    await setUp('1');
    flushInitialLoad();

    // supprime la 1ere regle, reordonne, ajoute une nouvelle regle
    component.removeDraftRule(component.draftRules()[0]);
    component.selectRuleType(2);
    component.ruleSourceField = 'email';
    component.submitRuleForm();

    component.saveAll();

    httpMock.expectOne(`${environment.apiUrl}/processes/1`).flush(processFixture);
    httpMock.expectOne(`${environment.apiUrl}/processes/1/rules/10`).flush({}); // suppression de la regle retiree

    const bumpReq = httpMock.expectOne(`${environment.apiUrl}/processes/1/rules/11`);
    expect(bumpReq.request.body.executionOrder).toBeGreaterThanOrEqual(10000);
    bumpReq.flush({});

    const finalizeReq = httpMock.expectOne(`${environment.apiUrl}/processes/1/rules/11`);
    expect(finalizeReq.request.body.executionOrder).toBe(1);
    finalizeReq.flush({});

    const createReq = httpMock.expectOne(`${environment.apiUrl}/processes/1/rules`);
    expect(createReq.request.body.executionOrder).toBe(2);
    createReq.flush({});

    flushInitialLoad(); // rechargement final (loadAll)

    expect(component.savingAll()).toBe(false);
  });
});
