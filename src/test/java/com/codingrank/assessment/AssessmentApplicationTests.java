package com.codingrank.assessment;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AssessmentApplicationTests {

    @TestConfiguration
    static class TestMongoConfig {
        @Bean
        @Primary
        public MongoClient mongoClient() {
            return mock(MongoClient.class);
        }

        @Bean
        @Primary
        public MongoDatabaseFactory mongoDatabaseFactory() {
            MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
            when(factory.getExceptionTranslator()).thenReturn(mock(PersistenceExceptionTranslator.class));
            return factory;
        }

        @Bean(name = "mongoTemplate")
        @Primary
        public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
            MongoCustomConversions conversions = new MongoCustomConversions(Collections.emptyList());
            MongoMappingContext mappingContext = new MongoMappingContext();
            mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
            mappingContext.afterPropertiesSet();

            MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory), mappingContext);
            converter.setCustomConversions(conversions);
            converter.afterPropertiesSet();

            MongoTemplate template = mock(MongoTemplate.class);
            when(template.getConverter()).thenReturn(converter);
            return template;
        }
    }

    @Test
    void contextLoads() {
    }
}
