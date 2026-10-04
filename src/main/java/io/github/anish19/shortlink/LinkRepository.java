package io.github.anish19.shortlink;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
    List<Link> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Limit limit);
    @Query("""
        select l from Link l
        where l.userId = :userId
          and (l.createdAt < :createdAt or (l.createdAt = :createdAt and l.id < :id))
        order by l.createdAt desc, l.id desc
        """)
    List<Link> findPageAfter(@Param("userId") Long userId,
                             @Param("createdAt") Instant createdAt,
                             @Param("id") Long id,
                             Limit limit);

}
