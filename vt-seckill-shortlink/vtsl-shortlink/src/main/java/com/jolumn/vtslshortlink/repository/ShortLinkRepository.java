package com.jolumn.vtslshortlink.repository;

import com.jolumn.vtslshortlink.entity.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByShortCode(String shortCode);

    Optional<ShortLink> findByUrlHash(String urlHash);

    Page<ShortLink> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT s.shortCode FROM ShortLink s WHERE s.status = 1")
    List<String> findAllActiveCodes();
}
