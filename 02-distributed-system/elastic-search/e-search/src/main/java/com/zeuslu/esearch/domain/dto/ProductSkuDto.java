package com.zeuslu.esearch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSkuDto {
    private String color;
    private String size;
    private Integer stock;
}
