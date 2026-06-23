package com.zmd.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zmd.order.common.Constants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置（mq.enabled=true 时生效）
 *
 * 配置要点（和 CRM 项目一样的模式）：
 * 1. Exchange + Queue + Binding
 * 2. 手动 ACK（保证消息不丢失）
 * 3. prefetch=100（提高吞吐）
 * 4. 并发消费者 5-20
 */
@Configuration
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true")
public class RabbitConfig {

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(Constants.MQ_EXCHANGE);
    }

    @Bean
    public Queue approvalQueue() {
        return QueueBuilder.durable(Constants.MQ_QUEUE_APPROVAL).build();
    }

    @Bean
    public Binding approvalBinding(Queue approvalQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(approvalQueue).to(orderExchange).with(Constants.MQ_ROUTING_KEY_APPROVAL);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(om);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(20);
        factory.setPrefetchCount(100);
        return factory;
    }
}
