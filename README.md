# Processador de Arquivos CNAB

Fiz esse projeto como uma introdução ao processamento em lote usando Spring Batch e para praticar a divisão de backend e frontend com diferentes stacks. 

Esta aplicação é uma API REST para importação e processamento de arquivos no formato CNAB (padrão bancário brasileiro). O sistema recebe um arquivo de texto via upload, processa cada linha de forma assíncrona usando Spring Batch e persiste as transações no banco de dados. Um segundo endpoint retorna um relatório com o saldo total por loja.

## Funcionalidades

- **Upload de arquivo CNAB** — recebe o arquivo via `multipart/form-data` e dispara o processamento em segundo plano
- **Processamento assíncrono em lote** — Spring Batch lê, transforma e persiste as transações em chunks de 1000 registros por vez
- **Parsing do formato posicional CNAB** — cada linha é lida com posições fixas e mapeada para os campos da transação
- **Normalização de valores** — o valor é dividido por 100 (centavos → reais) e o sinal é aplicado conforme o tipo da transação (débito/crédito)
- **9 tipos de transação suportados** — Débito, Boleto, Financiamento, Crédito, Recebimento de Empréstimo, Vendas, Recebimento TED, Recebimento DOC e Aluguel
- **Proteção contra reimportação** — retorna HTTP 409 se o mesmo arquivo já tiver sido processado
- **Relatório por loja** — endpoint que retorna o saldo total e a lista de transações agrupados por nome da loja
- **Testes unitários** — cobertura do `TransacaoService` com Mockito

## Tecnologias

- Java 21  
- Spring Boot  
- Spring Batch  
- Spring Data JDBC  
- PostgreSQL 17  
- JUnit 5 + Mockito  
- Maven  
- Docker



## Como Executar


**Pré-requisitos:** Docker e Docker Compose.

O `docker-compose.yml` sobe três serviços juntos: o banco PostgreSQL, o backend Spring Boot e o frontend.

1. Clone o repositório:
   ```bash
   git clone https://github.com/pedrocf01/processamento-cnab-backend.git
   cd backend-cnab
   ```

2. Construa a imagem do backend:
   ```bash
   docker build -t backend-cnab:latest .
   ```

3. Suba todos os serviços:
   ```bash
   docker compose up
   ```

Após a inicialização:

| Serviço | URL |
|---|---|
| API (Spring Boot) | `http://localhost:8080` |
| Frontend | `http://localhost:9090` |
| PostgreSQL | `localhost:5432` (banco: `cnabdb`, usuário: `pedro`) |

O schema da tabela `transacao` é criado automaticamente pelo `schema.sql` na inicialização do Spring Boot.

