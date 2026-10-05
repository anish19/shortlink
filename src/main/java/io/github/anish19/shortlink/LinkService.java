package io.github.anish19.shortlink;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class LinkService {
    private record Cursor(Instant createdAt, Long id) {}
    private static final int SHORT_URL_LENGTH = 7;
    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random = new SecureRandom();
    private final long TEMP_DEV_USER_ID = 1L;

    private final LinkRepository linkRepository;
    LinkService(LinkRepository linkRepository){
        this.linkRepository = linkRepository;
    }

    private String encodeCursor(Link last) {
        String raw = last.getCreatedAt().toString() + "|" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String parts[] = raw.split("\\|");
            if (parts.length != 2) throw new InvalidCursorException("Malformed cursor");
            return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursorException("Malformed cursor");
        }
    }

    private String generateShortCode(){
        StringBuilder shortCodeBuilder = new StringBuilder();
        for (int i = 0; i<SHORT_URL_LENGTH; i++) {
            shortCodeBuilder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return shortCodeBuilder.toString();
    }

    public LinkPage listLinks(String cursor, int limit) {
        int pageSize = Math.clamp(limit, 1, 100);
        Limit fetch = Limit.of(pageSize + 1);

        List<Link> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = linkRepository.findByUserIdOrderByCreatedAtDescIdDesc(TEMP_DEV_USER_ID, fetch);
        } else {
            Cursor c = decodeCursor(cursor);
            rows = linkRepository.findPageAfter(TEMP_DEV_USER_ID, c.createdAt(), c.id(), fetch);
        }

        boolean hasMore = rows.size() > pageSize;
        List<Link> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore? encodeCursor(page.getLast()) : null;

        List<LinkSummary> items = page.stream()
                .map(link -> new LinkSummary(link.getShortCode(), link.getLongUrl(), link.getCreatedAt(), link.getExpiresAt()))
                .toList();
        return new LinkPage(items, nextCursor);
    }

    public String createLink(String longUrl) throws IllegalStateException {
        for (int i=0; i<5; i++) {
            String shortCode = generateShortCode();
            Link link = new Link();
            link.setUserId(TEMP_DEV_USER_ID); // TODO: use the logged in user
            link.setShortCode(shortCode);
            link.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            link.setLongUrl(longUrl);
            try {
                linkRepository.saveAndFlush(link);
                return shortCode;
            } catch (DataIntegrityViolationException e) {
                //short code collision, retry with new code.
            }
        }
        throw new IllegalStateException("Could not generate unique code.");
    }

    public String findByShortCode(String shortCode) {
        Optional<Link> link = linkRepository.findByShortCode(shortCode);
        if (link.isEmpty()) {
            throw new LinkNotFoundException("Given shortCode is not registered.");
        } else if (link.get().getExpiresAt() != null &&
                link.get().getExpiresAt().isBefore(Instant.now())) {
            throw new LinkExpiredException(String.format("The shortCode for given link expired on %s.", link.get().getExpiresAt()));
        }
        return link.get().getLongUrl();
    }

    public void deleteLink(String shortCode) {
        Link link = linkRepository.findByShortCode(shortCode).orElseThrow(() -> new LinkNotFoundException("Short code not found"));
        if (!link.getUserId().equals(TEMP_DEV_USER_ID)) {
            throw new LinkNotFoundException("Short code not found.");
        }
        linkRepository.delete(link);
    }
}