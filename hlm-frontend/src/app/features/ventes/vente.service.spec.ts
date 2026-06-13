import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { VenteService, Vente, VenteStatut } from './vente.service';
import { environment } from '../../../environments/environment';

const API = `${environment.apiUrl}/api/ventes`;

describe('VenteService', () => {
  let service: VenteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(VenteService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list()', () => {
    it('GET /api/ventes and unwraps content array', () => {
      const ventes: Partial<Vente>[] = [{ id: '1', statut: 'COMPROMIS' as VenteStatut }];
      service.list().subscribe(result => {
        expect(result.length).toBe(1);
        expect(result[0].statut).toBe('COMPROMIS');
      });
      http.expectOne(req => req.url === API && req.method === 'GET')
          .flush({ content: ventes, totalPages: 1, totalElements: 1, number: 0, size: 1000 });
    });

    it('passes a large size parameter so all rows are returned', () => {
      service.list().subscribe();
      const req = http.expectOne(r => r.url === API);
      expect(Number(req.request.params.get('size'))).toBeGreaterThan(100);
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 1000 });
    });
  });

  describe('listByContact()', () => {
    it('sends contactId as query param and unwraps content', () => {
      service.listByContact('contact-123').subscribe(list => {
        expect(list).toEqual([]);
      });
      const req = http.expectOne(r => r.url === API);
      expect(req.request.params.get('contactId')).toBe('contact-123');
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 1000 });
    });
  });

  describe('get()', () => {
    it('GET /api/ventes/:id', () => {
      const vente: Partial<Vente> = { id: 'abc', statut: 'ACTE' as VenteStatut };
      service.get('abc').subscribe(v => expect(v.id).toBe('abc'));
      http.expectOne(`${API}/abc`).flush(vente);
    });
  });

  describe('create()', () => {
    it('POST /api/ventes with the request body', () => {
      const body = { contactId: 'c1', propertyId: 'p1', prixVente: 1000000 } as any;
      service.create(body).subscribe();
      const req = http.expectOne(API);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.contactId).toBe('c1');
      req.flush({});
    });
  });

  describe('updateStatut()', () => {
    it('PATCH /api/ventes/:id/statut with the statut request object', () => {
      service.updateStatut('v1', { statut: 'ANNULE' as VenteStatut, motifAnnulation: 'ACCORD_PARTIES' }).subscribe();
      const req = http.expectOne(`${API}/v1/statut`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body.statut).toBe('ANNULE');
      expect(req.request.body.motifAnnulation).toBe('ACCORD_PARTIES');
      req.flush({});
    });

    it('PATCH without motif for a non-cancellation transition', () => {
      service.updateStatut('v1', { statut: 'COMPROMIS' as VenteStatut }).subscribe();
      const req = http.expectOne(`${API}/v1/statut`);
      expect(req.request.body.motifAnnulation).toBeUndefined();
      req.flush({});
    });
  });

  describe('inviteBuyer()', () => {
    it('POST /api/ventes/:id/portal/invite', () => {
      service.inviteBuyer('v2').subscribe();
      const req = http.expectOne(`${API}/v2/portal/invite`);
      expect(req.request.method).toBe('POST');
      req.flush({ message: 'ok', magicLinkUrl: 'https://example.com' });
    });
  });

  describe('listPage()', () => {
    it('passes page and size params for paginated view', () => {
      service.listPage(2, 20).subscribe();
      const req = http.expectOne(r => r.url === API);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('20');
      req.flush({ content: [], totalPages: 0, totalElements: 0, number: 2, size: 20 });
    });
  });
});
