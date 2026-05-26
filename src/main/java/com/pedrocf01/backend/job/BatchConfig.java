package com.pedrocf01.backend.job;

import com.pedrocf01.backend.entity.TipoTransacao;
import com.pedrocf01.backend.entity.Transacao;
import com.pedrocf01.backend.entity.TransacaoCnab;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;

@Configuration
public class BatchConfig {
    private JobRepository jobRepository;

    public BatchConfig(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /*@Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }*/

    @Bean
    Job job(Step step) {
        return new JobBuilder("job", jobRepository)
                    .start(step).build();
    }

    @Bean
    Step step(ItemReader<TransacaoCnab> itemReader, ItemProcessor<TransacaoCnab, Transacao> itemProcessor,
              ItemWriter<Transacao> itemWriter, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step", jobRepository)
                                .<TransacaoCnab, Transacao>chunk(1000)
                                .reader(itemReader).processor(itemProcessor)
                                .writer(itemWriter)
                                .transactionManager(transactionManager)
                                .build();
    }

    @StepScope
    @Bean
    FlatFileItemReader<TransacaoCnab> reader(@Value("#{jobParameters['cnabFile']}") Resource resource) {
        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
        tokenizer.setColumns(
                new Range(1,1), new Range(2,9), new Range(10,19),
                new Range(20,30), new Range(31,42), new Range(43,48),
                new Range(49,62), new Range(63,80)
        );
        tokenizer.setNames("tipo", "data", "valor", "cpf", "cartao", "hora", "donoDaLoja", "nomeDaLoja");
        tokenizer.setStrict(false);

        return new FlatFileItemReaderBuilder<TransacaoCnab>().name("reader")
                    .resource(resource)
                    .lineTokenizer(tokenizer)
                    .targetType(TransacaoCnab.class)
                    .build();
    }

    @Bean
    ItemProcessor<TransacaoCnab, Transacao> processor() {
        return item -> {
            var tipoTransacao = TipoTransacao.findByTipo(item.tipo());
            var valorNormalizado = item.valor()
                                       .divide(new BigDecimal(100))
                                       .multiply(tipoTransacao.getSinal());

            return new Transacao(
                        null, item.tipo(), null, valorNormalizado,
                        item.cpf(), item.cartao(), null, item.donoDaLoja().trim(),
                        item.nomeDaLoja().trim()
                        )
                        .withData(item.data()).withHora(item.hora());
        };
    }

    @Bean
    JdbcBatchItemWriter<Transacao> writer(DataSource dataSource) {
        String sql = """
                        INSERT INTO transacao(
                          tipo, data, valor, cpf, cartao,
                          hora, dono_loja, nome_loja
                        ) VALUES (
                          :tipo, :data, :valor, :cpf, :cartao,
                          :hora, :donoDaLoja, :nomeDaLoja
                          )                                                                     
                        """;
        return new JdbcBatchItemWriterBuilder<Transacao>().dataSource(dataSource)
                                                          .sql(sql).beanMapped().build();
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    @Bean
    JobOperator jobOperatorAsync(JobRepository jobRepository) throws Exception {
        var jobOperator = new TaskExecutorJobOperator();
        jobOperator.setJobRepository(jobRepository);
        jobOperator.setTaskExecutor(new SimpleAsyncTaskExecutor());
        jobOperator.setJobRegistry(jobRegistry());
        jobOperator.afterPropertiesSet();
        return jobOperator;
    }
}
