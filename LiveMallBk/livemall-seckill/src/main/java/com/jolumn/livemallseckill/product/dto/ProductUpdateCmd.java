package com.jolumn.livemallseckill.product.dto;

import java.math.BigDecimal;

public class ProductUpdateCmd {

    private String title;
    private String subtitle;
    private String mainImage;
    private String detailImages;
    private BigDecimal price;
    private Integer stock;
    private Long categoryId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getMainImage() { return mainImage; }
    public void setMainImage(String mainImage) { this.mainImage = mainImage; }
    public String getDetailImages() { return detailImages; }
    public void setDetailImages(String detailImages) { this.detailImages = detailImages; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
