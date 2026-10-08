package io.github.anish19.shortlink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LinkControllerIntegrationTest {

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private LinkService linkService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String LONG_URL = "https://www.example.com/home";
    private final String RANDOM_SHORT_CODE = "plokij";

    @BeforeEach
    void setUp() {
        // Clear the database before each test
        linkRepository.deleteAll();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash) OVERRIDING SYSTEM VALUE " +
                "VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                1L, "dev@example.com", "test-hash");
    }

    // Test: create a new short link with a valid URL
    @Test
    void shouldCreateLinkWithValidUrl() {
        String shortCode = linkService.createLink(LONG_URL);

        assertNotNull(shortCode);
    }

    // Test: retrieve the original URL from a short code
    @Test
    void shouldFindUrlByShortCode() {
        String shortCode = linkService.createLink(LONG_URL);

        String longUrl = linkService.findByShortCode(shortCode);

        assertEquals(LONG_URL, longUrl);
    }

    // Test: throw LinkNotFoundException for non-existent code
    @Test
    void shouldThrowLinkNotFoundExceptionForNonExistentCode() {
        assertThrows(LinkNotFoundException.class, ()->{
            linkService.findByShortCode(RANDOM_SHORT_CODE);
        });
    }

    // Test: delete an existing link
    @Test
    void shouldDeleteExistingLink() {
        String shortCode = linkService.createLink(LONG_URL);

        assertNotNull(linkService.findByShortCode(shortCode));
        assertEquals(LONG_URL, linkService.findByShortCode(shortCode));

        linkService.deleteLink(shortCode);

        assertThrows(LinkNotFoundException.class, () -> {
            linkService.findByShortCode(shortCode);
        });
    }

    // Test: throw LinkNotFoundException when deleting non-existent code
    @Test
    void shouldThrowLinkNotFoundExceptionWhenDeletingNonExistentCode() {
        assertThrows(LinkNotFoundException.class, ()-> {
            linkService.deleteLink(RANDOM_SHORT_CODE);
        });
    }

    // Test: list links with cursor pagination
    @Test
    void shouldListLinksWithPagination() {

        for (int i=0; i<10; i++) {
            linkService.createLink(LONG_URL+ i);
        }

        LinkPage shortLinksPage = linkService.listLinks(null, 5);
        assertEquals(5, shortLinksPage.items().size());
        assertNotNull(shortLinksPage.nextCursor());
    }

    // Test: use cursor to paginate to the next page
    @Test
    void shouldPaginateUsingCursor() {
        String[] shortLinks = new String[10];
        for (int i=0; i<10; i++) {
            shortLinks[i] = linkService.createLink(LONG_URL+ i);
        }

        LinkPage firstPage = linkService.listLinks(null, 5);
        LinkPage secondPage = linkService.listLinks(firstPage.nextCursor(), 5);

        Set<String> expectedSecondPage = secondPage.items().stream()
                                        .map(LinkSummary::shortCode)
                                        .collect(Collectors.toSet());

        assertEquals(5, secondPage.items().size());
        assertTrue(expectedSecondPage.contains(shortLinks[4]));
        assertTrue(expectedSecondPage.contains(shortLinks[3]));
        assertTrue(expectedSecondPage.contains(shortLinks[2]));
        assertTrue(expectedSecondPage.contains(shortLinks[1]));
        assertTrue(expectedSecondPage.contains(shortLinks[0]));
    }

    // Test: throw InvalidCursorException for malformed cursor
    @Test
    void shouldThrowInvalidCursorExceptionForBadCursor() {
        assertThrows(InvalidCursorException.class, ()->{
            linkService.listLinks("invalid_base!", 10);
        });
    }
}
