package com.pedrocf01.backend.entity;

import java.math.BigDecimal;
import java.util.List;

public record TransacaoReport(
        String nomeDaLoja,
        BigDecimal total,
        List<Transacao> transacoes) {

    public TransacaoReport addTotal(BigDecimal valor) {
        return new TransacaoReport(nomeDaLoja, total.add(valor), transacoes);
    }

    public TransacaoReport addTransacao(Transacao transacao){
        transacoes.add(transacao);
        return new TransacaoReport(nomeDaLoja, total, transacoes);
    }

}
