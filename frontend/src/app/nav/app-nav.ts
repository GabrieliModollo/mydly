import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { barChartOutline, flagOutline, todayOutline } from 'ionicons/icons';

/**
 * Barra de navegação inferior (docs/design.md): Hoje, Resumo e Metas.
 */
@Component({
  selector: 'app-nav',
  imports: [IonIcon, RouterLink, RouterLinkActive],
  template: `
    <nav class="nav" aria-label="Navegação principal">
      <a
        class="nav__link"
        routerLink="/"
        routerLinkActive="nav__link--active"
        [routerLinkActiveOptions]="{ exact: true }"
        ariaCurrentWhenActive="page"
      >
        <ion-icon name="today-outline" aria-hidden="true" />
        Hoje
      </a>
      <a class="nav__link" routerLink="/resumo" routerLinkActive="nav__link--active" ariaCurrentWhenActive="page">
        <ion-icon name="bar-chart-outline" aria-hidden="true" />
        Resumo
      </a>
      <a class="nav__link" routerLink="/metas" routerLinkActive="nav__link--active" ariaCurrentWhenActive="page">
        <ion-icon name="flag-outline" aria-hidden="true" />
        Metas
      </a>
    </nav>
  `,
  styles: `
    .nav {
      display: flex;
      justify-content: center;
      gap: 8px;
      max-width: 40rem;
      margin: 0 auto;
      padding: 4px 20px calc(6px + var(--ion-safe-area-bottom, 0px));
    }

    .nav__link {
      display: inline-flex;
      flex: 1;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 2px;
      min-height: 48px;
      border-radius: 14px;
      color: var(--mydly-muted);
      font-size: 12px;
      font-weight: 600;
      text-decoration: none;

      ion-icon {
        font-size: 20px;
      }

      &:focus-visible {
        outline: 2px solid var(--mydly-focus);
        outline-offset: 2px;
      }
    }

    .nav__link--active {
      color: var(--mydly-ink);
      background: var(--mydly-surface);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppNav {
  constructor() {
    addIcons({ barChartOutline, flagOutline, todayOutline });
  }
}
