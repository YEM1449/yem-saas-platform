import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { VenteStatut } from './vente.service';

export interface PipelineStep {
  statut: VenteStatut;
  label: string;
}

const VENTE_STEPS: PipelineStep[] = [
  { statut: 'COMPROMIS',    label: 'Compromis'    },
  { statut: 'FINANCEMENT',  label: 'Financement'  },
  { statut: 'ACTE_NOTARIE', label: 'Acte notarié' },
  { statut: 'LIVRE',        label: 'Livré'        },
];

@Component({
  selector: 'app-pipeline-stepper',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (statut === 'ANNULE') {
      <div class="stepper-cancelled">
        <span class="stepper-cancelled-icon">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.5"/>
            <path d="M4.5 4.5l5 5M9.5 4.5l-5 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </span>
        Vente annulée
      </div>
    } @else {
      <div class="stepper" [class.stepper-vertical]="vertical">
        @for (step of steps; track step.statut; let i = $index) {
          <div class="stepper-step"
               [class.step-done]="isCompleted(step.statut)"
               [class.step-active]="statut === step.statut"
               [class.step-pending]="isPending(step.statut)">
            <div class="step-circle">
              @if (isCompleted(step.statut)) {
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                  <path d="M2 6l3 3 5-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              } @else {
                <span class="step-num">{{ i + 1 }}</span>
              }
            </div>
            <span class="step-label">{{ step.label }}</span>
            @if (i < steps.length - 1) {
              <div class="step-connector" [class.connector-done]="isCompleted(step.statut)"></div>
            }
          </div>
        }
      </div>
    }
  `,
  styleUrl: './pipeline-stepper.component.css',
})
export class PipelineStepperComponent {
  @Input() statut: VenteStatut = 'COMPROMIS';
  @Input() vertical = false;

  readonly steps = VENTE_STEPS;

  private readonly ORDER: Record<VenteStatut, number> = {
    COMPROMIS:    0,
    FINANCEMENT:  1,
    ACTE_NOTARIE: 2,
    LIVRE:        3,
    ANNULE:      -1,
  };

  isCompleted(step: VenteStatut): boolean {
    return this.ORDER[this.statut] > this.ORDER[step];
  }

  isPending(step: VenteStatut): boolean {
    return this.statut !== step && this.ORDER[this.statut] < this.ORDER[step];
  }
}
