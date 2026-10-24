import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { routes } from '../../app.routes';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';
import { Settings } from './settings';

describe('Settings', () => {
  let component: Settings;
  let fixture: ComponentFixture<Settings>;
  let httpMock: HttpTestingController;

  const currentUser = {
    id: 1, firstName: 'Salma', lastName: 'Ben Ali', email: 'salma.benali@corp.io', role: 'USER',
  };

  async function setUp(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        { provide: AuthService, useValue: { currentUser: () => currentUser, updateProfile: undefined, changePassword: undefined } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('pre-remplit le formulaire avec les informations de l\'utilisateur connecte', async () => {
    await setUp();
    expect(component.firstName).toBe('Salma');
    expect(component.lastName).toBe('Ben Ali');
    expect(component.email).toBe('salma.benali@corp.io');
  });

  it('choisit la nav USER pour un utilisateur non-admin', async () => {
    await setUp();
    expect(component.navItems.some((i) => i.key === 'processes')).toBe(true);
    expect(component.navItems.some((i) => i.key === 'users')).toBe(false);
  });

  it('signale que les mots de passe ne correspondent pas sans appeler le backend', async () => {
    await setUp();
    component.currentPassword = 'ancien123';
    component.newPassword = 'nouveau123';
    component.confirmPassword = 'different123';

    component.changePassword();

    expect(component.passwordError()).toContain('ne correspondent pas');
    httpMock.expectNone(`${environment.apiUrl}/auth/change-password`);
  });
});
