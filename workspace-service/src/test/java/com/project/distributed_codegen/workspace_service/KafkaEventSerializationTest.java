package com.project.distributed_codegen.workspace_service;

import com.project.distributed_codegen.common_lib.event.FileStoreRequestEvent;
import com.project.distributed_codegen.common_lib.event.FileStoreResponseEvent;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.core.io.ClassPathResource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventSerializationTest {
    @Test
    @SuppressWarnings("unchecked")
    void runtimeKafkaConfigurationRoundTripsBothStorageEvents() throws Exception {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        KafkaProperties kafka = new Binder(new MapConfigurationPropertySource(yaml.getObject()))
                .bind("spring.kafka", Bindable.of(KafkaProperties.class)).get();
        Serializer<Object> serializer = (Serializer<Object>) kafka.getProducer().getValueSerializer()
                .getDeclaredConstructor().newInstance();
        Deserializer<Object> deserializer = (Deserializer<Object>) kafka.getConsumer().getValueDeserializer()
                .getDeclaredConstructor().newInstance();
        serializer.configure(kafka.getProducer().getProperties(), false);
        Map<String, Object> consumerConfig = new HashMap<>(kafka.getConsumer().getProperties());
        deserializer.configure(consumerConfig, false);

        try (serializer; deserializer) {
            for (Object event : new Object[]{
                    new FileStoreRequestEvent(1L, "test-saga", "src/App.tsx", "export default 1;", 2L),
                    new FileStoreResponseEvent("test-saga", true, null, 1L)}) {
                RecordHeaders headers = new RecordHeaders();
                byte[] value = serializer.serialize("test-storage", headers, event);
                assertThat(deserializer.deserialize("test-storage", headers, value)).isEqualTo(event);
            }
        }
        assertThat(kafka.getConsumer().getAutoOffsetReset()).isEqualTo("earliest");
        assertThat(kafka.getConsumer().getEnableAutoCommit()).isFalse();
    }
}
