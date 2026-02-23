package com.example.orderapi.config;

import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory;
import com.ibm.msg.client.jakarta.jms.JmsFactoryFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.util.StringUtils;

@Configuration
@EnableJms
@ConditionalOnExpression("'${receive.protocol}'.equals('ibmmq') or '${send.protocol}'.equals('ibmmq')")
public class IbmMqConfig {
    @Value("${ibmmq.queue-manager}")
    private String queueManager;

    @Value("${ibmmq.channel}")
    private String channel;

    @Value("${ibmmq.conn-name}")
    private String connectionName;

    @Value("${ibmmq.username:}")
    private String username;

    @Value("${ibmmq.password:}")
    private String password;

    @Value("${ibmmq.app-name:specmatic-async-sample}")
    private String appName;

    @Bean
    @Primary
    public ConnectionFactory ibmMqConnectionFactory() throws JMSException {
        JmsFactoryFactory factoryFactory = JmsFactoryFactory.getInstance(WMQConstants.JAKARTA_WMQ_PROVIDER);
        JmsConnectionFactory connectionFactory = factoryFactory.createConnectionFactory();

        connectionFactory.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
        connectionFactory.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, queueManager);
        connectionFactory.setStringProperty(WMQConstants.WMQ_CHANNEL, channel);
        connectionFactory.setStringProperty(WMQConstants.WMQ_CONNECTION_NAME_LIST, connectionName);
        connectionFactory.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, appName);
        connectionFactory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);

        if (StringUtils.hasText(username)) {
            UserCredentialsConnectionFactoryAdapter adapter = new UserCredentialsConnectionFactoryAdapter();
            adapter.setTargetConnectionFactory(connectionFactory);
            adapter.setUsername(username);
            adapter.setPassword(password);
            return adapter;
        }

        return connectionFactory;
    }
}
