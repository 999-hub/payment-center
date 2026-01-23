package com.wzy.paymentcenter.support;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    //把一类重复的固定流程（连接、序列化、发送、异常处理）变成可复用的模板，你只提供变化的参数

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload);
    }
}

