package com.schooldays.codegen;

import java.io.File;
import java.nio.file.Path;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;

public final class GenerateJooqClasses {

    private static final String DEFAULT_OUTPUT_DIRECTORY = "src/main/java";
    private static final String DEFAULT_PACKAGE_NAME = "com.schooldays.jooq.generated";

    private GenerateJooqClasses() {
    }

    public static void main(String[] args) throws Exception {
        String outputDirectory = args.length > 0 ? args[0] : DEFAULT_OUTPUT_DIRECTORY;
        String packageName = args.length > 1 ? args[1] : DEFAULT_PACKAGE_NAME;

        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setLocaleConfig("locale", "C")
                .start()) {
            migrate(postgres.getPostgresDatabase());
            generate(postgres.getJdbcUrl("postgres", "postgres"), outputDirectory, packageName);
        }

        System.out.printf(
                "Generated jOOQ classes in %s with package %s%n",
                Path.of(outputDirectory).toAbsolutePath(),
                packageName
        );
    }

    private static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static void generate(String jdbcUrl, String outputDirectory, String packageName) throws Exception {
        new File(outputDirectory).mkdirs();

        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(jdbcUrl)
                        .withUser("postgres")
                        .withPassword("postgres"))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withInputSchema("public")
                                .withExcludes("flyway_schema_history|pgp_armor_headers"))
                        .withGenerate(new Generate()
                                .withDaos(false)
                                .withPojos(false)
                                .withRecords(true)
                                .withFluentSetters(true)
                                .withRoutines(false))
                        .withTarget(new Target()
                                .withPackageName(packageName)
                                .withDirectory(outputDirectory)));

        GenerationTool.generate(configuration);
    }
}
