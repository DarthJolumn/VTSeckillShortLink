package com.jolumn.vtslproduct.controller;

import com.jolumn.vtslcommon.annotation.RequireAuth;
import com.jolumn.vtslcommon.context.UserContext;
import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslproduct.entity.ProductCategory;
import com.jolumn.vtslproduct.repository.ProductCategoryRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product/category")
public class CategoryController {

    private final ProductCategoryRepository categoryRepo;

    public CategoryController(ProductCategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    @GetMapping("/list")
    public Result<List<ProductCategory>> list(
            @RequestParam(required = false) Long parentId) {
        if (parentId != null) {
            return Result.ok(categoryRepo.findByParentIdAndStatusOrderBySortOrderAsc(parentId, 1));
        }
        return Result.ok(categoryRepo.findByStatusOrderBySortOrderAsc(1));
    }

    @PostMapping
    @RequireAuth
    public Result<ProductCategory> create(@Valid @RequestBody ProductCategory category) {
        return Result.ok(categoryRepo.save(category));
    }
}
