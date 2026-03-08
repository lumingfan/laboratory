package com.zeuslu.es.basic.entity.dto;

import com.zeuslu.es.basic.entity.EsProduct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class ESResponseResult {
    private List<EsProduct> esProducts;
    private List<BrandVo> brandVos;
    private List<CategoryVo> categoryVos;
    private List<AttrVo> attrVos;
    private Integer pageNum;
    private Integer totalPages;
    private List<Integer> pageNavs;
    private Long total;


    @Data
    public static class BrandVo {
        private Long brandId;
        private String brandName;
        private String brandImg;
    }

    @Data
    public static class CategoryVo {
        private Long categoryId;
        private String categoryName;
    }

    @Data
    public static class AttrVo {
        private Long attrId;
        private String attrName;
        private List<String> attrValue;
    }
}
