package io.github.anish19.shortlink;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class LinkController {
    private final LinkService linkService;

    LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("/links")
    public ResponseEntity<Void> createLinkRequest(@Valid @RequestBody CreateLinkRequest request) {
        return ResponseEntity.created(URI.create("/" + linkService.createLink(request.url()))).build();
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> getUrl(@PathVariable String code) {
        // Return 302, so browser asks our server every time and so expiring or deleting a link takes effect
        // immediately. A cached 301 would keep redirecting even after the link is gone and we can keep track of clicks.
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(linkService.findByShortCode(code))).build();
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<Void> handleLinkNotFoundException(){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<Void> handleLinkExpiredException(){
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
