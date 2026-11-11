const CANONICAL_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** Local UI ids. Conversation and upload session ids must use {@link conversationId}. */
export function genId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

/** Lowercase canonical UUID. The server rejects any other conversation or upload session id. */
export function isCanonicalUuid(id: string | null | undefined): id is string {
  return !!id && id.length === 36 && CANONICAL_UUID.test(id);
}

export function conversationId(): string {
  return crypto.randomUUID();
}
