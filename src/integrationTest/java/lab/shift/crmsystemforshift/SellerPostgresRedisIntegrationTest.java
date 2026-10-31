package lab.shift.crmsystemforshift;

import lab.shift.crmsystemforshift.data.dto.requests.SellerCreateRequest;
import lab.shift.crmsystemforshift.data.dto.requests.SellerUpdateRequest;
import lab.shift.crmsystemforshift.data.dto.response.SellerResponse;
import lab.shift.crmsystemforshift.data.entities.SellerEntity;
import lab.shift.crmsystemforshift.repositories.SellerRepository;
import lab.shift.crmsystemforshift.services.SellerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SellerPostgresRedisIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = postgres();

    @Container
    static final GenericContainer<?> REDIS = redis();

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registerContainers(registry, POSTGRES, REDIS);
    }

    @Autowired
    private SellerService sellerService;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void cleanDatabase() {
        sellerRepository.deleteAll();
    }

    /**
     * Запись/инвалидация кэша в Redis через отдельный {@link StringRedisTemplate} видна не мгновенно
     * (соединение кэш-райтера и шаблона могут отличаться). Поэтому состояние ключа опрашиваем
     * короткий период вместо единичного мгновенного assert.
     */
    private void awaitCacheKey(String cacheKey, boolean shouldBePresent) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && Boolean.TRUE.equals(redis.hasKey(cacheKey)) != shouldBePresent) {
            Thread.sleep(50);
        }
        boolean present = Boolean.TRUE.equals(redis.hasKey(cacheKey));
        if (shouldBePresent) {
            assertTrue(present, "Кэш должен содержать ключ: " + cacheKey);
        } else {
            assertFalse(present, "Кэш должен быть сброшен: " + cacheKey);
        }
    }

    @Test
    void createSeller_persistsInPostgres() {
        SellerResponse created = sellerService.createSeller(new SellerCreateRequest("Иван Иванов", "ivan@mail.com"));

        assertTrue(created.getSellerId() > 0);

        SellerEntity fromDb = sellerRepository.findById(created.getSellerId()).orElseThrow();
        assertEquals("Иван Иванов", fromDb.getName());
        assertEquals("ivan@mail.com", fromDb.getContactInfo());
    }

    @Test
    void getSellerById_cachesResultInRedis() throws InterruptedException {
        SellerResponse created = sellerService.createSeller(new SellerCreateRequest("Пётр Петров", "petr@mail.com"));

        sellerService.getSellerById(created.getSellerId());

        String cacheKey = "sellers::" + created.getSellerId();
        awaitCacheKey(cacheKey, true);
    }

    @Test
    void updateSeller_evictsCache() throws InterruptedException {
        SellerResponse created = sellerService.createSeller(new SellerCreateRequest("Анна", "anna@mail.com"));

        sellerService.getSellerById(created.getSellerId());
        String cacheKey = "sellers::" + created.getSellerId();
        awaitCacheKey(cacheKey, true);

        sellerService.updateSeller(
                created.getSellerId(),
                new SellerUpdateRequest(created.getSellerId(), "Анна Смирнова", "anna.smirnova@mail.com"));

        awaitCacheKey(cacheKey, false);
    }

    @Test
    void deleteSeller_softDeletesInPostgresAndEvictsCache() throws InterruptedException {
        SellerResponse created = sellerService.createSeller(new SellerCreateRequest("Олег Олегов", "oleg@mail.com"));

        sellerService.getSellerById(created.getSellerId());
        String cacheKey = "sellers::" + created.getSellerId();
        awaitCacheKey(cacheKey, true);

        sellerService.deleteSeller(created.getSellerId());

        awaitCacheKey(cacheKey, false);
        assertTrue(sellerRepository.findById(created.getSellerId()).isEmpty(),
                "Продавец не должен возвращаться активным запросом");
        assertTrue(sellerRepository.findAllWithDeleted().stream()
                        .anyMatch(s -> s.getId().equals(created.getSellerId()) && s.getDeletionDate() != null),
                "Запись должна сохраниться в PostgreSQL как мягко удалённая");
    }
}