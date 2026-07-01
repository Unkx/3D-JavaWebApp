import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ListingService, Listing, PageResponse, StlFile } from './listing.service';

describe('ListingService', () => {
  let service: ListingService;
  let httpMock: HttpTestingController;

  const listing: Listing = { id: '1', title: 'T', description: 'D', requiredMaterial: 'PLA' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ListingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getListings() defaults to page 0, size 12, no search', () => {
    let result: PageResponse<Listing> | undefined;
    service.getListings().subscribe(r => (result = r));
    const req = httpMock.expectOne('/api/listings?page=0&size=12');
    expect(req.request.method).toBe('GET');
    const page: PageResponse<Listing> = { content: [listing], page: 0, size: 12, totalElements: 1, totalPages: 1, last: true };
    req.flush(page);
    expect(result).toEqual(page);
  });

  it('getListings() includes the trimmed, encoded search term when provided', () => {
    service.getListings(2, 5, '  hello world  ').subscribe();
    const req = httpMock.expectOne('/api/listings?page=2&size=5&search=hello%20world');
    req.flush({ content: [], page: 2, size: 5, totalElements: 0, totalPages: 0, last: true });
  });

  it('getListings() omits the search param when the trimmed term is empty', () => {
    service.getListings(0, 12, '   ').subscribe();
    httpMock.expectOne('/api/listings?page=0&size=12');
  });

  it('getListing() GETs a single listing by id', () => {
    service.getListing('1').subscribe();
    const req = httpMock.expectOne('/api/listings/1');
    expect(req.request.method).toBe('GET');
    req.flush(listing);
  });

  it('getMyListings() GETs the current user listings', () => {
    service.getMyListings().subscribe();
    const req = httpMock.expectOne('/api/listings/my');
    expect(req.request.method).toBe('GET');
    req.flush([listing]);
  });

  it('createListing() POSTs the listing payload', () => {
    service.createListing({ title: 'T', description: 'D', requiredMaterial: 'PLA' }).subscribe();
    const req = httpMock.expectOne('/api/listings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ title: 'T', description: 'D', requiredMaterial: 'PLA' });
    req.flush(listing);
  });

  it('updateListing() PUTs the full listing', () => {
    service.updateListing('1', listing).subscribe();
    const req = httpMock.expectOne('/api/listings/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(listing);
    req.flush(listing);
  });

  it('deleteListing() issues a DELETE', () => {
    service.deleteListing('1').subscribe();
    const req = httpMock.expectOne('/api/listings/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('uploadStlFile() POSTs a FormData with the file and enables progress reporting', () => {
    const file = new File(['data'], 'model.stl');
    service.uploadStlFile('1', file).subscribe();
    const req = httpMock.expectOne('/api/listings/1/upload-stl');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    expect(req.request.reportProgress).toBe(true);
    req.flush(listing);
  });

  it('getStlFiles() GETs the file list for a listing', () => {
    const files: StlFile[] = [];
    service.getStlFiles('1').subscribe();
    const req = httpMock.expectOne('/api/listings/1/stl-files');
    expect(req.request.method).toBe('GET');
    req.flush(files);
  });

  it('uploadStlFiles() appends each file under the "files" key', () => {
    const files = [new File(['a'], 'a.stl'), new File(['b'], 'b.obj')];
    service.uploadStlFiles('1', files).subscribe();
    const req = httpMock.expectOne('/api/listings/1/stl-files');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body.getAll('files').length).toBe(2);
    req.flush([]);
  });

  it('deleteStlFile() issues a DELETE for the given file', () => {
    service.deleteStlFile('1', 'f1').subscribe();
    const req = httpMock.expectOne('/api/listings/1/stl-files/f1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('reorderStlFiles() PATCHes the ordered id list', () => {
    service.reorderStlFiles('1', ['a', 'b']).subscribe();
    const req = httpMock.expectOne('/api/listings/1/stl-files/reorder');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(['a', 'b']);
    req.flush(null);
  });

  it('patchListing() PATCHes the update payload', () => {
    service.patchListing('1', { description: 'D2', requiredMaterial: 'ABS' }).subscribe();
    const req = httpMock.expectOne('/api/listings/1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ description: 'D2', requiredMaterial: 'ABS' });
    req.flush(listing);
  });

  it('propagates HTTP errors', () => {
    let error: unknown;
    service.getListing('missing').subscribe({ error: (e) => (error = e) });
    httpMock.expectOne('/api/listings/missing').flush('not found', { status: 404, statusText: 'Not Found' });
    expect((error as { status: number }).status).toBe(404);
  });
});
