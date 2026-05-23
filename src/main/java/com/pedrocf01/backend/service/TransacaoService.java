package com.pedrocf01.backend.service;

import com.pedrocf01.backend.entity.TipoTransacao;
import com.pedrocf01.backend.entity.TransacaoReport;
import com.pedrocf01.backend.repository.TransacaoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class TransacaoService {
    private final TransacaoRepository transacaoRepository;

    public TransacaoService(TransacaoRepository transacaoRepository) {
        this.transacaoRepository = transacaoRepository;
    }

    public List<TransacaoReport> listTotaisTransacoesPorNomeDaLoja() {
        var transacoes = transacaoRepository.findAllByOrderByNomeDaLojaAscIdDesc();

        var reportMap = new LinkedHashMap<String, TransacaoReport>();

        transacoes.forEach(transacao -> {
           String nomeDaLoja = transacao.nomeDaLoja();
           BigDecimal valor = transacao.valor();

           reportMap.compute(nomeDaLoja, (key, existingReport) -> {
               var report = (existingReport != null) ? existingReport :
                            new TransacaoReport(key, BigDecimal.ZERO, new ArrayList<>());

               return report.addTotal(valor).addTransacao(transacao);
           });
        });

        return new ArrayList<>(reportMap.values());
    }
}
