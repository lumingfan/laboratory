> Mapping 设计

由于本地环境只有一台单点的ES docker镜像实例, 因此 settings 只设计了

```json
"number_of_shards": 1,
"number_of_replicas": 0
```



title 需要支持一定的模糊容错, 可以在查询时通过fuzziness完成, 创建索引时额外增加ngram分词器会导致索引大幅膨胀, 因此不考虑

skus 直接扁平化存储(1 个 SKU = 1 个 ES 文档), 因为如果采用nested结构, 库存更新时会导致底层所有的nested 子文档被标记为删除后进行新建.

- 大型架构可以考虑将库存分离到redis中通过es获取sku_id, 然后再redis中查询库存
- 扁平化存储会导致搜索一个商品时出现多个sku列表, 需要可以通过collapse对product_id进行去重
  - 需要新增`product_id` 字段

完整设计:

```elastic
PUT /products
{
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "mappings": {
      "properties": {
        "id": {
          "type": "keyword"
        },
        "product_id": {
          "type": "keyword"
        },
        "title": {
          "type": "text",
          "analyzer": "ik_max_word",
          "search_analyzer": "ik_smart"
        },
        "brand": {
          "type": "keyword"
        },
        "price": {
          "type": "long"
        },
        "sales_volume": {
          "type": "integer"
        },
        "status": {
          "type": "boolean"
        },
        "color": {
          "type": "keyword"
        },
        "size": {
          "type": "keyword"
        },
        "stock": {
          "type": "integer"
        }

      }
    }
}
```







> 容错批量写入

重试策略:  Exponential Backoff + Jitter + Max Retries

- EB 为了给故障系统恢复的时间, 防止瞬时流量压垮下游服务导致雪崩
- Jitter 防止多个客户端同时重试造成惊群效应
- Max Retries 设置为3次, 超过次数后进入失败处理流程
- 重试只在SocketTimeoutException, 429 Too Many Requests等异常时进行, 对于`400 Bad Request`, 认证失败这些错误不进行重试



重试架构:

采用Spring 同步本地重试(Resillience4j/Spring Retry), 不使用MQ异步重试或者持久化表+定时任务重试

- 在真实场景一般采用MQ异步重试
- 金融场景采用持久化表+定时任务

幂等性:

- 写入 ES 时不要让它自动生成 ID，而是使用业务唯一 ID 作为 ES Document ID。ES 的 `Index` 操作天然支持覆盖（Put 语义）。
- 对于bulk失败, 解析 Bulk Response，找出 `hasFailed()` 的 item。只对这部分item重新封装并重试



关键代码:

通过RetryConfig配置RetryTemplate支持自定义最大重试次数, 自定义回滚异常和指数退避策略

- 需要自定义不回滚异常是因为对于es的bulk操作, 即使部分文档操作失败并不会抛出异常, 需要自行检查BulkResponse, 如果操作失败, 我们主动抛出RetryableException异常让Spring Retry重试, 重试过程中如果完成操作/非重试异常, 则停止重试
- 此处为了代码简洁, 没有进行复杂的错误项提取, 而是整个批次重试, 在正确性上不会存在问题, 因为我们需要传入id, index的幂等性进行了保证(实际业务中应该提取错误项进行重试减轻网络压力)
- 同样对于重试兜底, 实际开发中应该存入消息队列或者数据库中人工干预

```java
 @Retryable(retryFor = {RetryableException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    private Boolean bulkIngestionRetry(ProductBulkDto productBulkDto) throws Exception {
        try {
            BulkRequest.Builder builder = buildBulkRequest(productBulkDto);
            BulkResponse response = elasticsearchClient.bulk(builder.build());
            // 没有错误, 终止重试
            if (!response.errors()) {
                return true;
            }
            // 有错误, 继续重试, 此处直接采用es的幂等性, 不进行复杂的提取错误项进行重试, 直接重试整个批次
            throw new RetryableException();
        } catch (Exception e) {
            if (RetryUtil.isRetryableError(e)) {
                log.warn("重试，异常：{}", e.getMessage());
                throw new RetryableException(); 
            } else {
                log.error("非重试异常，终止重试", e);
                throw e; 
            }
        }
    }

@Recover
    public void bulkIngestionRecover(RetryableException e) {
        log.error("重试耗尽，进入兜底逻辑", e);
        // 这里可以放入消息队列中, 或者记录到数据库中, 以便后续人工干预
    }

```



> 复杂电商检索+多维聚合分析

由于一般传入的字段是可选的, 因此为了简化代码建议写一份工具类实现下面的功能

```java
   public static Query addTermIfPresent(String field, Object value) {
        if (value != null) {
            return Query.of(builder -> builder.term(t -> t.field(field).value(FieldValue.of(value))));
        }
        return null;
   }
```

注意must等方法不接受null 输入(但接收空列表), 因此还需要一个工具方法:

```java
   public static void addQueryIfPresent(List<Query> queryList, Query query) {
        if (query != null && queryList != null) {
            queryList.add(query);
        }
    }
```

搜索和聚合主要是API调用, 没太多可说的:

