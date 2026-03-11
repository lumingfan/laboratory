package com.zeuslu.esearch.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVo {
    private String id;
    private String productId;
    private String title;
    private String brand;
    private Long price;
    private Boolean status;
    private Integer salesVolume;
    private List<ProductSkuVo> productSkuVoList;
}
