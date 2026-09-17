package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Language;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the initial Java good/bad practices knowledge base on first boot
 * only (guarded by an empty-table check). Practices are meant to be curated
 * by humans afterward through the CRUD API/dashboard — this is a starting
 * set, not something re-applied on every restart.
 */
@Component
public class PracticeSeeder implements ApplicationRunner {

    private final PracticeRepository practiceRepository;

    public PracticeSeeder(PracticeRepository practiceRepository) {
        this.practiceRepository = practiceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (practiceRepository.count() > 0) {
            return;
        }
        practiceRepository.saveAll(seedPractices());
    }

    private List<Practice> seedPractices() {
        return List.of(
                practice(
                        "Empty catch block swallows exceptions",
                        "Catching an exception and doing nothing hides failures until they "
                                + "surface somewhere confusing much later, instead of at the point they "
                                + "actually happened.",
                        Category.CORRECTNESS,
                        Severity.HIGH,
                        """
                        try {
                            processOrder(order);
                        } catch (Exception e) {
                        }
                        """,
                        """
                        try {
                            processOrder(order);
                        } catch (OrderProcessingException e) {
                            log.error("Failed to process order {}", order.getId(), e);
                            throw e;
                        }
                        """,
                        "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}"),
                practice(
                        "Catching generic Exception instead of a specific type",
                        "Catching Exception or Throwable masks unrelated failures (including "
                                + "unchecked bugs like a NullPointerException) as if they were the "
                                + "expected failure mode.",
                        Category.CORRECTNESS,
                        Severity.MEDIUM,
                        """
                        try {
                            readConfig(path);
                        } catch (Exception e) {
                            log.warn("Config read failed", e);
                        }
                        """,
                        """
                        try {
                            readConfig(path);
                        } catch (IOException e) {
                            log.warn("Config read failed", e);
                        }
                        """,
                        "catch\\s*\\(\\s*(Exception|Throwable)\\s+\\w+\\s*\\)"),
                practice(
                        "String compared with == instead of .equals()",
                        "== compares object identity, not content. Two equal strings from "
                                + "different sources (e.g. one from a DB read) can be == false while "
                                + ".equals() correctly returns true.",
                        Category.CORRECTNESS,
                        Severity.HIGH,
                        """
                        if (status == "APPROVED") {
                            approve(order);
                        }
                        """,
                        """
                        if ("APPROVED".equals(status)) {
                            approve(order);
                        }
                        """,
                        "==\\s*\"[^\"]*\"|\"[^\"]*\"\\s*=="),
                practice(
                        "String concatenation in a loop",
                        "Each += on a String allocates a new String; over N iterations this is "
                                + "O(n^2). StringBuilder amortizes to O(n).",
                        Category.PERFORMANCE,
                        Severity.MEDIUM,
                        """
                        String csv = "";
                        for (Order order : orders) {
                            csv += order.getId() + ",";
                        }
                        """,
                        """
                        StringBuilder csv = new StringBuilder();
                        for (Order order : orders) {
                            csv.append(order.getId()).append(",");
                        }
                        """,
                        "for\\s*\\([^)]*\\)\\s*\\{[^}]*\\w+\\s*\\+=.*\\+"),
                practice(
                        "SQL built via string concatenation",
                        "Concatenating user-controlled values into a SQL string is a SQL "
                                + "injection vector. A PreparedStatement with bound parameters is "
                                + "not optional here, even for internal tools.",
                        Category.SECURITY,
                        Severity.CRITICAL,
                        """
                        String sql = "SELECT * FROM users WHERE id = '" + userId + "'";
                        Statement stmt = connection.createStatement();
                        ResultSet rs = stmt.executeQuery(sql);
                        """,
                        """
                        String sql = "SELECT * FROM users WHERE id = ?";
                        PreparedStatement stmt = connection.prepareStatement(sql);
                        stmt.setString(1, userId);
                        ResultSet rs = stmt.executeQuery();
                        """,
                        "\"[^\"]*SELECT[^\"]*\"\\s*\\+"),
                practice(
                        "Hardcoded credential or API key in source",
                        "A committed secret is compromised the moment it's pushed, whether or "
                                + "not the repo is public today \u2014 history doesn't forget. Load "
                                + "secrets from environment/config at runtime instead.",
                        Category.SECURITY,
                        Severity.CRITICAL,
                        """
                        String apiKey = "sk-live-91a2b3c4d5e6f7g8h9i0";
                        """,
                        """
                        String apiKey = System.getenv("OPENAI_API_KEY");
                        """,
                        "(password|apiKey|secret|token)\\s*=\\s*\"[^\"]{8,}\""),
                practice(
                        "Field injection instead of constructor injection",
                        "@Autowired on a field hides the dependency from the constructor "
                                + "signature, makes the class harder to unit test without Spring, and "
                                + "allows constructing an object in an invalid half-wired state.",
                        Category.STYLE,
                        Severity.LOW,
                        """
                        @Autowired
                        private OrderService orderService;
                        """,
                        """
                        private final OrderService orderService;

                        public OrderController(OrderService orderService) {
                            this.orderService = orderService;
                        }
                        """,
                        "@Autowired\\s*\\n\\s*private"),
                practice(
                        "Closeable resource opened without try-with-resources",
                        "A stream/connection opened without try-with-resources leaks a file "
                                + "handle or connection if the code between open and close throws.",
                        Category.CORRECTNESS,
                        Severity.HIGH,
                        """
                        FileInputStream in = new FileInputStream(file);
                        process(in);
                        in.close();
                        """,
                        """
                        try (FileInputStream in = new FileInputStream(file)) {
                            process(in);
                        }
                        """,
                        "new\\s+(FileInputStream|FileOutputStream|BufferedReader|FileReader)\\([^)]*\\)\\s*;"),
                practice(
                        "Returning null instead of Optional or an empty collection",
                        "A null return forces every caller to remember a null check or risk an "
                                + "NPE far from the source. An empty List/Optional makes the absent "
                                + "case impossible to forget.",
                        Category.STYLE,
                        Severity.MEDIUM,
                        """
                        public List<Order> findOrders(String userId) {
                            List<Order> orders = repository.findByUser(userId);
                            return orders.isEmpty() ? null : orders;
                        }
                        """,
                        """
                        public List<Order> findOrders(String userId) {
                            return repository.findByUser(userId);
                        }
                        """,
                        "return\\s+null\\s*;"),
                practice(
                        "Logging sensitive data",
                        "Passwords, tokens, and full card numbers written to logs end up in "
                                + "log aggregators, backups, and support tickets \u2014 systems with far "
                                + "weaker access control than the original request.",
                        Category.SECURITY,
                        Severity.HIGH,
                        """
                        log.info("Login attempt: user={} password={}", username, password);
                        """,
                        """
                        log.info("Login attempt: user={}", username);
                        """,
                        "log\\.\\w+\\([^)]*password[^)]*\\)"),
                practice(
                        "Test with no assertions",
                        "A test that only calls the method under test and returns proves the "
                                + "code doesn't throw \u2014 nothing about whether it did the right thing. "
                                + "It stays green through real regressions.",
                        Category.TEST_QUALITY,
                        Severity.MEDIUM,
                        """
                        @Test
                        void createOrderSucceeds() {
                            orderService.createOrder(request);
                        }
                        """,
                        """
                        @Test
                        void createOrderSucceeds() {
                            Order order = orderService.createOrder(request);
                            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
                        }
                        """,
                        // Not a clean regex target: needs "method body has no assert*/verify*
                        // call" reasoning, which chunk 4's rule engine may implement as an
                        // AST check rather than a pattern match.
                        "NO_ASSERTION_HEURISTIC"));
    }

    private Practice practice(
            String title,
            String description,
            Category category,
            Severity severity,
            String badExample,
            String goodExample,
            String detectionPattern) {
        return Practice.builder()
                .title(title)
                .description(description)
                .category(category)
                .severity(severity)
                .language(Language.JAVA)
                .badExample(badExample.strip())
                .goodExample(goodExample.strip())
                .detectionPattern(detectionPattern)
                .build();
    }
}
