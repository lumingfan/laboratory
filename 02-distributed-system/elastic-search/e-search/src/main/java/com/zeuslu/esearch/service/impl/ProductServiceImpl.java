package com.zeuslu.esearch.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.zeuslu.esearch.constant.ProductConstant;
import com.zeuslu.esearch.domain.dto.ProductBulkDto;
import com.zeuslu.esearch.domain.dto.ProductSearchDto;
import com.zeuslu.esearch.domain.dto.ProductSkuDto;
import com.zeuslu.esearch.domain.entity.Product;
import com.zeuslu.esearch.domain.vo.ProductAnalyticsVo;
import com.zeuslu.esearch.domain.vo.ProductSearchVo;
import com.zeuslu.esearch.domain.vo.ProductSkuVo;
import com.zeuslu.esearch.domain.vo.ProductVo;
import com.zeuslu.esearch.service.ProductService;
import com.zeuslu.esearch.util.QueryUtil;
import com.zeuslu.esearch.util.RetryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ElasticsearchTemplate elasticsearchTemplate;
    private final ElasticsearchClient elasticsearchClient;
    private final RetryTemplate retryTemplate;

    @Override
    public Boolean bulkIngestion(ProductBulkDto productBulkDto) {
        if (CollectionUtils.isEmpty(productBulkDto.getProductDtoList())) {
            return true;
        }

        try {
            return bulkIngestionRetry(productBulkDto);
        } catch (Exception e) {
            return false;
        }
    }

    private BulkRequest.Builder buildBulkRequest(ProductBulkDto productBulkDto) {
        List<Product> products = new ArrayList<>();
        productBulkDto.getProductDtoList().forEach(productDto -> {
            List<ProductSkuDto> productSkuDtoList = productDto.getProductSkuDtoList();
            List<Product> productSkuList = new ArrayList<>();
            productSkuDtoList.forEach(productSkuDto -> {
                Product product = new Product();
                BeanUtils.copyProperties(productDto, product);
                product.setColor(productSkuDto.getColor());
                product.setSize(productSkuDto.getSize());
                product.setStock(productSkuDto.getStock());
                productSkuList.add(product);
            });
            products.addAll(productSkuList);
        });
        BulkRequest.Builder builder = new BulkRequest.Builder();
        products.forEach(product -> builder
                .operations(op -> op.index(
                        idx -> idx.index("products").id(product.getId()).document(product)
                ))
        );
        return builder;
    }

    private Boolean bulkIngestionRetry(ProductBulkDto productBulkDto) throws Exception {
        retryTemplate.execute(
                (RetryCallback <Void, Exception>) context -> {
                    try {
                        BulkRequest.Builder builder = buildBulkRequest(productBulkDto);
                        BulkResponse response = elasticsearchClient.bulk(builder.build());
                        // 没有错误, 终止重试
                        if (!response.errors()) {
                            return null;
                        }
                        // 有错误, 继续重试, 此处直接采用es的幂等性, 不进行复杂的提取错误项进行重试, 直接重试整个批次
                        throw new IOException("批量写入失败，存在错误项");
                    } catch (Exception e) {
                        if (RetryUtil.isRetryableError(e)) {
                            log.warn("重试第 {} 次，异常：{}", context.getRetryCount(), e.getMessage());
                            throw e; // 继续重试
                        } else {
                            log.error("非重试异常，终止重试", e);
                            throw new RuntimeException(e); // 终止重试
                        }
                    }
                },
                // 3. 恢复回调（重试耗尽后执行）
                (RecoveryCallback<Void>) context -> {
                    log.error("重试耗尽，进入兜底逻辑", context.getLastThrowable());
                    // 这里可以放入消息队列中, 或者记录到数据库中, 以便后续人工干预
                    return null;
                }
        );
        return true;
    }


    @Override
    public ProductSearchVo search(ProductSearchDto productSearchDto) {
        String keyword = productSearchDto.getKeyword();
        String brand = productSearchDto.getBrand();
        Integer minPrice = productSearchDto.getMinPrice();
        Integer maxPrice = productSearchDto.getMaxPrice();
        List<ProductSkuDto> productSkuDtoList = productSearchDto.getProductSkuDtoList();

        BoolQuery boolQuery = new BoolQuery.Builder().filter(
                        QueryUtil.addTermIfPresent(ProductConstant.BRAND, brand),
                        QueryUtil.addRangeIfPresent(ProductConstant.PRICE, minPrice, maxPrice),
                        QueryUtil.addTermIfPresent(ProductConstant.STATUS, ProductConstant.STATUS_AVAILABLE),
                        Query.of(q -> q.bool(b -> b.should(
                                        productSkuDtoList.stream().map(
                                                productSkuDto -> Query.of(sq -> sq.bool(
                                                        sqb -> sqb.filter(
                                                                QueryUtil.addTermIfPresent(ProductConstant.COLOR, productSkuDto.getColor()),
                                                                QueryUtil.addTermIfPresent(ProductConstant.SIZE, productSkuDto.getSize()),
                                                                QueryUtil.addRangeIfPresent(ProductConstant.STOCK, 1, null)
                                                        )
                                                ))).toList()
                                )
                        ))
                ).must(m -> m.functionScore(
                        fs -> fs.query(QueryUtil.addMatchIfPresent(ProductConstant.TITLE, keyword)).functions (
                                fsf -> fsf.fieldValueFactor(fsff -> fsff.field(ProductConstant.SALES_VOLUME).modifier(FieldValueFactorModifier.Log1p).missing(1.0))
                        )
                )).build();

        Query query = Query.of(q -> q.bool(boolQuery));



        FieldCollapse fieldCollapse = FieldCollapse.of(
                fc -> fc.field(ProductConstant.PRODUCT_ID).innerHits(
                        fci -> fci.name(ProductConstant.COLLAPSE_HIT_NAME)
                                .size(ProductConstant.COLLAPSE_HIT_SIZE)
                                .sort(s -> s.field(sf -> sf.field(ProductConstant.PRICE).order(SortOrder.Asc)))
                                .source(SourceConfig.of(sc -> sc.filter(scf -> scf.includes(ProductConstant.COLOR, ProductConstant.SIZE, ProductConstant.STOCK))))
                )
        );

        String queryString = JsonpUtils.toJsonString(query, new JacksonJsonpMapper());
        String collapseString = JsonpUtils.toJsonString(fieldCollapse, new JacksonJsonpMapper());
        log.info("构造的Query字符串: {}, 构造的collapse字符串: {}", queryString, collapseString);

        NativeQuery nativeQuery = NativeQuery.builder().withQuery(query).withFieldCollapse(fieldCollapse).build();
        SearchHits<Product> results = elasticsearchTemplate.search(nativeQuery, Product.class);

        List<ProductVo> productVoList = new ArrayList<>();
        results.stream().forEach(productSearchHit -> {
            ProductVo productVo = new ProductVo();
            Product content = productSearchHit.getContent();
            BeanUtils.copyProperties(content, productVo);

            List<ProductSkuVo> productSkuVoList = new ArrayList<>();
            productSkuVoList.add(new ProductSkuVo(content.getColor(), content.getSize(), content.getStock()));
            SearchHits<?> searchHits = productSearchHit.getInnerHits().get(ProductConstant.COLLAPSE_HIT_NAME);
            if (searchHits != null) {
                searchHits.getSearchHits().forEach(innerHit -> {
                            Object innerHitContent = innerHit.getContent();
                            if (innerHitContent instanceof Map innerHitMap) {
                                String color = (String) innerHitMap.get(ProductConstant.COLOR);
                                String size = (String) innerHitMap.get(ProductConstant.SIZE);
                                Integer stock = (Integer) innerHitMap.get(ProductConstant.STOCK);
                                productSkuVoList.add(new ProductSkuVo(color, size, stock));
                            }
                        }
                );
            }
            productVo.setProductSkuVoList(productSkuVoList);
            productVoList.add(productVo);
        });
        return new ProductSearchVo(productVoList);
    }

    @Override
    public ProductAnalyticsVo analytics(String keyword) {
        return null;
    }
}
