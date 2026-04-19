import {
  AfterViewInit,
  Component,
  ElementRef,
  inject,
  OnInit,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TemplateService } from './template.service';
import { TemplateType } from './template.model';

const TYPE_LABELS: Record<string, string> = {
  CONTRACT: 'Contrat de vente',
  RESERVATION: 'Bon de réservation',
  CALL_FOR_FUNDS: 'Appel de fonds',
};

interface TemplateVar { var: string; desc: string; }
interface VarGroup    { id: string; label: string; icon: string; vars: TemplateVar[]; }

@Component({
  selector: 'app-template-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <!-- ── Top bar ─────────────────────────────────────────── -->
    <div class="ed-topbar">
      <div class="ed-topbar-left">
        <a routerLink="/app/templates" class="back-link">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9 2L5 7l4 5" stroke="currentColor" stroke-width="1.5"
                  stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          Retour aux modèles
        </a>
        <div class="ed-title-row">
          <h1 class="ed-title">{{ typeLabel }}</h1>
          <span class="badge-custom" *ngIf="isCustom">Personnalisé</span>
          <span class="badge-default" *ngIf="!isCustom">Modèle intégré</span>
        </div>
      </div>
      <div class="ed-actions">
        <button class="btn btn-ghost" (click)="toggleRaw()" [title]="rawMode ? 'Revenir à l\'éditeur visuel' : 'Voir le HTML source'">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round">
            <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
          </svg>
          {{ rawMode ? 'Mode visuel' : 'HTML source' }}
        </button>
        <a [href]="previewHref" target="_blank" rel="noopener noreferrer" class="btn btn-outline">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M1.5 6.5C1.5 6.5 3.5 2.5 6.5 2.5S11.5 6.5 11.5 6.5 9.5 10.5 6.5 10.5 1.5 6.5 1.5 6.5z"
                  stroke="currentColor" stroke-width="1.3"/>
            <circle cx="6.5" cy="6.5" r="1.5" stroke="currentColor" stroke-width="1.3"/>
          </svg>
          Aperçu PDF
        </a>
        <button *ngIf="isCustom" (click)="revert()" class="btn btn-danger-soft">Réinitialiser</button>
        <button (click)="save()" [disabled]="saving" class="btn btn-primary">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M2 7l3 3 6-6" stroke="currentColor" stroke-width="1.6"
                  stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ saving ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
        <span *ngIf="saveError"   class="msg-error">{{ saveError }}</span>
        <span *ngIf="saveSuccess" class="msg-success">✓ Enregistré</span>
      </div>
    </div>

    <!-- ── Formatting toolbar (WYSIWYG only) ─────────────────── -->
    <div class="ed-fmt-bar" *ngIf="!rawMode">
      <!-- Block format -->
      <select class="fmt-select" (mousedown)="$event.preventDefault()"
              (change)="execFormatBlock($any($event.target).value); $any($event.target).value = ''">
        <option value="" disabled selected>Paragraphe</option>
        <option value="p">Paragraphe</option>
        <option value="h1">Titre 1</option>
        <option value="h2">Titre 2</option>
        <option value="h3">Titre 3</option>
        <option value="h4">Titre 4</option>
        <option value="blockquote">Citation</option>
      </select>

      <div class="fmt-sep"></div>

      <!-- Text style -->
      <button class="fmt-btn" title="Gras (Ctrl+B)"
              (mousedown)="$event.preventDefault()" (click)="exec('bold')">
        <strong>G</strong>
      </button>
      <button class="fmt-btn" title="Italique (Ctrl+I)"
              (mousedown)="$event.preventDefault()" (click)="exec('italic')">
        <em>I</em>
      </button>
      <button class="fmt-btn" title="Souligné (Ctrl+U)"
              (mousedown)="$event.preventDefault()" (click)="exec('underline')">
        <u>S</u>
      </button>

      <div class="fmt-sep"></div>

      <!-- Alignment -->
      <button class="fmt-btn" title="Aligner à gauche"
              (mousedown)="$event.preventDefault()" (click)="exec('justifyLeft')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <line x1="1" y1="3" x2="13" y2="3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="1" y1="7" x2="9"  y2="7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="1" y1="11" x2="11" y2="11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
      <button class="fmt-btn" title="Centrer"
              (mousedown)="$event.preventDefault()" (click)="exec('justifyCenter')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <line x1="1" y1="3"  x2="13" y2="3"  stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="3" y1="7"  x2="11" y2="7"  stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="2" y1="11" x2="12" y2="11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
      <button class="fmt-btn" title="Aligner à droite"
              (mousedown)="$event.preventDefault()" (click)="exec('justifyRight')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <line x1="1"  y1="3"  x2="13" y2="3"  stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="5"  y1="7"  x2="13" y2="7"  stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="3"  y1="11" x2="13" y2="11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>

      <div class="fmt-sep"></div>

      <!-- Lists -->
      <button class="fmt-btn" title="Liste à puces"
              (mousedown)="$event.preventDefault()" (click)="exec('insertUnorderedList')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <circle cx="2" cy="4"  r="1.2" fill="currentColor"/>
          <circle cx="2" cy="10" r="1.2" fill="currentColor"/>
          <line x1="5" y1="4"  x2="13" y2="4"  stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="5" y1="10" x2="13" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
      <button class="fmt-btn" title="Liste numérotée"
              (mousedown)="$event.preventDefault()" (click)="exec('insertOrderedList')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <text x="1" y="5.5" font-size="5" fill="currentColor" font-family="sans-serif">1.</text>
          <text x="1" y="11" font-size="5" fill="currentColor" font-family="sans-serif">2.</text>
          <line x1="6" y1="4"  x2="13" y2="4"  stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="6" y1="10" x2="13" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>

      <div class="fmt-sep"></div>

      <!-- Misc -->
      <button class="fmt-btn" title="Ligne de séparation"
              (mousedown)="$event.preventDefault()" (click)="exec('insertHorizontalRule')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <line x1="1" y1="7" x2="13" y2="7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>

      <div class="fmt-sep"></div>

      <!-- Undo / Redo -->
      <button class="fmt-btn" title="Annuler (Ctrl+Z)"
              (mousedown)="$event.preventDefault()" (click)="exec('undo')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M2 5H8a4 4 0 0 1 0 8H5" stroke="currentColor" stroke-width="1.5"
                stroke-linecap="round" stroke-linejoin="round"/>
          <polyline points="2,2 2,5 5,5" stroke="currentColor" stroke-width="1.5"
                    stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <button class="fmt-btn" title="Rétablir (Ctrl+Y)"
              (mousedown)="$event.preventDefault()" (click)="exec('redo')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M12 5H6a4 4 0 0 0 0 8h3" stroke="currentColor" stroke-width="1.5"
                stroke-linecap="round" stroke-linejoin="round"/>
          <polyline points="12,2 12,5 9,5" stroke="currentColor" stroke-width="1.5"
                    stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>

      <div class="fmt-sep"></div>

      <!-- Insert image -->
      <button class="fmt-btn fmt-btn-image" title="Insérer une image (logo, tampon…)"
              (mousedown)="$event.preventDefault()" (click)="triggerImagePicker()"
              [disabled]="imageUploading">
        <svg *ngIf="!imageUploading" width="14" height="14" viewBox="0 0 14 14" fill="none">
          <rect x="1" y="2" width="12" height="10" rx="1.5" stroke="currentColor" stroke-width="1.3"/>
          <circle cx="4.5" cy="5.5" r="1.2" stroke="currentColor" stroke-width="1.2"/>
          <path d="M1 10l3-3 2.5 2.5L9 7l4 4" stroke="currentColor" stroke-width="1.3"
                stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <svg *ngIf="imageUploading" class="spin" width="14" height="14" viewBox="0 0 14 14" fill="none">
          <circle cx="7" cy="7" r="5.5" stroke="#cbd5e1" stroke-width="2"/>
          <path d="M7 1.5A5.5 5.5 0 0 1 12.5 7" stroke="#2563eb" stroke-width="2" stroke-linecap="round"/>
        </svg>
        Image
      </button>
      <input #imageInput type="file" accept="image/jpeg,image/png,image/gif,image/webp"
             style="display:none" (change)="onImageFileSelected($event)"/>

      <div class="fmt-sep"></div>

      <!-- Variable count badge -->
      <span class="var-count-badge" *ngIf="insertedCount > 0">
        {{ insertedCount }} variable{{ insertedCount > 1 ? 's' : '' }}
      </span>
    </div>

    <!-- ── Two-pane layout ─────────────────────────────────── -->
    <div class="ed-layout">

      <!-- LEFT — editor surface -->
      <div class="ed-pane-editor">

        <!-- WYSIWYG surface -->
        <div *ngIf="!rawMode"
             #editor
             class="wysiwyg-editor"
             [class.drag-active]="isDraggingOver"
             contenteditable="true"
             (blur)="saveSelection()"
             (dragover)="onDragOver($event)"
             (dragleave)="onDragLeave($event)"
             (drop)="onDrop($event)"
             (input)="recountInserted()"
             data-placeholder="Commencez à écrire votre contrat ou glissez des variables depuis le panneau de droite…">
        </div>

        <!-- Raw HTML surface -->
        <div *ngIf="rawMode" class="raw-mode-wrapper">
          <div class="raw-mode-banner">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            Mode HTML avancé — les modifications ici écrasent l'éditeur visuel.
            Les tokens <code>\${model.varName}</code> sont les variables.
          </div>
          <textarea
            #rawEditor
            class="html-editor"
            [(ngModel)]="rawHtml"
            spellcheck="false">
          </textarea>
        </div>
      </div>

      <!-- RIGHT — variable palette -->
      <div class="ed-pane-ref">
        <div class="ref-palette-title">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <circle cx="6" cy="6" r="5" stroke="currentColor" stroke-width="1.3"/>
            <path d="M6 5.5v3M6 3.5v.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          Variables disponibles
        </div>

        <input type="text" class="ref-search"
               placeholder="Filtrer (ex. prix, acheteur)…"
               [(ngModel)]="searchTerm"
               (input)="filterVars()"/>

        <div class="ref-hint" *ngIf="!rawMode">
          Cliquez ou glissez une variable pour l'insérer dans le document.
        </div>
        <div class="ref-hint" *ngIf="rawMode">
          En mode HTML : cliquez pour copier le token <code>\${model.*}</code>.
        </div>

        @for (g of filteredGroups; track g.id) {
          <div class="ref-group">
            <div class="ref-group-head">
              <span class="ref-group-icon">{{ g.icon }}</span>
              <span class="ref-group-label">{{ g.label }}</span>
              <span class="ref-group-count">{{ g.vars.length }}</span>
            </div>
            <div class="ref-group-body">
              @for (v of g.vars; track v.var) {
                <div class="var-chip"
                     draggable="true"
                     (dragstart)="onDragStart($event, v.var)"
                     (dragend)="onDragEnd()"
                     (click)="onVarClick(v.var)"
                     [title]="v.desc">
                  <span class="var-chip-label">{{ v.desc }}</span>
                  <code class="var-chip-token">\${{ '{' }}model.{{ v.var }}{{ '}' }}</code>
                </div>
              }
            </div>
          </div>
        }

        @if (filteredGroups.length === 0) {
          <div class="ref-empty">Aucune variable ne correspond à «&#160;{{ searchTerm }}&#160;»</div>
        }

        <div class="toast" *ngIf="toast">{{ toast }}</div>
      </div>
    </div>
  `,
  styles: [`
    /* ── Top bar ───────────────────────────────────────────── */
    .ed-topbar {
      display: flex; align-items: center; justify-content: space-between;
      gap: 16px; margin-bottom: 10px; flex-wrap: wrap;
    }
    .ed-topbar-left { display: flex; flex-direction: column; gap: 4px; }
    .back-link {
      display: inline-flex; align-items: center; gap: 5px;
      color: #2563eb; font-size: 12px; text-decoration: none;
    }
    .back-link:hover { text-decoration: underline; }
    .ed-title-row { display: flex; align-items: center; gap: 10px; }
    .ed-title { font-size: 18px; font-weight: 700; color: #1e293b; margin: 0; }
    .badge-custom  { background: #dbeafe; color: #1d4ed8; font-size: 11px; padding: 2px 8px; border-radius: 99px; font-weight: 600; }
    .badge-default { background: #f1f5f9; color: #64748b; font-size: 11px; padding: 2px 8px; border-radius: 99px; }
    .ed-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

    /* ── Formatting bar ────────────────────────────────────── */
    .ed-fmt-bar {
      display: flex; align-items: center; gap: 3px; flex-wrap: wrap;
      padding: 5px 10px; background: #f8fafc; border: 1px solid #e2e8f0;
      border-radius: 8px 8px 0 0; border-bottom: none;
    }
    .fmt-select {
      padding: 4px 6px; border: 1px solid #cbd5e1; border-radius: 5px;
      font-size: 12px; background: #fff; color: #374151; cursor: pointer;
      outline: none; height: 28px;
    }
    .fmt-select:focus { border-color: #2563eb; }
    .fmt-btn {
      width: 28px; height: 28px; border: 1px solid transparent; border-radius: 5px;
      background: transparent; color: #374151; cursor: pointer; display: inline-flex;
      align-items: center; justify-content: center; font-size: 13px; padding: 0;
      transition: background .1s, border-color .1s;
    }
    .fmt-btn:hover { background: #e2e8f0; border-color: #cbd5e1; }
    .fmt-btn:active { background: #dbeafe; border-color: #93c5fd; color: #1d4ed8; }
    .fmt-sep {
      width: 1px; height: 18px; background: #e2e8f0; margin: 0 4px; flex-shrink: 0;
    }
    .var-count-badge {
      margin-left: 6px; background: #dcfce7; color: #15803d;
      font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 99px;
    }

    /* ── Layout ────────────────────────────────────────────── */
    .ed-layout {
      display: grid; grid-template-columns: 1fr 280px; gap: 0;
      height: calc(100vh - 200px); min-height: 520px;
    }
    .ed-pane-editor { display: flex; flex-direction: column; }

    /* ── WYSIWYG editor surface ─────────────────────────────── */
    .wysiwyg-editor {
      flex: 1; overflow-y: auto;
      border: 1px solid #e2e8f0; border-radius: 0 0 0 8px;
      background: #fff; color: #1e293b;
      font-family: 'Georgia', 'Times New Roman', serif;
      font-size: 14px; line-height: 1.8;
      padding: 32px 48px; outline: none;
      transition: border-color .15s, box-shadow .15s;
      min-height: 400px;
    }
    .wysiwyg-editor:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37,99,235,.08);
    }
    .wysiwyg-editor.drag-active {
      border-color: #10b981; border-style: dashed;
      background: #f0fdf4;
    }
    .wysiwyg-editor:empty::before {
      content: attr(data-placeholder);
      color: #94a3b8; font-style: italic; pointer-events: none;
    }

    /* ── Typography in the editor ───────────────────────────── */
    .wysiwyg-editor :global(h1) { font-size: 22px; font-weight: 700; margin: 16px 0 8px; color: #0f172a; }
    .wysiwyg-editor :global(h2) { font-size: 18px; font-weight: 700; margin: 14px 0 6px; color: #1e293b; }
    .wysiwyg-editor :global(h3) { font-size: 15px; font-weight: 600; margin: 12px 0 5px; color: #334155; }
    .wysiwyg-editor :global(h4) { font-size: 13px; font-weight: 600; margin: 10px 0 4px; color: #475569; }
    .wysiwyg-editor :global(p)  { margin: 6px 0; }
    .wysiwyg-editor :global(blockquote) {
      border-left: 3px solid #cbd5e1; padding-left: 14px;
      color: #64748b; margin: 8px 0;
    }
    .wysiwyg-editor :global(ul), .wysiwyg-editor :global(ol) { margin: 6px 0; padding-left: 22px; }
    .wysiwyg-editor :global(hr) { border: none; border-top: 1px solid #e2e8f0; margin: 16px 0; }
    .wysiwyg-editor :global(table) { border-collapse: collapse; width: 100%; }
    .wysiwyg-editor :global(td), .wysiwyg-editor :global(th) {
      border: 1px solid #e2e8f0; padding: 6px 10px; font-size: 13px;
    }

    /* ── Variable pill (inside editor) ─────────────────────── */
    :global(.var-pill) {
      display: inline-flex; align-items: center;
      background: #eff6ff; color: #1d4ed8;
      border: 1px solid #bfdbfe; border-radius: 4px;
      padding: 1px 7px; font-size: 12px; font-weight: 600;
      font-family: 'Inter', system-ui, sans-serif;
      cursor: default; user-select: none; white-space: nowrap;
      vertical-align: middle; line-height: 1.5;
    }

    /* ── Raw HTML mode ──────────────────────────────────────── */
    .raw-mode-wrapper { display: flex; flex-direction: column; flex: 1; }
    .raw-mode-banner {
      display: flex; align-items: center; gap: 8px;
      background: #fef3c7; color: #92400e; font-size: 11px;
      padding: 8px 12px; border: 1px solid #fde68a; border-bottom: none;
    }
    .raw-mode-banner code { background: #fde68a; padding: 0 4px; border-radius: 3px; font-size: 10px; }
    .html-editor {
      flex: 1; font-family: 'Courier New', Consolas, monospace; font-size: 12px;
      line-height: 1.65; border: 1px solid #e2e8f0; border-radius: 0 0 0 8px;
      padding: 16px; resize: none; background: #0f172a; color: #e2e8f0;
      outline: none; box-sizing: border-box; tab-size: 2;
    }
    .html-editor:focus { border-color: #2563eb; }

    /* ── Variable palette (right pane) ──────────────────────── */
    .ed-pane-ref {
      display: flex; flex-direction: column; gap: 8px;
      overflow-y: auto; border: 1px solid #e2e8f0;
      border-left: none; border-radius: 0 8px 8px 0;
      padding: 12px; background: #f8fafc;
    }
    .ref-palette-title {
      display: flex; align-items: center; gap: 5px;
      font-size: 11px; font-weight: 700; color: #475569;
      text-transform: uppercase; letter-spacing: 0.06em; padding-bottom: 4px;
      border-bottom: 1px solid #e2e8f0; margin-bottom: 4px;
    }
    .ref-search {
      width: 100%; box-sizing: border-box; padding: 7px 10px;
      border: 1px solid #cbd5e1; border-radius: 6px; font-size: 12px;
      background: #fff; outline: none;
    }
    .ref-search:focus { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37,99,235,.15); }
    .ref-hint { font-size: 11px; color: #94a3b8; line-height: 1.4; }
    .ref-hint code { background: #e2e8f0; padding: 0 3px; border-radius: 3px; font-size: 10px; }

    .ref-group { display: flex; flex-direction: column; gap: 4px; }
    .ref-group-head { display: flex; align-items: center; gap: 6px; padding: 4px 2px; }
    .ref-group-icon { font-size: 13px; }
    .ref-group-label { font-size: 10px; font-weight: 700; color: #475569; text-transform: uppercase; letter-spacing: 0.05em; }
    .ref-group-count { margin-left: auto; background: #e2e8f0; color: #64748b; font-size: 10px; padding: 1px 5px; border-radius: 99px; font-weight: 600; }
    .ref-group-body { display: flex; flex-direction: column; gap: 3px; }

    .var-chip {
      display: flex; flex-direction: column; gap: 1px;
      padding: 6px 9px; border-radius: 6px; cursor: grab;
      background: #fff; border: 1px solid #e2e8f0;
      transition: all .12s;
    }
    .var-chip:hover { background: #eff6ff; border-color: #93c5fd; transform: translateX(-2px); box-shadow: 0 1px 4px rgba(37,99,235,.12); }
    .var-chip:active { cursor: grabbing; }
    .var-chip-label { font-size: 11px; color: #374151; font-weight: 500; line-height: 1.3; }
    .var-chip-token { font-size: 9px; color: #94a3b8; font-family: monospace; margin-top: 1px; }

    .ref-empty { font-size: 11px; color: #94a3b8; text-align: center; padding: 16px 0; }
    .toast { background: #dcfce7; color: #15803d; font-size: 11px; padding: 6px 10px; border-radius: 6px; text-align: center; font-weight: 500; }

    /* ── Shared buttons ─────────────────────────────────────── */
    .btn {
      display: inline-flex; align-items: center; gap: 5px;
      padding: 6px 14px; border-radius: 6px; font-size: 13px;
      font-weight: 500; cursor: pointer; border: none; text-decoration: none;
      white-space: nowrap;
    }
    .btn-primary { background: #2563eb; color: #fff; }
    .btn-primary:hover:not(:disabled) { background: #1d4ed8; }
    .btn-primary:disabled { opacity: .6; cursor: not-allowed; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #475569; }
    .btn-outline:hover { background: #f1f5f9; }
    .btn-ghost  { background: transparent; border: 1px solid #e2e8f0; color: #64748b; font-size: 12px; padding: 5px 12px; }
    .btn-ghost:hover { background: #f1f5f9; color: #374151; }
    .btn-danger-soft { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; }
    .btn-danger-soft:hover { background: #fee2e2; }
    .msg-error   { color: #dc2626; font-size: 12px; }
    .msg-success { color: #16a34a; font-size: 12px; }

    /* Image button */
    .fmt-btn-image { width: auto; padding: 0 8px; gap: 4px; font-size: 12px; color: #374151; }
    .fmt-btn-image:disabled { opacity: .5; cursor: not-allowed; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .spin { animation: spin .8s linear infinite; }
  `],
})
export class TemplateEditorComponent implements OnInit, AfterViewInit {
  private route  = inject(ActivatedRoute);
  private router = inject(Router);
  private svc    = inject(TemplateService);
  private http   = inject(HttpClient);

  @ViewChild('editor')     editorRef!: ElementRef<HTMLDivElement>;
  @ViewChild('rawEditor')  rawEditorRef!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('imageInput') imageInputRef!: ElementRef<HTMLInputElement>;

  type!: TemplateType;
  isCustom       = false;
  saving         = false;
  imageUploading = false;
  saveError    = '';
  saveSuccess  = false;
  toast        = '';
  searchTerm   = '';
  isDraggingOver = false;
  insertedCount  = 0;
  rawMode        = false;
  rawHtml        = '';

  private savedRange: Range | null = null;
  private loadedHtml = '';

  get typeLabel():  string { return TYPE_LABELS[this.type] ?? this.type; }
  get previewHref(): string { return this.svc.previewUrl(this.type); }
  private get editorEl(): HTMLDivElement { return this.editorRef?.nativeElement; }

  // ── Variable catalog ──────────────────────────────────────

  readonly groups: VarGroup[] = [
    {
      id: 'societe', label: 'Société & contexte', icon: '🏢',
      vars: [
        { var: 'societeName',   desc: 'Nom de la société' },
        { var: 'projectName',   desc: 'Nom du projet' },
        { var: 'agentName',     desc: 'Nom de l\'agent' },
        { var: 'agentEmail',    desc: 'Email de l\'agent' },
        { var: 'generatedAt',   desc: 'Date/heure de génération' },
        { var: 'createdAt',     desc: 'Date de création' },
      ],
    },
    {
      id: 'property', label: 'Bien immobilier', icon: '🏠',
      vars: [
        { var: 'propertyRef',   desc: 'Référence du bien' },
        { var: 'propertyTitle', desc: 'Titre du bien' },
        { var: 'propertyType',  desc: 'Type (APPARTEMENT, VILLA…)' },
        { var: 'agreedPrice',   desc: 'Prix de vente convenu' },
        { var: 'listPrice',     desc: 'Prix catalogue' },
        { var: 'prixVente',     desc: 'Prix de vente (pipeline)' },
      ],
    },
    {
      id: 'buyer', label: 'Acheteur', icon: '👤',
      vars: [
        { var: 'buyerName',        desc: 'Nom complet de l\'acheteur' },
        { var: 'buyerDisplayName', desc: 'Nom affiché' },
        { var: 'buyerPhone',       desc: 'Téléphone' },
        { var: 'buyerEmail',       desc: 'Email' },
        { var: 'buyerAddress',     desc: 'Adresse' },
        { var: 'buyerNationalId',  desc: 'CIN / N° pièce d\'identité' },
        { var: 'buyerIce',         desc: 'ICE / numéro fiscal' },
        { var: 'buyerTypeLabel',   desc: 'Personne physique / morale' },
      ],
    },
    {
      id: 'contract', label: 'Contrat / Vente', icon: '📄',
      vars: [
        { var: 'venteRef',         desc: 'Référence de la vente' },
        { var: 'contractRef',      desc: 'Référence du contrat' },
        { var: 'contractStatus',   desc: 'Statut du contrat' },
        { var: 'statut',           desc: 'Statut de la vente' },
        { var: 'dateCompromis',    desc: 'Date du compromis' },
        { var: 'dateActeNotarie',  desc: 'Date acte notarié' },
        { var: 'dateLivraisonPrevue', desc: 'Date de livraison prévue' },
        { var: 'signedAt',         desc: 'Date de signature' },
        { var: 'depositReference', desc: 'Réf. acompte / réservation' },
        { var: 'depositAmount',    desc: 'Montant de l\'acompte' },
        { var: 'depositDate',      desc: 'Date de l\'acompte' },
        { var: 'dueDate',          desc: 'Date d\'échéance' },
        { var: 'notes',            desc: 'Notes libres' },
      ],
    },
  ];

  filteredGroups: VarGroup[] = this.groups;

  // ── Lifecycle ─────────────────────────────────────────────

  ngOnInit(): void {
    this.type = this.route.snapshot.paramMap.get('type') as TemplateType;
    this.svc.getSource(this.type).subscribe({
      next: res => {
        this.loadedHtml = res.htmlContent;
        this.isCustom   = res.custom;
        if (this.editorRef?.nativeElement) {
          this.loadIntoEditor(res.htmlContent);
        }
      },
      error: () => { this.saveError = 'Impossible de charger le modèle.'; },
    });
  }

  ngAfterViewInit(): void {
    if (this.loadedHtml && this.editorRef?.nativeElement) {
      this.loadIntoEditor(this.loadedHtml);
    }
  }

  // ── Toggle raw / WYSIWYG ──────────────────────────────────

  toggleRaw(): void {
    if (!this.rawMode) {
      this.rawHtml = this.serializeContent();
      this.rawMode = true;
    } else {
      this.rawMode = false;
      setTimeout(() => {
        if (this.editorRef?.nativeElement) {
          this.loadIntoEditor(this.rawHtml);
        }
      });
    }
  }

  // ── Load / serialize ──────────────────────────────────────

  private loadIntoEditor(html: string): void {
    const varMap = this.buildVarMap();
    const withPills = html.replace(/\$\{model\.([a-zA-Z0-9_]+)\}/g, (_, varName) => {
      const desc = varMap[varName] ?? varName;
      return `<span class="var-pill" data-var="${varName}" contenteditable="false">${desc}</span>`;
    });
    this.editorEl.innerHTML = withPills;
    this.recountInserted();
  }

  private serializeContent(): string {
    if (this.rawMode) return this.rawHtml;
    if (!this.editorEl) return '';
    const clone = this.editorEl.cloneNode(true) as HTMLElement;
    clone.querySelectorAll('span[data-var]').forEach(span => {
      const varName = span.getAttribute('data-var')!;
      span.replaceWith(`\${model.${varName}}`);
    });
    return clone.innerHTML;
  }

  private buildVarMap(): Record<string, string> {
    const map: Record<string, string> = {};
    for (const g of this.groups) {
      for (const v of g.vars) { map[v.var] = v.desc; }
    }
    return map;
  }

  // ── Actions ───────────────────────────────────────────────

  save(): void {
    this.saving     = true;
    this.saveError  = '';
    this.saveSuccess = false;
    const content = this.serializeContent();
    this.svc.upsert(this.type, content).subscribe({
      next: () => {
        this.saving = false;
        this.isCustom = true;
        this.saveSuccess = true;
        setTimeout(() => this.saveSuccess = false, 3000);
      },
      error: (err) => {
        this.saving    = false;
        this.saveError = err?.error?.message ?? 'Erreur lors de la sauvegarde.';
      },
    });
  }

  revert(): void {
    if (!confirm(`Réinitialiser "${this.typeLabel}" vers le modèle intégré ?`)) return;
    this.svc.delete(this.type).subscribe({
      next: () => { this.isCustom = false; this.router.navigateByUrl('/app/templates'); },
      error: (err) => { this.saveError = err?.error?.message ?? 'Erreur lors de la réinitialisation.'; },
    });
  }

  // ── Formatting commands ───────────────────────────────────

  exec(cmd: string, val?: string): void {
    this.editorEl?.focus();
    document.execCommand(cmd, false, val ?? '');
  }

  execFormatBlock(tag: string): void {
    if (!tag) return;
    this.editorEl?.focus();
    document.execCommand('formatBlock', false, tag);
  }

  // ── Selection management ──────────────────────────────────

  saveSelection(): void {
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      this.savedRange = sel.getRangeAt(0).cloneRange();
    }
  }

  private restoreSelection(): boolean {
    if (!this.savedRange) return false;
    const sel = window.getSelection();
    if (!sel) return false;
    sel.removeAllRanges();
    sel.addRange(this.savedRange);
    return true;
  }

  // ── Variable insertion ────────────────────────────────────

  onVarClick(varName: string): void {
    if (this.rawMode) {
      const token = `\${model.${varName}}`;
      navigator.clipboard?.writeText(token).catch(() => {});
      this.flashToast(`Copié : \${model.${varName}}`);
      return;
    }
    this.insertVar(varName);
  }

  insertVar(varName: string): void {
    const chip = this.makeChip(varName);
    this.editorEl.focus();
    this.restoreSelection();

    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      // Only insert inside the editor
      if (this.editorEl.contains(range.commonAncestorContainer)) {
        range.deleteContents();
        range.insertNode(chip);
        range.setStartAfter(chip);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
        this.savedRange = range.cloneRange();
        this.recountInserted();
        this.flashToast(`Inséré : ${chip.textContent}`);
        return;
      }
    }
    // Fallback: append at end
    this.editorEl.appendChild(chip);
    this.recountInserted();
    this.flashToast(`Inséré : ${chip.textContent}`);
  }

  private makeChip(varName: string): HTMLSpanElement {
    const desc = this.buildVarMap()[varName] ?? varName;
    const span = document.createElement('span');
    span.className = 'var-pill';
    span.setAttribute('data-var', varName);
    span.setAttribute('contenteditable', 'false');
    span.textContent = desc;
    return span;
  }

  // ── Drag & drop ───────────────────────────────────────────

  onDragStart(ev: DragEvent, varName: string): void {
    ev.dataTransfer?.setData('application/x-var', varName);
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'copy';
  }

  onDragOver(ev: DragEvent): void {
    ev.preventDefault();
    if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'copy';
    this.isDraggingOver = true;
  }

  onDragLeave(_ev: DragEvent): void { this.isDraggingOver = false; }
  onDragEnd():  void                { this.isDraggingOver = false; }

  onDrop(ev: DragEvent): void {
    ev.preventDefault();
    this.isDraggingOver = false;
    const varName = ev.dataTransfer?.getData('application/x-var');
    if (!varName) return;

    const chip = this.makeChip(varName);
    const docAny = document as Document & {
      caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null;
      caretRangeFromPoint?:    (x: number, y: number) => Range | null;
    };

    let range: Range | null = null;
    if (docAny.caretRangeFromPoint) {
      range = docAny.caretRangeFromPoint(ev.clientX, ev.clientY);
    } else if (docAny.caretPositionFromPoint) {
      const cp = docAny.caretPositionFromPoint(ev.clientX, ev.clientY);
      if (cp) {
        range = document.createRange();
        range.setStart(cp.offsetNode, cp.offset);
        range.collapse(true);
      }
    }

    if (range && this.editorEl.contains(range.commonAncestorContainer)) {
      range.insertNode(chip);
      range.setStartAfter(chip);
      range.collapse(true);
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(range);
      this.savedRange = range.cloneRange();
    } else {
      this.editorEl.appendChild(chip);
    }

    this.editorEl.focus();
    this.recountInserted();
    this.flashToast(`Inséré : ${chip.textContent}`);
  }

  // ── Search ────────────────────────────────────────────────

  filterVars(): void {
    const q = this.searchTerm.trim().toLowerCase();
    if (!q) { this.filteredGroups = this.groups; return; }
    this.filteredGroups = this.groups
      .map(g => ({ ...g, vars: g.vars.filter(v =>
        v.var.toLowerCase().includes(q) || v.desc.toLowerCase().includes(q)) }))
      .filter(g => g.vars.length > 0);
  }

  // ── Image upload ──────────────────────────────────────────

  triggerImagePicker(): void {
    this.imageInputRef.nativeElement.value = '';
    this.imageInputRef.nativeElement.click();
  }

  onImageFileSelected(ev: Event): void {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (!file) return;

    if (file.size > 3 * 1024 * 1024) {
      this.flashToast('Image trop grande (max 3 Mo)');
      return;
    }

    const form = new FormData();
    form.append('file', file);
    this.imageUploading = true;
    this.saveSelection();

    this.http.post<{ dataUri: string }>('/api/templates/images', form).subscribe({
      next: ({ dataUri }) => {
        this.imageUploading = false;
        this.insertImage(dataUri, file.name);
      },
      error: () => {
        this.imageUploading = false;
        this.flashToast('Erreur lors du chargement de l\'image.');
      },
    });
  }

  private insertImage(dataUri: string, name: string): void {
    const img = document.createElement('img');
    img.src = dataUri;
    img.alt = name.replace(/\.[^.]+$/, '');
    img.style.cssText = 'max-width:240px;height:auto;display:block;margin:8px 0;';

    this.editorEl.focus();
    this.restoreSelection();

    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      if (this.editorEl.contains(range.commonAncestorContainer)) {
        range.deleteContents();
        range.insertNode(img);
        range.setStartAfter(img);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
        this.savedRange = range.cloneRange();
        return;
      }
    }
    this.editorEl.insertBefore(img, this.editorEl.firstChild);
  }

  // ── Helpers ───────────────────────────────────────────────

  recountInserted(): void {
    const pills = this.editorEl?.querySelectorAll('span[data-var]').length ?? 0;
    this.insertedCount = pills;
  }

  private flashToast(msg: string): void {
    this.toast = msg;
    setTimeout(() => this.toast = '', 1800);
  }
}
