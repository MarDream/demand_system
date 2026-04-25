package com.demand.system.integration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final DockerImageName RABBIT_IMAGE = DockerImageName.parse("rabbitmq:management");
    private static final DockerImageName MINIO_IMAGE = DockerImageName.parse("minio/minio:latest");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("demand_system")
            .withUsername("root")
            .withPassword("admin123")
            .withInitScript("db/init.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(RABBIT_IMAGE)
            .withExposedPorts(5672)
            .withEnv("RABBITMQ_DEFAULT_USER", "admin")
            .withEnv("RABBITMQ_DEFAULT_PASS", "admin")
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(MINIO_IMAGE)
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "admin")
            .withEnv("MINIO_ROOT_PASSWORD", "admin123456")
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "admin");
        registry.add("spring.rabbitmq.password", () -> "admin");

        registry.add("minio.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("minio.access-key", () -> "admin");
        registry.add("minio.secret-key", () -> "admin123456");
        registry.add("minio.bucket-name", () -> "demand-system");
    }

    @BeforeAll
    static void initMinioBucket() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials("admin", "admin123456")
                .build();

        boolean found = client.bucketExists(BucketExistsArgs.builder().bucket("demand-system").build());
        if (!found) {
            client.makeBucket(MakeBucketArgs.builder().bucket("demand-system").build());
        }
    }
}
