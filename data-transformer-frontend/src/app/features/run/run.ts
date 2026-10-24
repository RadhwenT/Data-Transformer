import { Component, ElementRef, OnInit, ViewChild, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ProcessDetail, ProcessRule } from '../../models/api.models';
import { ExecutionService } from '../../services/execution.service';
import { ProcessService } from '../../services/process.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { USER_NAV_ITEMS } from '../shared/nav-items';

const MAX_FILE_SIZE = 1024 * 1024;

@Component({
  selector: 'app-run',
  imports: [RouterLink, AppShell],
  templateUrl: './run.html',
  styleUrl: './run.css',
})
export class Run implements OnInit {

  readonly navItems = USER_NAV_ITEMS;

  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly process = signal<ProcessDetail | null>(null);

  readonly selectedFile = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly dragging = signal(false);

  readonly running = signal(false);
  readonly runError = signal<string | null>(null);

  private processId!: number;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private processService: ProcessService,
    private executionService: ExecutionService
  ) {}

  ngOnInit(): void {
    this.processId = Number(this.route.snapshot.paramMap.get('id'));
    this.processService.getById(this.processId).subscribe({
      next: (process) => {
        this.process.set(process);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else {
          this.errorMessage.set('traitement introuvable');
        }
      },
    });
  }

  acceptExtension(): string {
    return this.process()?.inputFormat === 'XML' ? '.xml' : '.json';
  }

  fieldsLabel(rule: ProcessRule): string {
    return rule.sourceField
      + (rule.sourceField2 ? ' + ' + rule.sourceField2 : '')
      + (rule.targetField ? ' → ' + rule.targetField : '');
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.validateAndSetFile(file);
    }
  }

  openFileDialog(): void {
    this.fileInputRef.nativeElement.click();
  }

  onFileInputChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.validateAndSetFile(file);
    }
  }

  private validateAndSetFile(file: File): void {
    const expectedExtension = this.acceptExtension();

    if (!file.name.toLowerCase().endsWith(expectedExtension)) {
      this.fileError.set(`extension invalide : un fichier ${expectedExtension} est attendu`);
      return;
    }
    if (file.size === 0) {
      this.fileError.set('le fichier est vide');
      return;
    }
    if (file.size > MAX_FILE_SIZE) {
      this.fileError.set('le fichier depasse 1 Mo');
      return;
    }

    this.fileError.set(null);
    this.selectedFile.set(file);
    this.runError.set(null);
  }

  removeFile(): void {
    this.selectedFile.set(null);
    this.fileError.set(null);
    if (this.fileInputRef) {
      this.fileInputRef.nativeElement.value = '';
    }
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    return `${(bytes / 1024).toFixed(1)} KB`;
  }

  canRun(): boolean {
    return !this.running() && this.selectedFile() !== null && (this.process()?.rules.length ?? 0) > 0;
  }

  run(): void {
    const file = this.selectedFile();
    if (!file || this.running()) {
      return;
    }

    this.running.set(true);
    this.runError.set(null);

    this.executionService.run(this.processId, file).subscribe({
      next: (execution) => {
        this.router.navigate(['/executions', execution.id]);
      },
      error: () => {
        this.running.set(false);
        this.runError.set("l'execution a echoue");
      },
    });
  }
}
