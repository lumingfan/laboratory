package com.zeuslu.esearch.service;

import com.zeuslu.esearch.domain.dto.ProductBulkDto;
import com.zeuslu.esearch.domain.dto.ProductSearchDto;
import com.zeuslu.esearch.domain.vo.ProductAnalyticsVo;
import com.zeuslu.esearch.domain.vo.ProductSearchVo;

public interface ProductService {
    Boolean bulkIngestion(ProductBulkDto productBulkDto);

    ProductSearchVo search(ProductSearchDto productSearchDto);

    ProductAnalyticsVo analytics(String keyword);
}
