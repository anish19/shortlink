package io.github.anish19.shortlink;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
}
