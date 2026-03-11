package com.zeuslu.esearch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private String id;
    private String productId;
    private String title;
    private String brand;
    private Long price;
    private Boolean status;
    private List<ProductSkuDto> productSkuDtoList;
}
