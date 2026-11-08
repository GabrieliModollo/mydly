package com.mydly.entries.internal;

/** Dá aos testes de outros pacotes o tamanho da semana de exemplo da demonstração (ADR 0017). */
public final class DemoEntriesAccess {

    private DemoEntriesAccess() {
    }

    /** Quantos lançamentos o reset da demonstração grava. */
    public static long size() {
        return DemoEntries.week(DemoEntries.DAYS).size();
    }
}
