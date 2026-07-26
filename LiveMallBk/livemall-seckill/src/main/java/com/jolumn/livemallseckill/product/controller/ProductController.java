package com.jolumn.livemallseckill.product.controller;

import com.jolumn.livemallcommon.annotation.RequireAuth;
import com.jolumn.livemallcommon.dto.PageResult;
import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallseckill.product.dto.ProductDTO;
import com.jolumn.livemallseckill.product.dto.ProductPublishCmd;
import com.jolumn.livemallseckill.product.dto.ProductUpdateCmd;
import com.jolumn.livemallseckill.product.service.ProductFacade;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/product")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductFacade productFacade;

    public ProductController(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    @PostMapping("/publish")
    @RequireAuth
    public Result<Long> publish(@Valid @RequestBody ProductPublishCmd cmd,
                                @RequestHeader("X-User-Id") Long userId) {
        cmd.setUserId(userId);
        Long productId = productFacade.publish(cmd);
        log.info("商家发布商品: userId={}, productId={}", userId, productId);
        return Result.ok(productId);
    }

    @GetMapping("/{id}")
    public Result<ProductDTO> getById(@PathVariable Long id) {
        return Result.ok(productFacade.getById(id));
    }

    @GetMapping("/{id}/available")
    public Result<Boolean> checkAvailable(@PathVariable Long id) {
        return Result.ok(productFacade.checkAvailable(id));
    }

    @GetMapping("/list")
    public Result<PageResult<ProductDTO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return Result.ok(productFacade.list(page, size, userId, categoryId, sortBy, sortDir));
    }

    @PutMapping("/{id}")
    @RequireAuth
    public Result<Void> update(@PathVariable Long id,
                                @Valid @RequestBody ProductUpdateCmd cmd,
                                @RequestHeader("X-User-Id") Long userId) {
        productFacade.update(id, userId, cmd);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequireAuth
    public Result<Void> updateStatus(@PathVariable Long id,
                                      @RequestBody Map<String, Integer> body,
                                      @RequestHeader("X-User-Id") Long userId) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return Result.error(400, "status 必须为 0 或 1");
        }
        productFacade.updateStatus(id, userId, status);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public Result<Void> softDelete(@PathVariable Long id,
                                    @RequestHeader("X-User-Id") Long userId) {
        productFacade.softDelete(id, userId);
        return Result.ok();
    }
}
