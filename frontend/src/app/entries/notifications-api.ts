import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type { Tone } from './week.models';

/** O que o usuário quer receber (ADR 0016). */
export interface NotificationSettings {
  budgetAlerts: boolean;
  dailyReminder: boolean;
  weeklySummary: boolean;
  tone: Tone;
}

export type DevicePlatform = 'ANDROID' | 'IOS';

/** Acesso HTTP às notificações push. */
@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  registerDevice(token: string, platform: DevicePlatform): Observable<void> {
    return this.http.put<void>('/api/v1/notifications/devices', { token, platform });
  }

  unregisterDevice(token: string): Observable<void> {
    return this.http.delete<void>('/api/v1/notifications/devices', { params: new HttpParams().set('token', token) });
  }

  settings(): Observable<NotificationSettings> {
    return this.http.get<NotificationSettings>('/api/v1/notifications/settings');
  }

  saveSettings(settings: NotificationSettings): Observable<NotificationSettings> {
    return this.http.put<NotificationSettings>('/api/v1/notifications/settings', settings);
  }
}
