import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface PaletteCommand {
  label: string;
  hint: string;
  run: () => void;
}

@Component({
  selector: 'app-command-palette',
  imports: [FormsModule],
  templateUrl: './command-palette.html',
  styleUrl: './command-palette.css'
})
export class CommandPalette {

  @Input() open = false;
  @Input() query = '';
  @Input() commands: PaletteCommand[] = [];

  @Output() queryChange = new EventEmitter<string>();
  @Output() select = new EventEmitter<PaletteCommand>();
  @Output() close = new EventEmitter<void>();

  filteredCommands(): PaletteCommand[] {
    const term = this.query.toLowerCase().trim();
    const list = term ? this.commands.filter((c) => c.label.toLowerCase().includes(term)) : this.commands;
    return list.slice(0, 8);
  }

  onQueryChange(value: string): void {
    this.queryChange.emit(value);
  }

  onSelect(cmd: PaletteCommand): void {
    this.select.emit(cmd);
  }

  onOverlayClick(): void {
    this.close.emit();
  }

  stopProp(event: Event): void {
    event.stopPropagation();
  }
}
