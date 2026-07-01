import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserService, User } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  const user: User = { id: '1', email: 'a@b.com', role: 'USER' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getUsers() GETs the users list', () => {
    let result: User[] | undefined;
    service.getUsers().subscribe(r => (result = r));
    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    req.flush([user]);
    expect(result).toEqual([user]);
  });

  it('getUser() GETs a single user by id', () => {
    service.getUser('1').subscribe();
    const req = httpMock.expectOne('/api/users/1');
    expect(req.request.method).toBe('GET');
    req.flush(user);
  });

  it('createUser() POSTs the user payload', () => {
    service.createUser(user).subscribe();
    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(user);
    req.flush(user);
  });

  it('updateUser() PUTs the user payload', () => {
    service.updateUser('1', user).subscribe();
    const req = httpMock.expectOne('/api/users/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(user);
    req.flush(user);
  });

  it('deleteUser() issues a DELETE', () => {
    service.deleteUser('1').subscribe();
    const req = httpMock.expectOne('/api/users/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('propagates HTTP errors', () => {
    let error: unknown;
    service.getUser('missing').subscribe({ error: (e) => (error = e) });
    httpMock.expectOne('/api/users/missing').flush('not found', { status: 404, statusText: 'Not Found' });
    expect((error as { status: number }).status).toBe(404);
  });
});
