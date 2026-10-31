package lab.shift.crmsystemforshift;

import lab.shift.crmsystemforshift.data.dto.requests.SellerCreateRequest;
import lab.shift.crmsystemforshift.data.dto.requests.TransactionCreateRequest;
import lab.shift.crmsystemforshift.data.dto.response.SellerResponse;
import lab.shift.crmsystemforshift.data.dto.response.TransactionResponse;
import lab.shift.crmsystemforshift.data.entities.PaymentType;
import lab.shift.crmsystemforshift.repositories.SellerRepository;
import lab.shift.crmsystemforshift.repositories.TransactionRepository;
import lab.shift.crmsystemforshift.services.SellerService;
import lab.shift.crmsystemforshift.services.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TransactionPostgresRedisIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = postgres();

    @Container
    static final GenericContainer<?> REDIS = redis();

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registerContainers(registry, POSTGRES, REDIS);
    }

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private SellerService sellerService;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        sellerRepository.deleteAll();
    }

    @Test
    void createTransaction_persistsWithSellerReferenceInPostgres() {
        SellerResponse seller = sellerService.createSeller(new SellerCreateRequest("Мария Мариева", "maria@mail.com"));

        TransactionResponse tx = transactionService.createTransaction(
                new TransactionCreateRequest(seller.getSellerId(), 2500.75, PaymentType.CARD));

        assertTrue(tx.getId() > 0);
        assertEquals(seller.getSellerId(), tx.getSellerId());
        assertEquals(1, transactionRepository.findAll().size());
        assertTrue(transactionRepository.findAll().stream()
                .allMatch(t -> t.getSeller().getId().equals(seller.getSellerId())));
    }

    @Test
    void analytics_mostProductiveSeller_aggregatesInPostgres() {
        SellerResponse s1 = sellerService.createSeller(new SellerCreateRequest("Seller A", "a@mail.com"));
        SellerResponse s2 = sellerService.createSeller(new SellerCreateRequest("Seller B", "b@mail.com"));

        transactionService.createTransaction(new TransactionCreateRequest(s1.getSellerId(), 100.0, PaymentType.CASH));
        transactionService.createTransaction(new TransactionCreateRequest(s2.getSellerId(), 300.0, PaymentType.CARD));
        transactionService.createTransaction(new TransactionCreateRequest(s2.getSellerId(), 200.0, PaymentType.CARD));

        SellerResponse best = sellerService.getMostProductiveSeller(
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1));

        assertEquals("Seller B", best.getName(), "Самый продуктивный продавец определяется по сумме транзакций");
    }

    @Test
    void analytics_belowAmount_filtersByTotalInPostgres() {
        SellerResponse s1 = sellerService.createSeller(new SellerCreateRequest("Seller C", "c@mail.com"));
        SellerResponse s2 = sellerService.createSeller(new SellerCreateRequest("Seller D", "d@mail.com"));

        transactionService.createTransaction(new TransactionCreateRequest(s1.getSellerId(), 4000.0, PaymentType.CARD));
        transactionService.createTransaction(new TransactionCreateRequest(s2.getSellerId(), 1000.0, PaymentType.CARD));

        var below = sellerService.getSellersBelowAmount(
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                2000.0);

        assertEquals(1, below.size());
        assertEquals("Seller D", below.get(0).getName());
    }
}