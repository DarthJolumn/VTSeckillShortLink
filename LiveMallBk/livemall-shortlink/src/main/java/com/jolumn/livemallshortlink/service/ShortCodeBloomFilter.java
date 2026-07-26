package com.jolumn.livemallshortlink.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.jolumn.livemallshortlink.repository.ShortLinkRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ShortCodeBloomFilter {

    private static final Logger log = LoggerFactory.getLogger(ShortCodeBloomFilter.class);
    private static final double FPP = 0.01;
    private static final long EXPECTED_SIZE = 100_000;

    private volatile BloomFilter<CharSequence> filter;

    private final ShortLinkRepository shortLinkRepository;

    public ShortCodeBloomFilter(ShortLinkRepository shortLinkRepository) {
        this.shortLinkRepository = shortLinkRepository;
    }

    @PostConstruct
    public void init() {
        rebuild();
    }

    @Scheduled(fixedDelay = 120_000)
    public void rebuild() {
        List<String> codes = shortLinkRepository.findAllActiveCodes();
        BloomFilter<CharSequence> newFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                Math.max(codes.size(), EXPECTED_SIZE),
                FPP);
        for (String code : codes) {
            newFilter.put(code);
        }
        filter = newFilter;
        log.info("短码 BloomFilter 重建完成: {} 个短码, 误判率={}", codes.size(), FPP);
    }

    public boolean mightContain(String shortCode) {
        BloomFilter<CharSequence> f = filter;
        return f == null || f.mightContain(shortCode);
    }

    /** 创建短码后即时插入，不必等 2min 重建窗口 */
    public void add(String shortCode) {
        BloomFilter<CharSequence> f = filter;
        if (f != null) f.put(shortCode);
    }
}
