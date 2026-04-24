import { Directive, HostBinding, Input } from '@angular/core';

/**
 * Applies the `.skeleton` shimmer class and hides real content from
 * assistive technology while `loading` is true.
 *
 * Usage:
 *   <span [appSkeleton]="isLoading" class="skeleton-card"></span>
 */
@Directive({
  selector: '[appSkeleton]',
  standalone: true,
})
export class SkeletonDirective {
  @Input({ alias: 'appSkeleton' }) loading = false;

  @HostBinding('class.skeleton')
  get skeletonClass(): boolean { return this.loading; }

  @HostBinding('attr.aria-hidden')
  get ariaHidden(): string | null { return this.loading ? 'true' : null; }
}
