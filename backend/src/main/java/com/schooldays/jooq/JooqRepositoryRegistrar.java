package com.schooldays.jooq;

import java.util.Arrays;
import java.util.Map;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class JooqRepositoryRegistrar
        implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {

    private Environment environment;
    private ResourceLoader resourceLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(EnableJooqRepositories.class.getName());
        String[] basePackages = attributes == null ? new String[0] : (String[]) attributes.get("basePackages");
        if (basePackages.length == 0) {
            basePackages = new String[]{ClassUtils.getPackageName(metadata.getClassName())};
        }

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        AnnotationMetadata metadata = beanDefinition.getMetadata();
                        return metadata.isIndependent() && metadata.isInterface();
                    }
                };
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(JooqRepositoryBean.class));

        Arrays.stream(basePackages)
                .filter(StringUtils::hasText)
                .flatMap(basePackage -> scanner.findCandidateComponents(basePackage).stream())
                .forEach(candidate -> registerRepository(candidate, registry));
    }

    private void registerRepository(BeanDefinition candidate, BeanDefinitionRegistry registry) {
        try {
            Class<?> repoClass = Class.forName(candidate.getBeanClassName());
            JooqRepositoryBean annotation = repoClass.getAnnotation(JooqRepositoryBean.class);
            Table<? extends Record> table = table(annotation);
            Field<?> idField = table.field(annotation.idField());
            if (idField == null) {
                idField = DSL.field(DSL.name(annotation.idField()));
            }

            BeanDefinitionBuilder builder =
                    BeanDefinitionBuilder.genericBeanDefinition(JooqRepositoryFactoryBean.class);
            builder.addConstructorArgValue(repoClass);
            builder.addConstructorArgValue(table);
            builder.addConstructorArgValue(idField);

            registry.registerBeanDefinition(beanName(repoClass), builder.getBeanDefinition());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to register jOOQ repository", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Table<? extends Record> table(JooqRepositoryBean annotation) {
        if (!Table.class.equals(annotation.tableClass())) {
            try {
                return (Table<? extends Record>) annotation.tableClass().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to instantiate jOOQ table class: "
                        + annotation.tableClass().getName(), e);
            }
        }
        if (!StringUtils.hasText(annotation.table())) {
            throw new IllegalStateException("@JooqRepositoryBean requires either table or tableClass");
        }
        return DSL.table(DSL.name(annotation.table()));
    }

    private String beanName(Class<?> repoClass) {
        String simpleName = repoClass.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