> **Nota:** o `docker-compose.yml` inclui o frontend como terceiro serviço (`spa-app`), cujo build é disparado a partir do diretório `../processamento-cnab-frontend`(disponível em <https://github.com/pedrocf01/processamento-cnab-frontend>. Certifique-se de que o repositório do frontend está presente nesse caminho antes de executar o compose.



### Sobre o Dockerfile

O build utiliza **multi-stage** para minimizar o tamanho da imagem final:

- **Estágio de build:** `maven:3.9-eclipse-temurin-21` compila o projeto com `mvn clean package -DskipTests` e gera o `.jar`.
- **Estágio de runtime:** `eclipse-temurin:21-jre-alpine` copia apenas o `.jar` e um diretório `/app/tmp` (usado para salvar os arquivos CNAB em upload), mantendo a imagem leve.

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build   # compila
FROM eclipse-temurin:21-jre-alpine            # executa 
```

## Endpoints

### `POST /cnab/upload`

Recebe um arquivo CNAB e inicia o processamento assíncrono.

**Requisição:**
```
Content-Type: multipart/form-data
Parâmetro: file (arquivo .txt no formato CNAB)
```

**Resposta de sucesso (200):**
```
Processamento iniciado!
```

**Resposta de conflito (409):** quando o mesmo arquivo já foi importado anteriormente:
```
O arquivo informado já foi importado no sistema!
```

**Exemplo com curl:**
```bash
curl -X POST http://localhost:8080/cnab/upload \
  -F "file=@files/CNAB.txt"
```

---

### `GET /transacoes`

Retorna o relatório de transações agrupado por nome da loja, com o saldo total e a lista detalhada de cada transação.

**Resposta (200):**
```json
[
  {
    "nomeDaLoja": "BAR DO JOÃO",
    "total": 102.00,
    "transacoes": [
      {
        "id": 1,
        "tipo": 3,
        "data": "2019-03-01",
        "valor": 142.00,
        "cpf": 9620676017,
        "cartao": "4753****3153",
        "hora": "15:34:53",
        "donoDaLoja": "JOÃO MACEDO",
        "nomeDaLoja": "BAR DO JOÃO"
      }
    ]
  }
]
```

## Formato CNAB

Cada linha do arquivo segue o layout posicional abaixo:

| Posição | Tamanho | Campo | Descrição |
|---|---|---|---|
| 1 | 1 | `tipo` | Tipo da transação (1–9) |
| 2–9 | 8 | `data` | Data no formato `YYYYMMDD` |
| 10–19 | 10 | `valor` | Valor em centavos |
| 20–30 | 11 | `cpf` | CPF do titular do cartão |
| 31–42 | 12 | `cartao` | Número do cartão (mascarado) |
| 43–48 | 6 | `hora` | Hora no formato `HHmmss` |
| 49–62 | 14 | `donoDaLoja` | Nome do dono da loja |
| 63–80 | 18 | `nomeDaLoja` | Nome da loja |

**Exemplo de linha:**
```
3201903010000014200096206760174753****3153153453JOÃO MACEDO   BAR DO JOÃO
```

## Tipos de Transação

| Código | Tipo | Natureza |
|---|---|---|
| 1 | Débito | Entrada (+) |
| 2 | Boleto | Saída (−) |
| 3 | Financiamento | Saída (−) |
| 4 | Crédito | Entrada (+) |
| 5 | Recebimento Empréstimo | Entrada (+) |
| 6 | Vendas | Entrada (+) |
| 7 | Recebimento TED | Entrada (+) |
| 8 | Recebimento DOC | Entrada (+) |
| 9 | Aluguel | Saída (−) |

## Como o Processamento Funciona

1. O arquivo é recebido via `POST /cnab/upload` e salvo em disco pelo `CnabService`.
2. Um job do Spring Batch é disparado de forma **assíncrona** via `TaskExecutorJobOperator`.
3. O `FlatFileItemReader` lê o arquivo linha a linha usando um tokenizador de largura fixa (`FixedLengthTokenizer`) e mapeia cada linha para um `TransacaoCnab`.
4. O `ItemProcessor` converte cada `TransacaoCnab` em `Transacao`: normaliza o valor (÷ 100), aplica o sinal conforme o tipo e processa data e hora.
5. O `JdbcBatchItemWriter` insere os registros no banco em chunks de 1000 itens por transação.
6. O Spring Batch garante idempotência: tentar importar o mesmo arquivo novamente lança `JobInstanceAlreadyCompleteException`, tratada pelo `ExceptionHandlerController` com HTTP 409.

## Testes

```bash
./mvnw test
```

O `TransacaoServiceTest` cobre a lógica de agrupamento e cálculo de totais por loja usando Mockito para mockar o repositório.

## Schema do Banco de Dados

```sql
CREATE TABLE IF NOT EXISTS transacao (
  id        SERIAL PRIMARY KEY,
  tipo      INT,
  data      DATE,
  valor     DECIMAL,
  cpf       BIGINT,
  cartao    VARCHAR(255),
  hora      TIME,
  dono_loja VARCHAR(255),
  nome_loja VARCHAR(255)
);
```
