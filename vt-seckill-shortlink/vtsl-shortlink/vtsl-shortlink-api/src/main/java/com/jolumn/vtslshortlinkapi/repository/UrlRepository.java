package com.jolumn.vtslshortlinkapi.repository;

import com.jolumn.vtslshortlinkapi.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortKeyAndDeletedAtIsNull(String shortKey);

    List<Url> findByUserIdAndDeletedAtIsNull(Long userId);

    boolean existsByOriginalUrlAndUserIdAndDeletedAtIsNull(String originalUrl, Long userId);

    boolean existsByShortKeyAndDeletedAtIsNull(String shortKey);

    @Modifying
    @Query("UPDATE Url u SET u.clicks = u.clicks + 1 WHERE u.id = :id")
    int incrementClicks(@Param("id") Long id);
}
