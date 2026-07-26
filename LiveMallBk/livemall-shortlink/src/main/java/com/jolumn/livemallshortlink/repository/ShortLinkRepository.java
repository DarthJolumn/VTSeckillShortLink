package com.jolumn.livemallshortlink.repository;

import com.jolumn.livemallshortlink.entity.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByShortCode(String shortCode);

    Optional<ShortLink> findByUrlHash(String urlHash);

    Page<ShortLink> findByUserId(Long userId, Pageable pageable);
}
