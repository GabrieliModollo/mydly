package com.mydly.integrations.internal.openfinance;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do agregador de Open Finance, o Pluggy, só no sandbox (ADR 0015).
 *
 * @param clientId     o {@code CLIENT_ID} do time no Pluggy; vazio desliga o Open Finance
 * @param clientSecret o {@code CLIENT_SECRET}; fica só no servidor
 * @param baseUrl      a API do Pluggy
 * @param timeout      quanto cada chamada ao Pluggy espera
 * @param historyDays  quantos dias para trás a primeira sincronização busca
 */
@ConfigurationProperties("mydly.open-finance")
public record OpenFinanceProperties(String clientId, String clientSecret,
                                    @DefaultValue("https://api.pluggy.ai") String baseUrl,
                                    @DefaultValue("PT20S") Duration timeout,
                                    @DefaultValue("90") int historyDays) {

    public boolean enabled() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
        // As chaves não aparecem em log nem em mensagem de erro.
        return "OpenFinanceProperties[baseUrl=" + baseUrl + ", enabled=" + enabled() + "]";
    }
}
