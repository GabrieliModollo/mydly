package com.mydly.integrations;

import com.mydly.finance.TransactionOrigin;
import java.util.List;

/**
 * Porta das fontes externas de transação (ADR 0005). Cada adaptador lê a sua fonte e devolve as
 * transações num formato só; a importação, a reconciliação com os lançamentos manuais e a gravação
 * nos números são as mesmas para todas (ADR 0014).
 *
 * <p>Adaptadores: o extrato OFX ou CSV ({@code StatementImportSource}) e, numa próxima etapa, o
 * agregador de Open Finance no sandbox.
 */
public interface TransactionSource {

    /** A origem gravada em cada transação. */
    TransactionOrigin origin();

    /**
     * As transações da fonte. Cada uma tem um id externo estável: ler a mesma transação de novo
     * devolve o mesmo id, e é isso que impede a duplicação.
     *
     * @throws InvalidSourceException quando a fonte não pode ser lida, com uma mensagem para o usuário
     */
    List<ExternalTransaction> fetch();
}
