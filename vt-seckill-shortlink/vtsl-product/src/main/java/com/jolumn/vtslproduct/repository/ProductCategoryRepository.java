package com.jolumn.vtslproduct.repository;

import com.jolumn.vtslproduct.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByStatusOrderBySortOrderAsc(Integer status);

    List<ProductCategory> findByParentIdAndStatusOrderBySortOrderAsc(Long parentId, Integer status);
}
