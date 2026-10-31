package lab.shift.crmsystemforshift;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * База для интеграционных тестов. Поднимает эфемерные контейнеры PostgreSQL и Redis
 * (Testcontainers) и переопределяет конфигурацию приложения на них.
 * Контейнеры живут только на время выполнения тестового класса и автоматически удаляются.
 *
 * <p>Каждый конкретный тестовый класс объявляет СОБСТВЕННЫЕ экземпляры контейнеров через
 * {@link #postgres()} и {@link #redis()} и регистрирует их в {@link #registerContainers(...)}.
 * Это необходимо, чтобы параллельные/последовательные запуски классов не делили (и не гасили)
 * общий контейнер.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    protected static PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("crmdb")
                .withUsername("crmuser")
                .withPassword("crmpassword");
    }

    protected static GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }

    protected static void registerContainers(DynamicPropertyRegistry registry,
                                             PostgreSQLContainer<?> postgres,
                                             GenericContainer<?> redis) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Контейнер каждый раз свежий, поэтому достаточно "create" без drop на shutdown:
        // create-drop при остановке контекста пытается обратиться к уже останавливающейся БД
        // и висит ~30 секунд (timeout пула соединений), что искажает замеры времени.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.cache.type", () -> "redis");
    }
}