import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { PropertyService, CreatePropertyRequest } from './property.service';
import { environment } from '../../../environments/environment';

const API = `${environment.apiUrl}/api/properties`;

describe('PropertyService', () => {
  let service: PropertyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(PropertyService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('GET /api/properties and unwraps content', () => {
      service.list().subscribe(props => expect(props).toEqual([]));
      http.expectOne(r => r.url === API && r.method === 'GET')
          .flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 2000 });
    });

    it('passes projectId filter when provided', () => {
      service.list({ projectId: 'proj-1' }).subscribe();
      const req = http.expectOne(r => r.url === API);
      expect(req.request.params.get('projectId')).toBe('proj-1');
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 2000 });
    });

    it('passes immeubleId filter when provided', () => {
      service.list({ immeubleId: 'imm-7' }).subscribe();
      const req = http.expectOne(r => r.url === API);
      expect(req.request.params.get('immeubleId')).toBe('imm-7');
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 2000 });
    });

    it('passes status filter when provided', () => {
      service.list({ status: 'ACTIVE' }).subscribe();
      const req = http.expectOne(r => r.url === API);
      expect(req.request.params.get('status')).toBe('ACTIVE');
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 2000 });
    });

    it('does NOT pass undefined filters as params', () => {
      service.list({}).subscribe();
      const req = http.expectOne(r => r.url === API);
      expect(req.request.params.has('projectId')).toBeFalse();
      expect(req.request.params.has('immeubleId')).toBeFalse();
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 2000 });
    });
  });

  describe('listPage()', () => {
    it('sends page and size params for paginated navigation', () => {
      service.listPage({ projectId: 'p1' }, 3, 25).subscribe();
      const req = http.expectOne(r => r.url === API);
      expect(req.request.params.get('page')).toBe('3');
      expect(req.request.params.get('size')).toBe('25');
      expect(req.request.params.get('projectId')).toBe('p1');
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 3, size: 25 });
    });
  });

  describe('getById()', () => {
    it('GET /api/properties/:id', () => {
      service.getById('prop-42').subscribe(p => expect(p).toBeTruthy());
      http.expectOne(`${API}/prop-42`).flush({ id: 'prop-42' });
    });
  });

  describe('create()', () => {
    it('POST /api/properties with body', () => {
      const req = { type: 'APPARTEMENT', title: 'T3', referenceCode: 'A01', price: 500000, projectId: 'proj-1' } as CreatePropertyRequest;
      service.create(req).subscribe();
      const httpReq = http.expectOne(API);
      expect(httpReq.request.method).toBe('POST');
      expect(httpReq.request.body.type).toBe('APPARTEMENT');
      httpReq.flush({});
    });
  });

  describe('setStatus()', () => {
    it('PATCH /api/properties/:id/status with status in body', () => {
      service.setStatus('prop-1', 'WITHDRAWN').subscribe();
      const req = http.expectOne(`${API}/prop-1/status`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ status: 'WITHDRAWN' });
      req.flush({});
    });
  });

  describe('downloadMediaUrl()', () => {
    it('returns the correct media download URL (pure function, no HTTP)', () => {
      const url = service.downloadMediaUrl('media-99');
      expect(url).toBe(`${environment.apiUrl}/api/media/media-99/download`);
    });
  });
});
