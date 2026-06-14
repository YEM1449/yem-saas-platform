import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-shortcut-grid',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shortcut-grid.component.html',
  styleUrl: './shortcut-grid.component.css',
})
export class ShortcutGridComponent {
  @Input() isAdminOrManager = false;
}
