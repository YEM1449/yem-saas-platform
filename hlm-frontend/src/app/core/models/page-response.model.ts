/** Flat shape — endpoints that return a raw Spring `Page<T>` (tasks, admin-users). */
export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Pagination metadata in the backend `PageResponse.of()` envelope (contacts, ventes, properties). */
export interface PageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Nested envelope returned by backend `PageResponse.of(page)` — `{ content, page }`. */
export interface PagedResult<T> {
  content: T[];
  page: PageMeta;
}
