import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { IonContent, IonFooter, IonHeader, IonIcon, IonModal, IonSpinner, IonToolbar } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { cloudOfflineOutline } from 'ionicons/icons';
import { firstValueFrom, forkJoin, Observable } from 'rxjs';
import { CATEGORY_LABELS } from '../entries/entry-format';
import {
  BudgetRequest,
  BudgetResponse,
  DayOfWeekName,
  HabitGoalRequest,
  HabitGoalResponse,
} from '../entries/goal.models';
import { GoalsApi } from '../entries/goals-api';
import { AppNav } from '../nav/app-nav';
import { BudgetSheet } from './budget-sheet';
import {
  budgetStatusLabel,
  budgetSummary,
  DAY_OF_WEEK_OPTIONS,
  goalSummary,
  moneyProgressLabel,
  progressValue,
  progressWidth,
} from './goal-format';
import { HabitGoalSheet } from './habit-goal-sheet';

type LoadState = 'loading' | 'loaded' | 'error';

/**
 * Aba "Metas" (docs/design.md): as metas dos hábitos do catálogo, os limites de gasto por categoria e
 * o dia em que a semana começa. A edição abre num painel; ao fechar, o foco volta ao botão que abriu.
 */
@Component({
  selector: 'app-goals',
  imports: [AppNav, BudgetSheet, HabitGoalSheet, IonContent, IonFooter, IonHeader, IonIcon, IonModal, IonSpinner, IonToolbar],
  templateUrl: './goals.page.html',
  styleUrl: './goals.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPage implements OnInit {
  private readonly api = inject(GoalsApi);

  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly dayOptions = DAY_OF_WEEK_OPTIONS;
  protected readonly goalSummary = goalSummary;
  protected readonly budgetSummary = budgetSummary;
  protected readonly budgetStatusLabel = budgetStatusLabel;
  protected readonly moneyProgressLabel = moneyProgressLabel;
  protected readonly progressWidth = progressWidth;
  protected readonly progressValue = progressValue;

  protected readonly loadState = signal<LoadState>('loading');
  protected readonly habits = signal<HabitGoalResponse[]>([]);
  protected readonly budgets = signal<BudgetResponse[]>([]);
  protected readonly firstDay = signal<DayOfWeekName | null>(null);

  /** Hábitos bons primeiro, depois os ruins, cada grupo na ordem da API. */
  protected readonly goodHabits = computed(() => this.habits().filter((habit) => habit.polarity === 'GOOD'));
  protected readonly badHabits = computed(() => this.habits().filter((habit) => habit.polarity === 'BAD'));
  protected readonly hasGoals = computed(() => this.habits().some((habit) => habit.goal !== null));
  protected readonly hasBudgets = computed(() => this.budgets().some((budget) => budget.limit !== null));

  protected readonly editingHabit = signal<HabitGoalResponse | null>(null);
  protected readonly editingBudget = signal<BudgetResponse | null>(null);
  protected readonly sheetError = signal<string | null>(null);
  protected readonly saving = signal(false);
  /** Aviso curto anunciado ao leitor de tela depois de salvar ("Meta de Hidratação salva."). */
  protected readonly notice = signal('');

  private readonly habitSheet = viewChild(HabitGoalSheet);
  private readonly budgetSheet = viewChild(BudgetSheet);
  /** O botão que abriu o painel, para o foco voltar a ele ao fechar. */
  private opener: HTMLElement | null = null;
  private entered = false;
  /** O último dia que o servidor confirmou (na carga, numa gravação ou numa releitura). */
  private confirmedFirstDay: DayOfWeekName | null = null;
  /** O último dia escolhido que ainda não foi enviado; só ele importa. */
  private pendingFirstDay: DayOfWeekName | null = null;
  /** Há um ciclo de gravação do início da semana em andamento. */
  private savingFirstDay = false;

  constructor() {
    addIcons({ cloudOfflineOutline });
  }

  ngOnInit(): void {
    void this.load();
  }

  /** Ao voltar para a aba, recarrega: o gasto do período muda a cada lançamento. */
  ionViewWillEnter(): void {
    if (this.entered) {
      void this.load();
    }
    this.entered = true;
  }

  protected async load(): Promise<void> {
    this.loadState.set('loading');
    try {
      const { habits, budgets, preferences } = await firstValueFrom(
        forkJoin({ habits: this.api.habitGoals(), budgets: this.api.budgets(), preferences: this.api.preferences() }),
      );
      this.habits.set(habits);
      this.budgets.set(budgets);
      this.firstDay.set(preferences.firstDayOfWeek);
      this.confirmedFirstDay = preferences.firstDayOfWeek;
      this.loadState.set('loaded');
    } catch {
      this.loadState.set('error');
    }
  }

  protected habitAction(habit: HabitGoalResponse): string {
    const noun = habit.polarity === 'BAD' ? 'limite' : 'meta';
    return `${habit.goal ? 'Editar' : 'Definir'} ${noun} de ${habit.name}`;
  }

  protected budgetAction(budget: BudgetResponse): string {
    return `${budget.limit !== null ? 'Editar' : 'Definir'} limite de ${CATEGORY_LABELS[budget.category]}`;
  }

  protected openHabit(habit: HabitGoalResponse, event: Event): void {
    this.opener = event.currentTarget as HTMLElement | null;
    this.sheetError.set(null);
    this.editingHabit.set(habit);
  }

  protected openBudget(budget: BudgetResponse, event: Event): void {
    this.opener = event.currentTarget as HTMLElement | null;
    this.sheetError.set(null);
    this.editingBudget.set(budget);
  }

  /** O Ionic pode mover o foco ao apresentar o painel; o primeiro campo recebe o foco de novo. */
  protected onPresented(): void {
    this.habitSheet()?.focusFirst();
    this.budgetSheet()?.focusFirst();
  }

  /** Fecha o painel e devolve o foco ao botão que o abriu. */
  protected closeSheet(): void {
    if (this.editingHabit() === null && this.editingBudget() === null) {
      return;
    }
    this.editingHabit.set(null);
    this.editingBudget.set(null);
    this.sheetError.set(null);
    const opener = this.opener;
    this.opener = null;
    opener?.focus();
  }

  protected async saveHabit(request: HabitGoalRequest): Promise<void> {
    const habit = this.editingHabit();
    if (!habit) {
      return;
    }
    const result = await this.run(this.api.saveHabitGoal(habit.habit, request));
    if (result) {
      const saved = result.value;
      this.habits.update((list) => list.map((item) => (item.habit === saved.habit ? saved : item)));
      this.finish(habit.polarity === 'BAD' ? `Limite de ${habit.name} salvo.` : `Meta de ${habit.name} salva.`);
    }
  }

  protected async removeHabit(): Promise<void> {
    const habit = this.editingHabit();
    if (!habit) {
      return;
    }
    if (await this.run(this.api.removeHabitGoal(habit.habit))) {
      this.habits.update((list) => list.map((item) => (item.habit === habit.habit ? { ...item, goal: null } : item)));
      this.finish(`${habit.name} ficou sem ${habit.polarity === 'BAD' ? 'limite' : 'meta'}.`);
    }
  }

  protected async saveBudget(request: BudgetRequest): Promise<void> {
    const budget = this.editingBudget();
    if (!budget) {
      return;
    }
    const result = await this.run(this.api.saveBudget(budget.category, request));
    if (result) {
      const saved = result.value;
      this.budgets.update((list) => list.map((item) => (item.category === saved.category ? saved : item)));
      this.finish(`Limite de ${CATEGORY_LABELS[budget.category]} salvo.`);
    }
  }

  protected async removeBudget(): Promise<void> {
    const budget = this.editingBudget();
    if (!budget) {
      return;
    }
    if (await this.run(this.api.removeBudget(budget.category))) {
      this.budgets.update((list) =>
        list.map((item) =>
          item.category === budget.category
            ? { category: item.category, period: null, limit: null, validFrom: null, progress: null }
            : item,
        ),
      );
      this.finish(`${CATEGORY_LABELS[budget.category]} ficou sem limite.`);
    }
  }

  /**
   * O seletor do início da semana. Navegar no {@code select} com as setas dispara uma mudança por passo,
   * então as gravações vão uma de cada vez: com um {@code PUT} em voo, só o último dia escolhido fica
   * guardado e é enviado quando ele termina. Assim o servidor aplica as mudanças na ordem em que foram
   * escolhidas e a tela só mostra o que ele confirmou.
   */
  protected changeFirstDay(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as DayOfWeekName;
    if (value === this.firstDay()) {
      return;
    }
    this.firstDay.set(value);
    this.pendingFirstDay = value;
    if (!this.savingFirstDay) {
      void this.saveFirstDay();
    }
  }

  /**
   * Envia o dia pendente até não sobrar nenhum diferente do confirmado. Uma falha com outro dia pendente
   * não importa, porque o pendente vai em seguida. No fim, o aviso e os orçamentos refletem o último dia
   * gravado; se a última gravação falhou, a tela relê o dia que o servidor tem.
   */
  private async saveFirstDay(): Promise<void> {
    this.savingFirstDay = true;
    try {
      do {
        let failed = false;
        while (this.pendingFirstDay !== null) {
          const value = this.pendingFirstDay;
          this.pendingFirstDay = null;
          if (value === this.confirmedFirstDay) {
            failed = false;
            continue;
          }
          try {
            this.confirmedFirstDay = (await firstValueFrom(this.api.savePreferences({ firstDayOfWeek: value })))
              .firstDayOfWeek;
            failed = false;
          } catch {
            failed = true;
          }
        }
        await this.settleFirstDay(failed);
        // Uma mudança durante o fechamento (releitura ou orçamentos) começa outra rodada.
      } while (this.pendingFirstDay !== null);
    } finally {
      this.savingFirstDay = false;
    }
  }

  /** Fecha uma rodada de gravações: aviso e orçamentos, ou a releitura do servidor depois de uma falha. */
  private async settleFirstDay(failed: boolean): Promise<void> {
    if (failed) {
      try {
        this.confirmedFirstDay = (await firstValueFrom(this.api.preferences())).firstDayOfWeek;
      } catch {
        // Sem resposta do servidor, o seletor volta ao último dia que ele confirmou.
      }
      if (this.pendingFirstDay === null) {
        this.firstDay.set(this.confirmedFirstDay);
        this.notice.set('Não foi possível salvar o início da semana. Tente de novo.');
      }
      return;
    }
    const saved = this.confirmedFirstDay;
    this.firstDay.set(saved);
    const label = DAY_OF_WEEK_OPTIONS.find((option) => option.value === saved)?.label ?? '';
    this.notice.set(`A semana agora começa ${label === 'Sábado' || label === 'Domingo' ? 'no' : 'na'} ${label.toLowerCase()}.`);
    // Os orçamentos semanais mudam de período junto com a semana; uma falha aqui não desfaz a gravação.
    try {
      const budgets = await firstValueFrom(this.api.budgets());
      if (this.pendingFirstDay === null) {
        this.budgets.set(budgets);
      }
    } catch {
      // Os orçamentos ficam como estavam até a próxima carga da aba.
    }
  }

  /**
   * Envia uma mudança do painel. Um 400 mostra a mensagem da API dentro do painel; outra falha mostra
   * um aviso genérico. Devolve {@code null} quando falhou.
   */
  private async run<T>(request: Observable<T>): Promise<{ value: T } | null> {
    this.saving.set(true);
    this.sheetError.set(null);
    try {
      // O DELETE responde 204 sem corpo; o valor só importa no PUT.
      return { value: await firstValueFrom(request, { defaultValue: undefined as T }) };
    } catch (error) {
      const detail = error instanceof HttpErrorResponse && error.status === 400 ? error.error?.detail : null;
      this.sheetError.set(typeof detail === 'string' ? detail : 'Não foi possível salvar. Tente de novo.');
      return null;
    } finally {
      this.saving.set(false);
    }
  }

  private finish(message: string): void {
    this.notice.set(message);
    this.closeSheet();
  }
}
