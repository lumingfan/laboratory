package com.zeuslu.esearch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchDto {
    private String keyword;
    private String brand;
    private Integer minPrice;
    private Integer maxPrice;
    private List<ProductSkuDto> productSkuDtoList;
}
