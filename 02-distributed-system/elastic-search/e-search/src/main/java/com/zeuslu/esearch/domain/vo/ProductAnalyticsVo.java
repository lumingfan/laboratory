package com.zeuslu.esearch.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductAnalyticsVo {
    private Map<String, Integer> brandDistribution;
    private Map<String, Integer> priceRangeDistribution;
}
