package com.example.product;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Spring Batch has no first-class Mongo writer, so the writer below is a small custom one calling
 * MongoTemplate directly — the standard approach, not a missing dependency. Each imported product
 * also publishes PRODUCT_CREATED (same as the regular POST /api/products path) so inventory-service
 * auto-provisions a stock record for it exactly like any other product.
 */
@Configuration
public class ProductImportJobConfig {

    @Bean
    public FlatFileItemReader<ProductCsvRow> productCsvReader() {
        return new FlatFileItemReaderBuilder<ProductCsvRow>()
                .name("productCsvReader")
                .resource(new ClassPathResource("sample-products.csv"))
                .linesToSkip(1)
                .delimited(spec -> spec.names("name", "sku", "price"))
                .targetType(ProductCsvRow.class)
                .build();
    }

    @Bean
    public ItemProcessor<ProductCsvRow, Product> productCsvProcessor() {
        return row -> new Product(row.getSku(), row.getName(), new BigDecimal(row.getPrice()));
    }

    @Bean
    public ItemWriter<Product> productCsvWriter(MongoTemplate mongoTemplate, KafkaTemplate<String, Object> kafkaTemplate) {
        return (Chunk<? extends Product> products) -> {
            for (Product product : products) {
                Product saved = mongoTemplate.insert(product);
                kafkaTemplate.send(Topics.PRODUCT_EVENTS, DomainEvent.of(EventTypes.PRODUCT_CREATED, null, Map.of(
                        "productId", saved.getId(),
                        "name", saved.getName(),
                        "sku", saved.getSku()
                )));
            }
        };
    }

    @Bean
    public Step productImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   FlatFileItemReader<ProductCsvRow> reader, ItemProcessor<ProductCsvRow, Product> processor,
                                   ItemWriter<Product> writer) {
        return new StepBuilder("productImportStep", jobRepository)
                .<ProductCsvRow, Product>chunk(5, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job productImportJob(JobRepository jobRepository, Step productImportStep) {
        return new JobBuilder("productImportJob", jobRepository)
                .start(productImportStep)
                .build();
    }
}
