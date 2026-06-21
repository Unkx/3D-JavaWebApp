import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ConversationSummary {
  id: string;
  listingId: string;
  listingTitle: string;
  otherParticipantEmail: string;
  lastMessageContent: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface ChatMessage {
  id: string;
  conversation: { id: string };
  sender: { id: string; email: string };
  content: string;
  read: boolean;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private http = inject(HttpClient);
  private apiUrl = '/api/conversations';

  getMyConversations(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(this.apiUrl);
  }

  createOrGet(listingId: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(this.apiUrl, { listingId });
  }

  getMessages(conversationId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/${conversationId}/messages`);
  }

  sendMessage(conversationId: string, content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.apiUrl}/${conversationId}/messages`, { content });
  }

  markRead(conversationId: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${conversationId}/read`, {});
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`);
  }
}
