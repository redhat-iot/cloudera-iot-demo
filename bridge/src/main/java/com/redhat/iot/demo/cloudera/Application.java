/**
 *  Copyright 2005-2017 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package com.redhat.iot.demo.cloudera;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.mqtt.MQTTConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ImportResource({"classpath:spring/camel-context.xml"})
public class Application extends RouteBuilder {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configure() throws Exception {

        // Incoming from MQTT
        from("mqtt:incoming?host=tcp://ec-broker-mqtt.redhat-iot.svc:1883&subscribeTopicName=Red-Hat/+/cloudera-demo/facilities/+/lines/+/machines/+&userName=demogw&password=RedHat123!@#")
                .process(new Processor() {
                @Override
                public void process(Exchange exchange)
                        throws Exception {
                    if (exchange.getIn() != null) {
                        Message message = exchange.getIn();
                        message.setHeader(KafkaConstants.KEY, message.getHeader(MQTTConfiguration.MQTT_SUBSCRIBE_TOPIC));
                    }
                }
            })
        .to("kafka:ingest?brokers=34.208.144.34:9092&serializerClass=org.apache.kafka.common.serialization.ByteArraySerializer");

        // Dev Kit Gateways (PLC via Modbus and Eurotech Sensor Panel)
        from("mqtt:incoming?host=tcp://ec-broker-mqtt.redhat-iot.svc:1883&subscribeTopicName=Red-Hat/+/W1/A1/#&userName=demogw&password=RedHat123!@#")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange)
                            throws Exception {
                        if (exchange.getIn() != null) {
                            Message message = exchange.getIn();
                            message.setHeader(KafkaConstants.KEY, message.getHeader(MQTTConfiguration.MQTT_SUBSCRIBE_TOPIC));
                        }
                    }
                })
        .to("kafka:telemetry?brokers=34.212.173.140:9092&serializerClass=org.apache.kafka.common.serialization.ByteArraySerializer");

        // Monitoring Kafka topic, for testing only
        from("kafka:model?brokers=34.212.173.140:9092&groupId=kapua_test")
        .to("log:model")
                .process(exchange -> {
                    System.out.println("FIRST" + "\n");

                    String messageKey = "";
                    if (exchange.getIn() != null) {
                        Message message = exchange.getIn();
                        Integer partitionId = (Integer) message
                                .getHeader(KafkaConstants.PARTITION);
                        String topicName = (String) message
                                .getHeader(KafkaConstants.TOPIC);
                        if (message.getHeader(KafkaConstants.KEY) != null)
                            messageKey = (String) message
                                    .getHeader(KafkaConstants.KEY);
                        Object data = message.getBody();

                        System.out.println("topicName :: "
                                + topicName + " partitionId :: "
                                + partitionId + " messageKey :: "
                                + messageKey + " message :: "
                                + data + "\n");
                    }
        // Need to transfer this to MQTT so that gateways can pick it up without Kafka client
        }).to("log:model");
    }
}
