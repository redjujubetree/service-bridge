package top.redjujubetree.servicebridge.example;

import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleTopicConfigurationTest {

    @Test
    void routesBothExampleBusinessesToSharedTopic() throws Exception {
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load("example", new ClassPathResource("application.yml")).get(0);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(source);
        BridgeTopicProperties topics = Binder.get(environment)
                .bind("service.bridge", BridgeTopicProperties.class).get();

        assertThat(topics.requestTopicFor("GENERATE_REPORT"))
                .isEqualTo("service-bridge.request");
        assertThat(topics.requestTopicFor("VALIDATE_INVOICE"))
                .isEqualTo("service-bridge.request");
        assertThat(topics.requestTopicNames()).containsExactly("service-bridge.request");
    }
}
