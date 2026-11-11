import { apiFetch } from './client';

export interface ConversationSummary {
  id: string;
  title: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
}

export async function listConversations(): Promise<ConversationSummary[]> {
  const res = await apiFetch('/api/conversations');
  if (!res.ok) throw new Error('failed to list conversations');
  return res.json();
}

export async function getConversationMessages(id: string): Promise<ConversationMessage[] | null> {
  const res = await apiFetch(`/api/conversations/${id}/messages`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('failed to fetch conversation messages');
  return res.json();
}
