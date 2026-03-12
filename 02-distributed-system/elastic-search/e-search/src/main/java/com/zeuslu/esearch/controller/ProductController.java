package com.zeuslu.esearch.controller;


import com.zeuslu.esearch.domain.dto.ProductBulkDto;
import com.zeuslu.esearch.domain.dto.ProductSearchDto;
import com.zeuslu.esearch.domain.vo.ProductAnalyticsVo;
import com.zeuslu.esearch.domain.vo.ProductSearchVo;
import com.zeuslu.esearch.service.ProductService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/bulk")
    public Boolean bulkIngestion(@NotNull @RequestBody ProductBulkDto productBulkDto) {
        return productService.bulkIngestion(productBulkDto);
    }

    @PostMapping("/search")
    public ProductSearchVo search(@NotNull @RequestBody ProductSearchDto productSearchDto) {
        return productService.search(productSearchDto);
    }

    @GetMapping("/analytics")
    public ProductAnalyticsVo analytics(@RequestParam(required = false, name = "keyword") String keyword) {
        return productService.analytics(keyword);
    }

}
