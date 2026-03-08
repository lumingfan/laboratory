package com.zeuslu.es.basic.entity.dto;

import com.zeuslu.es.basic.entity.EsProductAttr;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ESRequestParam {
    private String keyword;
    private Long categoryId;
    private List<Long> brandId;
    private List<String> attrs;
    private Boolean hasStock;
    private String price;
    private String sort;
    private Integer pageNum;

}
