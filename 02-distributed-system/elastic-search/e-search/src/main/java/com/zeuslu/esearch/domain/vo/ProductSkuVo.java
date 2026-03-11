package com.zeuslu.esearch.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSkuVo {
    private String color;
    private String size;
    private Integer stock;
}
