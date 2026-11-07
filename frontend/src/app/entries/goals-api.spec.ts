import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { GoalsApi } from './goals-api';

describe('GoalsApi', () => {
  let api: GoalsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(GoalsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista e grava a meta de um hábito pelo código', async () => {
    const list = firstValueFrom(api.habitGoals());
    http.expectOne('/api/v1/goals/habits').flush([]);
    expect(await list).toEqual([]);

    const saved = firstValueFrom(api.saveHabitGoal('HYDRATION', { kind: 'DAILY_QUANTITY', targetQuantity: 2, unit: 'L' }));
    const req = http.expectOne('/api/v1/goals/habits/HYDRATION');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ kind: 'DAILY_QUANTITY', targetQuantity: 2, unit: 'L' });
    req.flush({ habit: 'HYDRATION' });
    expect((await saved).habit).toBe('HYDRATION');

    const removed = firstValueFrom(api.removeHabitGoal('GYM'));
    const del = http.expectOne('/api/v1/goals/habits/GYM');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    await removed;
  });

  it('orçamentos por categoria e preferências', async () => {
    const saved = firstValueFrom(api.saveBudget('FOOD', { period: 'WEEKLY', limit: 100 }));
    const req = http.expectOne('/api/v1/budgets/FOOD');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ period: 'WEEKLY', limit: 100 });
    req.flush({ category: 'FOOD' });
    await saved;

    const prefs = firstValueFrom(api.savePreferences({ firstDayOfWeek: 'SUNDAY' }));
    const put = http.expectOne('/api/v1/preferences');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ firstDayOfWeek: 'SUNDAY' });
    put.flush({ firstDayOfWeek: 'SUNDAY' });
    expect((await prefs).firstDayOfWeek).toBe('SUNDAY');
  });
});
