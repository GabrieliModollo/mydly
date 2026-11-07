package com.mydly.integrations.internal.statement;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Utilidades comuns aos extratos: texto do arquivo, valores no formato brasileiro e ids estáveis. */
final class StatementText {

    /** Bancos brasileiros ainda exportam em Windows-1252 (o {@code CHARSET:1252} do OFX). */
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private StatementText() {
    }

    /** O texto do arquivo: UTF-8 quando é válido, Windows-1252 quando não é. Sem a marca de BOM. */
    static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            text = new String(bytes, WINDOWS_1252);
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    /**
     * Um valor com sinal em formato brasileiro ({@code -1.234,56}, {@code 50,00}) ou com ponto decimal
     * ({@code -1234.56}), com ou sem {@code R$}. Vazio quando não é um número.
     */
    static Optional<BigDecimal> amount(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.replace("R$", "").replace(" ", "").replace(" ", "").strip();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            // O separador que vem por último é o decimal; o outro separa os milhares.
            value = comma > dot ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
        } else if (comma >= 0) {
            value = value.replace(',', '.');
        }
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Um id estável a partir de partes do conteúdo: o mesmo arquivo gera sempre os mesmos ids. */
    static String hash(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.join("\u001F", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
