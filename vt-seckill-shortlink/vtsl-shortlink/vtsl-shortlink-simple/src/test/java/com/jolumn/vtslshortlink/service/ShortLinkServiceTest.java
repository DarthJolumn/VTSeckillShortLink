package com.jolumn.vtslshortlink.service;

import com.jolumn.vtslcommon.api.ProductShortLinkService;
import com.jolumn.vtslcommon.dto.PageResult;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslshortlink.dto.ShortLinkVO;
import com.jolumn.vtslshortlink.entity.ShortLink;
import com.jolumn.vtslshortlink.repository.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    private ShortLinkService service;

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private ShortLinkCache shortLinkCache;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ProductShortLinkService productShortLinkService;

    private ShortLink createTestEntity(String shortCode, String url, Long productId) {
        ShortLink entity = new ShortLink();
        entity.setId(1L);
        entity.setShortCode(shortCode);
        entity.setOriginalUrl(url);
        entity.setProductId(productId);
        entity.setUrlHash("testhash");
        entity.setStatus(1);
        entity.setClickCount(0L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpireAt(LocalDateTime.now().plusDays(30));
        return entity;
    }

    @BeforeEach
    void setUp() {
        service = new ShortLinkService(
                shortLinkRepository,
                shortLinkCache,
                idGenerator,
                statisticsService,
                redisTemplate,
                30
        );
        ReflectionTestUtils.setField(service, "productShortLinkService", productShortLinkService);
    }

    // ========= getOriginalUrl =========

    @Test
    void testGetOriginalUrl_L1Hit() {
        when(shortLinkCache.isBlocked("Pcode")).thenReturn(false);
        when(shortLinkCache.getFromL1("Pcode")).thenReturn("/product/1");

        String url = service.getOriginalUrl("Pcode");
        assertEquals("/product/1", url);
        verify(statisticsService).recordClick("Pcode");
        verify(shortLinkCache).incrementClickCount("Pcode");
    }

    @Test
    void testGetOriginalUrl_L2Hit() {
        when(shortLinkCache.isBlocked("Pcode")).thenReturn(false);
        when(shortLinkCache.getFromL1("Pcode")).thenReturn(null);
        when(shortLinkCache.getFromL2("Pcode")).thenReturn("/product/2");

        String url = service.getOriginalUrl("Pcode");
        assertEquals("/product/2", url);
    }

    @Test
    void testGetOriginalUrl_AlgorithmFallback() {
        when(shortLinkCache.isBlocked("Pcode")).thenReturn(false);
        when(shortLinkCache.getFromL1("Pcode")).thenReturn(null);
        when(shortLinkCache.getFromL2("Pcode")).thenReturn(null);
        when(productShortLinkService.getAvailableProductUrl(anyLong()))
                .thenReturn("/product/3");

        String url = service.getOriginalUrl("Pcode");
        assertEquals("/product/3", url);
        verify(shortLinkCache).putWithProduct(eq("Pcode"), eq("/product/3"), anyLong(), any());
    }

    @Test
    void testGetOriginalUrl_DbFallback() {
        String shortCode = "dbCode";
        ShortLink entity = createTestEntity(shortCode, "/dbpage", 1L);

        when(shortLinkCache.isBlocked(shortCode)).thenReturn(false);
        when(shortLinkCache.getFromL1(shortCode)).thenReturn(null);
        when(shortLinkCache.getFromL2(shortCode)).thenReturn(null);
        when(shortLinkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(entity));

        String url = service.getOriginalUrl(shortCode);
        assertEquals("/dbpage", url);
        verify(shortLinkCache).put(eq(shortCode), eq("/dbpage"), any());
    }

    @Test
    void testGetOriginalUrl_Blocked() {
        when(shortLinkCache.isBlocked("Pblocked")).thenReturn(true);

        assertThrows(BizException.class, () -> service.getOriginalUrl("Pblocked"));
    }

    @Test
    void testGetOriginalUrl_AlgorithmNullProduct() {
        when(shortLinkCache.isBlocked("Pcode")).thenReturn(false);
        when(shortLinkCache.getFromL1("Pcode")).thenReturn(null);
        when(shortLinkCache.getFromL2("Pcode")).thenReturn(null);
        when(productShortLinkService.getAvailableProductUrl(anyLong())).thenReturn(null);

        assertThrows(BizException.class, () -> service.getOriginalUrl("Pcode"));
    }

    @Test
    void testGetOriginalUrl_DbNotFound() {
        String shortCode = "nonexistent";
        when(shortLinkCache.isBlocked(shortCode)).thenReturn(false);
        when(shortLinkCache.getFromL1(shortCode)).thenReturn(null);
        when(shortLinkCache.getFromL2(shortCode)).thenReturn(null);
        when(shortLinkRepository.findByShortCode(shortCode)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> service.getOriginalUrl(shortCode));
    }

    @Test
    void testGetOriginalUrl_DbExpired() {
        String shortCode = "expired";
        ShortLink entity = createTestEntity(shortCode, "/old", 1L);
        entity.setExpireAt(LocalDateTime.now().minusDays(1));

        when(shortLinkCache.isBlocked(shortCode)).thenReturn(false);
        when(shortLinkCache.getFromL1(shortCode)).thenReturn(null);
        when(shortLinkCache.getFromL2(shortCode)).thenReturn(null);
        when(shortLinkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(entity));

        assertThrows(BizException.class, () -> service.getOriginalUrl(shortCode));
    }

    // ========= createShortLink =========

    @Test
    void testCreateShortLink_New() {
        Long productId = 100L;
        String url = "/product/100";
        String urlHash = "md5ofurl";
        String shortCode = "PnewCode";

        when(shortLinkCache.getExistingCodeByHash(anyString())).thenReturn(null);
        when(shortLinkRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());
        when(idGenerator.nextCode()).thenReturn(shortCode);
        when(shortLinkRepository.findByShortCode(shortCode))
                .thenReturn(Optional.of(createTestEntity(shortCode, url, productId)));

        ShortLinkVO vo = service.createShortLink(1L, productId, url, "test");
        assertNotNull(vo);
        assertEquals(shortCode, vo.getShortCode());
        assertEquals(url, vo.getOriginalUrl());
        verify(shortLinkCache).putHashMapping(anyString(), eq(shortCode), any());
    }

    @Test
    void testCreateShortLink_DuplicateKeyRetry() {
        Long productId = 200L;
        String url = "/product/200";
        String urlHash = "md5ofurl2";
        String shortCode1 = "Pconflict";
        String shortCode2 = "PretryOk";

        when(shortLinkCache.getExistingCodeByHash(anyString())).thenReturn(null);
        when(shortLinkRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());
        when(idGenerator.nextCode()).thenReturn(shortCode1, shortCode2);
        when(shortLinkRepository.save(any()))
                .thenThrow(new DuplicateKeyException("冲突"))
                .thenReturn(null);
        when(shortLinkRepository.findByShortCode(shortCode2))
                .thenReturn(Optional.of(createTestEntity(shortCode2, url, productId)));

        ShortLinkVO vo = service.createShortLink(1L, productId, url, "test");
        assertNotNull(vo);
        assertEquals(shortCode2, vo.getShortCode());
        verify(idGenerator, times(2)).nextCode();
    }

    @Test
    void testCreateShortLink_ExistingInRedis() {
        String url = "/product/300";
        when(shortLinkCache.getExistingCodeByHash(anyString())).thenReturn("Pexisting");

        String code = service.createShortLink(300L, url);
        assertEquals("Pexisting", code);
        verify(shortLinkRepository, never()).findByUrlHash(anyString());
    }

    @Test
    void testCreateShortLink_ExistingInDb() {
        String url = "/product/400";
        String shortCode = "PexistingDb";
        when(shortLinkCache.getExistingCodeByHash(anyString())).thenReturn(null);
        when(shortLinkRepository.findByUrlHash(anyString()))
                .thenReturn(Optional.of(createTestEntity(shortCode, url, 400L)));

        String code = service.createShortLink(400L, url);
        assertEquals(shortCode, code);
        verify(shortLinkCache).putHashMapping(anyString(), eq(shortCode), any());
    }

    // ========= listByUser / findById / softDelete =========

    @Test
    void testListByUser() {
        Long userId = 1L;
        ShortLink entity = createTestEntity("Plist", "/product/1", 1L);
        Page<ShortLink> page = new PageImpl<>(List.of(entity));
        when(shortLinkRepository.findByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(page);

        PageResult<ShortLinkVO> result = service.listByUser(userId, 1, 20);
        assertEquals(1, result.getRecords().size());
        assertEquals("Plist", result.getRecords().get(0).getShortCode());
    }

    @Test
    void testFindById() {
        ShortLink entity = createTestEntity("Pfind", "/product/99", 99L);
        when(shortLinkRepository.findById(1L)).thenReturn(Optional.of(entity));

        ShortLinkVO vo = service.findById(1L);
        assertEquals("Pfind", vo.getShortCode());
    }

    @Test
    void testFindByIdNotFound() {
        when(shortLinkRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.findById(999L));
    }

    @Test
    void testSoftDelete() {
        ShortLink entity = createTestEntity("Pdel", "/product/1", 1L);
        entity.setUserId(1L);
        when(shortLinkRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.softDelete(1L, 1L);
        assertEquals(2, entity.getStatus());
        verify(shortLinkRepository).save(entity);
    }

    @Test
    void testSoftDelete_Forbidden() {
        ShortLink entity = createTestEntity("Pdel", "/product/1", 1L);
        entity.setUserId(2L);
        when(shortLinkRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThrows(BizException.class, () -> service.softDelete(1L, 1L));
    }
}
