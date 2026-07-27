package com.jolumn.vtslseckill.product.repository;

import com.jolumn.vtslseckill.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.isDeleted = 0")
    Optional<Product> findById(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.status = 1 AND p.stock > 0 AND p.isDeleted = 0")
    Optional<Product> findAvailableById(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.isDeleted = 0")
    Page<Product> findAllActive(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isDeleted = 0 AND (:categoryId IS NULL OR p.categoryId = :categoryId)")
    Page<Product> findAllActiveByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.userId = :userId AND p.isDeleted = 0")
    Page<Product> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.userId = :userId AND p.status = :status AND p.isDeleted = 0")
    Page<Product> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status, Pageable pageable);
}
