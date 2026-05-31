/**
 * HLM UI — shared component library (barrel).
 *
 * Standalone, typed, accessible components that wrap the canonical design-token /
 * global-CSS system. Import individually (tree-shakable):
 *
 *   import { UiButtonComponent, UiKpiCardComponent } from '@app/shared/ui';
 *
 * or by relative path from a feature component's `imports: [...]`.
 *
 * See ./README.md for the catalogue and usage guidelines.
 */
export { UiButtonComponent } from './ui-button.component';
export type { UiButtonVariant, UiButtonSize } from './ui-button.component';

export { UiCardComponent } from './ui-card.component';

export { UiKpiCardComponent } from './ui-kpi-card.component';
export type { UiKpiTone, UiTrend } from './ui-kpi-card.component';

export { UiProgressCardComponent } from './ui-progress-card.component';

export { UiStatusPillComponent } from './ui-status-pill.component';

export { UiEmptyStateComponent } from './ui-empty-state.component';

export { UiPageHeaderComponent } from './ui-page-header.component';
export type { UiBreadcrumb } from './ui-page-header.component';
