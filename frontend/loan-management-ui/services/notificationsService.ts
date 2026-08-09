import { get, post } from './api';

export interface AppNotification {
  id: number;
  title: string;
  message: string;
  type: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export const getMyNotifications = (): Promise<AppNotification[]> => get('/notifications') as Promise<AppNotification[]>;
export const getUnreadCount = (): Promise<{ count: number }> => get('/notifications/unread-count') as Promise<{ count: number }>;
export const markNotificationRead = (id: number) => post(`/notifications/${id}/read`);
export const markAllNotificationsRead = () => post('/notifications/read-all');
