import { Component, ChangeDetectionStrategy, signal, computed, inject, OnInit, OnDestroy, ElementRef, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SlicePipe } from '@angular/common';
import { ConversationService, ConversationSummary, ChatMessage } from '../../services/conversation.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-messages',
  imports: [RouterLink, FormsModule, SlicePipe],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MessagesComponent implements OnInit, OnDestroy {
  private conversationService = inject(ConversationService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);

  conversations = signal<ConversationSummary[]>([]);
  loading = signal(true);
  selectedId = signal<string | null>(null);
  messages = signal<ChatMessage[]>([]);
  messagesLoading = signal(false);
  newMessage = signal('');
  sending = signal(false);

  private pollInterval: ReturnType<typeof setInterval> | null = null;
  readonly messageList = viewChild<ElementRef>('messageList');

  selectedConversation = computed(() => {
    const id = this.selectedId();
    return this.conversations().find(c => c.id === id) ?? null;
  });

  currentUserId(): string | null {
    return this.authService.currentUser()?.userId ?? null;
  }

  ngOnInit(): void {
    this.loadConversations();
    const convId = this.route.snapshot.queryParamMap.get('conv');
    if (convId) {
      setTimeout(() => this.selectConversation(convId), 500);
    }
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  private loadConversations(): void {
    this.loading.set(true);
    this.conversationService.getMyConversations().subscribe({
      next: data => { this.conversations.set(data); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  selectConversation(id: string): void {
    this.selectedId.set(id);
    this.loadMessages(id);
    this.conversationService.markRead(id).subscribe(() => {
      this.conversations.update(list =>
        list.map(c => c.id === id ? { ...c, unreadCount: 0 } : c)
      );
    });
    if (this.pollInterval) clearInterval(this.pollInterval);
    this.pollInterval = setInterval(() => this.loadMessages(id), 10000);
  }

  private loadMessages(conversationId: string): void {
    const isInitial = this.messages().length === 0;
    if (isInitial) this.messagesLoading.set(true);
    this.conversationService.getMessages(conversationId).subscribe({
      next: data => {
        this.messages.set(data);
        this.messagesLoading.set(false);
        setTimeout(() => this.scrollToBottom(), 50);
      },
      error: () => this.messagesLoading.set(false)
    });
  }

  sendMessage(): void {
    const content = this.newMessage().trim();
    const convId = this.selectedId();
    if (!content || !convId) return;
    this.sending.set(true);
    this.conversationService.sendMessage(convId, content).subscribe({
      next: msg => {
        this.messages.update(list => [...list, msg]);
        this.newMessage.set('');
        this.sending.set(false);
        setTimeout(() => this.scrollToBottom(), 50);
      },
      error: () => this.sending.set(false)
    });
  }

  private scrollToBottom(): void {
    const el = this.messageList()?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }

  isOwn(msg: ChatMessage): boolean {
    return msg.sender.id === this.currentUserId();
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleString('pl-PL', {
      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    });
  }
}
