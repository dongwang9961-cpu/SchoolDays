package com.schooldays.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.AnnotationMetadata;

class JooqRepositoryRegistrarTests {

    @Test
    void registersAnnotatedRepositoryInterfaces() {
        JooqRepositoryRegistrar registrar = new JooqRepositoryRegistrar();
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registrar.setEnvironment(new StandardEnvironment());
        registrar.setResourceLoader(new DefaultResourceLoader());

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(TestRepositoryConfig.class), registry);

        assertThat(registry.containsBeanDefinition("userRepository")).isTrue();
        assertThat(registry.containsBeanDefinition("roleRepository")).isTrue();
        assertThat(registry.containsBeanDefinition("registrationLinkRepository")).isTrue();
    }

    @Configuration
    @EnableJooqRepositories(basePackages = "com.schooldays.dao.auth")
    static class TestRepositoryConfig {
    }
}