```java
    @Override
    public ProductSearchVo search(ProductSearchDto productSearchDto) {
        String keyword = productSearchDto.getKeyword();
        String brand = productSearchDto.getBrand();
        Integer minPrice = productSearchDto.getMinPrice();
        Integer maxPrice = productSearchDto.getMaxPrice();
        List<ProductSkuDto> productSkuDtoList = productSearchDto.getProductSkuDtoList();

        List<Query> shouldQueryList = productSkuDtoList.stream().map(
                productSkuDto ->  {
                    List<Query> shouldFilterQuery = new ArrayList<>();
                    QueryUtil.addQueryIfPresent(shouldFilterQuery, QueryUtil.addTermIfPresent(ProductConstant.COLOR, productSkuDto.getColor()));
                    QueryUtil.addQueryIfPresent(shouldFilterQuery, QueryUtil.addTermIfPresent(ProductConstant.SIZE, productSkuDto.getSize()));
                    QueryUtil.addQueryIfPresent(shouldFilterQuery, QueryUtil.addRangeIfPresent(ProductConstant.STOCK, 1, null));
                    return Query.of(sq -> sq.bool(sqb -> sqb.filter(shouldFilterQuery)));
                }
        ).toList();

        List<Query> filterQueryList = new ArrayList<>();
        QueryUtil.addQueryIfPresent(filterQueryList, QueryUtil.addTermIfPresent(ProductConstant.BRAND, brand));
        QueryUtil.addQueryIfPresent(filterQueryList, QueryUtil.addRangeIfPresent(ProductConstant.PRICE, minPrice, maxPrice));
        QueryUtil.addQueryIfPresent(filterQueryList, QueryUtil.addTermIfPresent(ProductConstant.STATUS, ProductConstant.STATUS_AVAILABLE));
        QueryUtil.addQueryIfPresent(filterQueryList, Query.of(q -> q.bool(b -> b.should(shouldQueryList))));

        List<Query> mustQueryList = new ArrayList<>();
        Query titleQuery = QueryUtil.addMatchIfPresent(ProductConstant.TITLE, keyword);
        if (titleQuery != null) {
            Function<FunctionScore.Builder, ObjectBuilder<FunctionScore>> functionsBuilder = fsf -> fsf.fieldValueFactor(fsff -> fsff
                    .field(ProductConstant.SALES_VOLUME)
                    .modifier(FieldValueFactorModifier.Log1p)
                    .missing(1.0)
            );
            Query functionScoreQuery = Query.of(
                    q -> q.functionScore(fs -> fs.query(titleQuery).functions(functionsBuilder))
            );
            mustQueryList.add(functionScoreQuery);
        }

        BoolQuery boolQuery = new BoolQuery.Builder().filter(filterQueryList).must(mustQueryList).build();
        Query query = Query.of(q -> q.bool(boolQuery));

        Function<InnerHits.Builder, ObjectBuilder<InnerHits>> innerHitsBuilder =
                fci -> fci.name(ProductConstant.COLLAPSE_HIT_NAME)
                        .size(ProductConstant.COLLAPSE_HIT_SIZE)
                        .sort(s -> s.field(sf -> sf.field(ProductConstant.PRICE).order(SortOrder.Asc)))
                        .source(SourceConfig.of(sc -> sc.filter(scf -> scf.includes(ProductConstant.COLOR, ProductConstant.SIZE, ProductConstant.STOCK))));

        FieldCollapse fieldCollapse = FieldCollapse.of(
                fc -> fc.field(ProductConstant.PRODUCT_ID).innerHits(innerHitsBuilder)
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
        List<Query> mustQueryList = new ArrayList<>();
        QueryUtil.addQueryIfPresent(mustQueryList, QueryUtil.addMatchIfPresent(ProductConstant.TITLE, keyword));
        Query query = Query.of(q -> q.bool(new BoolQuery.Builder().must(mustQueryList).build()));
        Aggregation brandAgg = Aggregation.of(agg -> agg.terms(t -> t.field(ProductConstant.BRAND)));
        Aggregation priceAgg = Aggregation.of(agg -> agg.histogram(t -> t.field(ProductConstant.PRICE).interval(ProductConstant.AGG_PRICE_INTERVAL)));

        log.info("构造的Query字符串: {}, 构造的brandAgg字符串: {}, 构造的priceAgg字符串: {}",
                JsonpUtils.toJsonString(query, new JacksonJsonpMapper()),
                JsonpUtils.toJsonString(brandAgg, new JacksonJsonpMapper()),
                JsonpUtils.toJsonString(priceAgg, new JacksonJsonpMapper()));


        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withAggregation(ProductConstant.AGG_BRAND_DISTRIBUTION, brandAgg)
                .withAggregation(ProductConstant.AGG_PRICE_DISTRIBUTION, priceAgg).build();

        SearchHits<Product> results = elasticsearchTemplate.search(
                nativeQuery, Product.class);

        ProductAnalyticsVo productAnalyticsVo = new ProductAnalyticsVo();
        productAnalyticsVo.setBrandDistribution(new HashMap<>());
        productAnalyticsVo.setPriceDistribution(new HashMap<>());

        AggregationsContainer<?> container = results.getAggregations();
        if (container != null && container.aggregations() instanceof List aggregations) {
            aggregations.stream().forEach(aggregation -> {
                if (aggregation instanceof ElasticsearchAggregation esAgg) {
                    if (ProductConstant.AGG_BRAND_DISTRIBUTION.equals(esAgg.aggregation().getName())) {
                        Aggregate termAgg = esAgg.aggregation().getAggregate();
                        if (termAgg.isSterms()) {
                            termAgg.sterms().buckets().array().forEach(bucket -> {
                                String brand = bucket.key().stringValue();
                                long count = bucket.docCount();
                                productAnalyticsVo.getBrandDistribution().put(brand, (int) count);
                            });
                        }
                    } else if (ProductConstant.AGG_PRICE_DISTRIBUTION.equals(esAgg.aggregation().getName())) {
                        Aggregate histoAgg = esAgg.aggregation().getAggregate();
                        if (histoAgg.isHistogram()) {
                            histoAgg.histogram().buckets().array().forEach(bucket -> {
                                double key = bucket.key();
                                long count = bucket.docCount();
                                productAnalyticsVo.getPriceDistribution().put(key, (int) count);
                            });
                        }
                    }
                }
            });
        }
        return productAnalyticsVo;
    }}
```

































