import { apiFetch } from './client';
import type { NotificationItem, NotificationSetting, Page } from './types';

export const NOTIFICATION_PAGE_SIZE = 20;

export function fetchNotifications(page: number, signal?: AbortSignal): Promise<Page<NotificationItem>> {
  return apiFetch<Page<NotificationItem>>('/api/me/notifications', {
    query: { page, size: NOTIFICATION_PAGE_SIZE },
    signal,
  });
}

// 머리글에서 배경으로 부르는 요청이라, 세션이 끊겨 있어도 로그인 화면으로 보내지 않는다.
export function fetchUnreadCount(signal?: AbortSignal): Promise<{ count: number }> {
  return apiFetch<{ count: number }>('/api/me/notifications/unread-count', {
    signal,
    skipUnauthorizedHandler: true,
  });
}

export function markNotificationRead(notificationId: number): Promise<void> {
  return apiFetch<void>(`/api/me/notifications/${notificationId}/read`, { method: 'POST' });
}

export function markAllNotificationsRead(): Promise<void> {
  return apiFetch<void>('/api/me/notifications/read-all', { method: 'POST' });
}

export function fetchNotificationSettings(signal?: AbortSignal): Promise<NotificationSetting[]> {
  return apiFetch<NotificationSetting[]>('/api/me/notification-settings', { signal });
}

// 본문에 없는 종류는 서버가 기본값으로 되돌리므로 종류 전체를 보낸다.
export function updateNotificationSettings(items: NotificationSetting[]): Promise<NotificationSetting[]> {
  return apiFetch<NotificationSetting[]>('/api/me/notification-settings', { method: 'PUT', body: items });
}
