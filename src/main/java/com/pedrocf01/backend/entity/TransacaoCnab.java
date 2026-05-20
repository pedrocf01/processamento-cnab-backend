package com.pedrocf01.backend.entity;

import java.math.BigDecimal;

public record TransacaoCnab(
        Integer tipo,
        String data,
        BigDecimal valor,
        Long cpf,
        String cartao,
        String hora,
        String donoDaLoja,
        String nomeDaLoja
) {
}
