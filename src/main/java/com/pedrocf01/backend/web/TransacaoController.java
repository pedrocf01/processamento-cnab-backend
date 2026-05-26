package com.pedrocf01.backend.web;

import com.pedrocf01.backend.entity.Transacao;
import com.pedrocf01.backend.entity.TransacaoReport;
import com.pedrocf01.backend.service.TransacaoService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("transacoes")
public class TransacaoController {
    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @GetMapping
    @CrossOrigin(origins = {"http://localhost:9090"})
    public List<TransacaoReport> listAll(){
        return transacaoService.listTotaisTransacoesPorNomeDaLoja();
    }
}
