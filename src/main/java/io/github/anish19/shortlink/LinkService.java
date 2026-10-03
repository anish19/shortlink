package io.github.anish19.shortlink;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
public class LinkService {
    private static final int SHORT_URL_LENGTH = 7;
    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random = new SecureRandom();

    private final LinkRepository linkRepository;
    LinkService(LinkRepository linkRepository){
        this.linkRepository = linkRepository;
    }

    private String generateShortCode(){
        StringBuilder shortCodeBuilder = new StringBuilder();
        for (int i = 0; i<SHORT_URL_LENGTH; i++) {
            shortCodeBuilder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return shortCodeBuilder.toString();
    }

    public String createLink(String longUrl) throws IllegalStateException {
        for (int i=0; i<5; i++) {
            String shortCode = generateShortCode();
            Link link = new Link();
            link.setUserId(1L); // TODO: use the logged in user
            link.setShortCode(shortCode);
            link.setCreatedAt(Instant.now());
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
}
