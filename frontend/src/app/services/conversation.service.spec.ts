import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ConversationService, ChatMessage, ConversationSummary } from './conversation.service';

describe('ConversationService', () => {
  let service: ConversationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ConversationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getMyConversations() issues a GET to /api/conversations', () => {
    const summaries: ConversationSummary[] = [];
    let result: ConversationSummary[] | undefined;
    service.getMyConversations().subscribe(r => (result = r));
    const req = httpMock.expectOne('/api/conversations');
    expect(req.request.method).toBe('GET');
    req.flush(summaries);
    expect(result).toBe(summaries);
  });

  it('createOrGet() POSTs listingId only when otherUserId is omitted', () => {
    service.createOrGet('listing1').subscribe();
    const req = httpMock.expectOne('/api/conversations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ listingId: 'listing1' });
    req.flush({ id: 'conv1' });
  });

  it('createOrGet() includes otherUserId in the body when provided', () => {
    service.createOrGet('listing1', 'user2').subscribe();
    const req = httpMock.expectOne('/api/conversations');
    expect(req.request.body).toEqual({ listingId: 'listing1', otherUserId: 'user2' });
    req.flush({ id: 'conv1' });
  });

  it('getMessages() GETs the messages for a conversation', () => {
    const messages: ChatMessage[] = [];
    service.getMessages('conv1').subscribe();
    const req = httpMock.expectOne('/api/conversations/conv1/messages');
    expect(req.request.method).toBe('GET');
    req.flush(messages);
  });

  it('sendMessage() POSTs the content', () => {
    service.sendMessage('conv1', 'hello').subscribe();
    const req = httpMock.expectOne('/api/conversations/conv1/messages');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'hello' });
    req.flush({});
  });

  it('markRead() PUTs an empty body', () => {
    service.markRead('conv1').subscribe();
    const req = httpMock.expectOne('/api/conversations/conv1/read');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  it('getUnreadCount() GETs the unread count', () => {
    let result: { count: number } | undefined;
    service.getUnreadCount().subscribe(r => (result = r));
    const req = httpMock.expectOne('/api/conversations/unread-count');
    expect(req.request.method).toBe('GET');
    req.flush({ count: 3 });
    expect(result).toEqual({ count: 3 });
  });

  it('propagates server errors', () => {
    let error: unknown;
    service.getMyConversations().subscribe({ error: (e) => (error = e) });
    httpMock.expectOne('/api/conversations').flush('fail', { status: 500, statusText: 'Server Error' });
    expect((error as { status: number }).status).toBe(500);
  });
});
