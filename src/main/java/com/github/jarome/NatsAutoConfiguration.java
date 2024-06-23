package com.github.jarome;

import com.github.jarome.config.ConsumerProperties;
import com.github.jarome.config.NatsMessageConverter;
import com.github.jarome.config.NatsTemplate;
import io.nats.client.Connection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.io.IOException;

@Configuration
@ComponentScan
@EnableConfigurationProperties({ConsumerProperties.class})
@DependsOn({"natsConnection", "natsMessageConverter"})
public class NatsAutoConfiguration {

    private final Connection connection;
    private final NatsMessageConverter natsMessageConverter;

    public NatsAutoConfiguration(Connection connection, NatsMessageConverter natsMessageConverter) {
        this.connection = connection;
        this.natsMessageConverter = natsMessageConverter;
    }

    @Bean
    @ConditionalOnMissingBean
    public NatsTemplate natsTemplate() {
        try {
            return new NatsTemplate(connection.jetStream(), natsMessageConverter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
