package com.jolumn.vtslproduct.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class ProductPublishCmd {

    @NotBlank(message = "商品标题不能为空")
    private String title;

    private String subtitle;
    private String mainImage;
    private String detailImages;

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须大于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
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
