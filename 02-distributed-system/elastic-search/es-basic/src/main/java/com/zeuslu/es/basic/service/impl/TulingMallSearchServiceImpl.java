package com.zeuslu.es.basic.service.impl;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptionsBuilders;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import co.elastic.clients.elasticsearch.core.search.*;
import co.elastic.clients.json.JsonData;
import com.zeuslu.es.basic.constant.SearchConstant;
import com.zeuslu.es.basic.entity.EsProduct;
import com.zeuslu.es.basic.entity.dto.ESRequestParam;
import com.zeuslu.es.basic.entity.dto.ESResponseResult;
import com.zeuslu.es.basic.service.TulingMallSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;


@Service(value = "tulingMallSearchService")
public class TulingMallSearchServiceImpl implements TulingMallSearchService {


    @Qualifier("elasticsearchClient")
    @Autowired
    ElasticsearchClient client;


    /**************************图灵商城搜索*****************************/
    @Override
    public ESResponseResult search(ESRequestParam param) {

        try {
            //1、构建检索对象-封装请求相关参数信息
            SearchRequest searchRequest = startBuildRequestParam(param);

            //2、进行检索操作
            SearchResponse response = client.search(searchRequest, EsProduct.class);
            System.out.println("response:" + response);
            //3、分析响应数据，封装成指定的格式
            ESResponseResult responseResult = startBuildResponseResult(response, param);
            return responseResult;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    /**
     * 封装请求参数信息
     * 关键字查询、根据属性、分类、品牌、价格区间、是否有库存等进行过滤、分页、高亮、以及聚合统计品牌分类属性
     * price=1_5000&keyword=手机&sort=salecount_asc&hasStock=1&pageNum=1&pageSize=20&categoryId=19&attrs=2_蓝色&attrs=1_2核
     */
    private SearchRequest startBuildRequestParam(ESRequestParam param) {

        //构建搜索请求
        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

        /**
         * 关键字查询、根据属性、分类、品牌、价格区间、是否有库存等进行过滤、分页、高亮、以及聚合统计品牌分类属性
         */

        //构建bool查询
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        //1、查询关键字
        if (!StringUtils.isEmpty(param.getKeyword())) {
            //单字段查询
//            boolQueryBuilder.must(QueryBuilders.match(
//                    m->m.field("name").query(param.getKeyword())
//            ));
            //多字段查询
            boolQueryBuilder.must(m->m.multiMatch(
                    q->q.fields("name", "keywords", "subTitle").query(param.getKeyword())
            ));
        }
        //2、根据类目ID进行过滤
        if (null != param.getCategoryId()) {
            boolQueryBuilder.filter(QueryBuilders.term(t -> t.field("categoryId").value(param.getCategoryId())));
        }

        //3、根据品牌ID进行过滤
        if (null != param.getBrandId() && !param.getBrandId().isEmpty()) {
            List<FieldValue> brandIds = param.getBrandId().stream().map(FieldValue::of).toList();
            boolQueryBuilder.filter(QueryBuilders.terms(t -> t.field("brandId").terms(v -> v.value(brandIds))));
        }

        //4、根据属性进行相关过滤
        if (param.getAttrs() != null && !param.getAttrs().isEmpty()) {
            param.getAttrs().forEach(item -> {
                //attrs=1_白色&2_4核
                BoolQuery.Builder boolQuery = QueryBuilders.bool();

                //attrs=1_64G
                String[] s = item.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");//这个属性检索用的值

                boolQuery.filter(QueryBuilders.term(t -> t.field("attrs.attrId").value(attrId)));

                List<FieldValue> attrValueList = Arrays.stream(attrValues).map(FieldValue::of).collect(Collectors.toList());
                boolQuery.filter(QueryBuilders.terms(t -> t.field("attrs.attrValue").terms(v -> v.value(attrValueList))));

                NestedQuery.Builder nestedQueryBuilder = new NestedQuery.Builder();
                //nested查询
                nestedQueryBuilder.path("attrs").query(q -> q.bool(boolQuery.build())).scoreMode(ChildScoreMode.None);

                boolQueryBuilder.filter(q -> q.nested(nestedQueryBuilder.build()));
            });

        }

        //5、是否有库存
        if (null != param.getHasStock()) {
            boolQueryBuilder.filter(QueryBuilders.term(t -> t.field("hasStock").value(param.getHasStock())));
        }


        //6、根据价格过滤
        if (!StringUtils.isEmpty(param.getPrice())) {
            //价格的输入形式为：10_100（起始价格和最终价格）或_100（不指定起始价格）或10_（不限制最终价格）
            RangeQuery.Builder rangeQueryBuilder = QueryBuilders.range().field("price");

            String[] price = param.getPrice().split("_");
            if (price.length == 2) {
                //price: _5000   [, 5000]
                if (param.getPrice().startsWith("_")) {
                    rangeQueryBuilder.lte(JsonData.of(price[1]));
                } else {
                    //price: 1_5000  [1, 5000]
                    rangeQueryBuilder.gte(JsonData.of(price[0])).lte(JsonData.of(price[1]));
                }

            } else if (price.length == 1) {
                //price: 1_     [1]
                if (param.getPrice().endsWith("_")) {
                    rangeQueryBuilder.gte(JsonData.of(price[0]));
                }
            }
            boolQueryBuilder.filter(r -> r.range(rangeQueryBuilder.build()));
        }

        //封装所有查询条件
        searchRequestBuilder.query(q -> q.bool(boolQueryBuilder.build()));


        /**
         * 实现排序、高亮、分页操作
         */

        //排序
        //页面传入的参数值形式 sort=price_asc/desc
        if (!StringUtils.isEmpty(param.getSort())) {
            String sort = param.getSort();
            String[] sortFileds = sort.split("_");

            if (!StringUtils.isEmpty(sortFileds[0])) {
                SortOrder sortOrder = "asc".equalsIgnoreCase(sortFileds[1]) ? SortOrder.Asc : SortOrder.Desc;

                //排序
                FieldSort fieldSort = SortOptionsBuilders.field().field(sortFileds[0]).order(sortOrder).build();
                searchRequestBuilder.sort(s -> s.field(fieldSort));
            }
        }


        //分页查询
        searchRequestBuilder.from((param.getPageNum() - 1) * SearchConstant.PAGE_SIZE);
        searchRequestBuilder.size(SearchConstant.PAGE_SIZE);

        //高亮显示
        if (!StringUtils.isEmpty(param.getKeyword())) {
            HighlightField highlightField = new HighlightField.Builder().preTags("<b style='color:red'>").postTags("</b>").build();
            searchRequestBuilder.highlight(h -> h.fields("name", highlightField));
        }


        /**
         * 对品牌、分类信息、属性信息进行聚合分析
         */
        //1. 按照品牌进行聚合
        //1.1 品牌的子聚合-品牌名聚合
        Aggregation brand_name_agg = AggregationBuilders.terms(t -> t.field("brandName").size(1));
        //1.2 品牌的子聚合-品牌图片聚合
        Aggregation brand_img_agg = AggregationBuilders.terms(t -> t.field("brandImg").size(1));

        Aggregation brand_agg = new Aggregation.Builder()
                //按照品牌id进行聚合
                .terms(t -> t.field("brandId").size(50)).aggregations("brand_name_agg", brand_name_agg).aggregations("brand_img_agg", brand_img_agg).build();
        searchRequestBuilder.aggregations("brand_agg", brand_agg);

        //2. 按照分类信息进行聚合
        Aggregation category_agg = new Aggregation.Builder().terms(t -> t.field("categoryId").size(50)).aggregations("category_name_agg", AggregationBuilders.terms(t -> t.field("categoryName").size(1))).build();
        searchRequestBuilder.aggregations("category_agg", category_agg);


        //2. 按照属性信息进行聚合
        NestedAggregation attrs = new NestedAggregation.Builder().path("attrs").build();

        Aggregation attr_id_agg = new Aggregation.Builder()
                //2.1 按照属性ID进行聚合
                .terms(t -> t.field("attrs.attrId"))
                //2.1.1 在每个属性ID下，按照属性名进行聚合
                .aggregations("attr_name_agg", AggregationBuilders.terms(t -> t.field("attrs.attrName").size(1)))
                //2.1.1 在每个属性ID下，按照属性值进行聚合
                .aggregations("attr_value_agg", AggregationBuilders.terms(t -> t.field("attrs.attrValue").size(1))).build();

        Aggregation attrs_agg = new Aggregation.Builder().nested(attrs).aggregations("attr_id_agg", attr_id_agg).build();

        searchRequestBuilder.aggregations("attrs_agg", attrs_agg);

        System.out.println("构建的DSL语句:" + searchRequestBuilder.toString());


        SearchRequest searchRequest = searchRequestBuilder.index(SearchConstant.INDEX_NAME).build();

        return searchRequest;
    }


    /**
     * 封装查询到的结果信息
     * 关键字查询、根据属性、分类、品牌、价格区间、是否有库存等进行过滤、分页、高亮、以及聚合统计品牌分类属性
     */
    private ESResponseResult startBuildResponseResult(SearchResponse response, ESRequestParam param) {
        //构建返回结果
        ESResponseResult result = new ESResponseResult();

        //1、获取查询到的商品信息
        HitsMetadata<EsProduct> hitsMetadata = response.hits();
        List<Hit<EsProduct>> hits = hitsMetadata.hits();

        List<EsProduct> esProducts = new ArrayList<>();
        //2、遍历所有商品信息
        if (!hits.isEmpty()) {
            for (Hit<EsProduct> hit : hits) {
                EsProduct product = hit.source();

                //2.1 判断是否按关键字检索，若是就显示高亮，否则不显示
                if (!StringUtils.isEmpty(param.getKeyword())) {
                    //2.2 拿到高亮信息显示标题
                    List<String> name = hit.highlight().get("name");
                    //2.3 判断name中是否含有查询的关键字(因为是多字段查询，因此可能不包含指定的关键字，假设不包含则显示原始name字段的信息)
                    String nameValue = name != null ? name.get(0) : product.getName();
                    product.setName(nameValue);
                }
                esProducts.add(product);

            }
        }
        result.setEsProducts(esProducts);

        //3、当前商品涉及到的所有品牌信息，小米手机和小米电脑都属于小米品牌，过滤重复品牌信息
        List<ESResponseResult.BrandVo> brandVos = new ArrayList<>();

        // 获取聚合结果
        Map<String, Aggregate> aggs = response.aggregations();
        //获取到品牌的聚合
        Aggregate brandAgg = aggs.get("brand_agg");
        if (brandAgg != null) {
            List<LongTermsBucket> brandIdBuckets = brandAgg.lterms().buckets().array();
            for (LongTermsBucket brandIdBucket : brandIdBuckets) {
                //构建品牌信息
                ESResponseResult.BrandVo brandVo = new ESResponseResult.BrandVo();
                //设置品牌ID
                brandVo.setBrandId(brandIdBucket.key());

                Aggregate brandImgAgg = brandIdBucket.aggregations().get("brand_img_agg");
                Aggregate brandNameAgg = brandIdBucket.aggregations().get("brand_name_agg");
                if (brandImgAgg != null && brandNameAgg != null) {
                    StringTermsBucket imgBucket = brandImgAgg.sterms().buckets().array().get(0);
                    StringTermsBucket nameBucket = brandNameAgg.sterms().buckets().array().get(0);
                    //设置品牌的图片和名称
                    brandVo.setBrandImg(imgBucket.key().stringValue());
                    brandVo.setBrandName(nameBucket.key().stringValue());
                }
                brandVos.add(brandVo);
            }
        }
        result.setBrandVos(brandVos);


        //4、当前商品相关的所有类目信息
        //获取到分类的聚合
        List<ESResponseResult.CategoryVo> categoryVos = new ArrayList<>();

        Aggregate categoryAgg = aggs.get("category_agg");
        if (categoryAgg != null) {
            List<LongTermsBucket> categoryBuckets = categoryAgg.lterms().buckets().array();
            for (LongTermsBucket categoryBucket : categoryBuckets) {
                //构建分类信息
                ESResponseResult.CategoryVo categoryVo = new ESResponseResult.CategoryVo();
                //设置分类ID
                categoryVo.setCategoryId(categoryBucket.key());

                Aggregate categoryNameAgg = categoryBucket.aggregations().get("category_name_agg");
                if (categoryNameAgg != null) {
                    StringTermsBucket nameBucket = categoryNameAgg.sterms().buckets().array().get(0);
                    //设置分类名称
                    categoryVo.setCategoryName(nameBucket.key().stringValue());
                }
                categoryVos.add(categoryVo);
            }
        }
        result.setCategoryVos(categoryVos);


        //5、获取商品相关的所有属性信息
        List<ESResponseResult.AttrVo> attrVos = new ArrayList<>();
        //获取属性信息的聚合
        Aggregate attrsAgg = aggs.get("attrs_agg");
        if (attrsAgg != null) {
            //获取属性id的集合
            Aggregate attrIdAgg = attrsAgg.nested().aggregations().get("attr_id_agg");
            List<LongTermsBucket> attrBuckets = attrIdAgg.lterms().buckets().array();
            for (LongTermsBucket attrBucket : attrBuckets) {
                //构建属性信息
                ESResponseResult.AttrVo attrVo = new ESResponseResult.AttrVo();
                //设置属性ID
                attrVo.setAttrId(attrBucket.key());

                Aggregate attrNameAgg = attrBucket.aggregations().get("attr_name_agg");
                Aggregate attrValueAgg = attrBucket.aggregations().get("attr_value_agg");
                if (attrNameAgg != null && attrValueAgg != null) {
                    StringTermsBucket attrNameBucket = attrNameAgg.sterms().buckets().array().get(0);
                    //设置属性名称
                    attrVo.setAttrName(attrNameBucket.key().stringValue());

                    List<StringTermsBucket> attrValueBuckets = attrValueAgg.sterms().buckets().array();
                    List<String> attrValues = new ArrayList<>();
                    for (StringTermsBucket attrValueBucket : attrValueBuckets) {
                        attrValues.add(attrValueBucket.key().stringValue());
                    }
                    //设置属性值
                    attrVo.setAttrValue(attrValues);
                }
                attrVos.add(attrVo);
            }
        }
        result.setAttrVos(attrVos);

        //6、进行分页操作
        result.setPageNum(param.getPageNum());
        //获取总记录数
        long total = hitsMetadata.total().value();
        result.setTotal(total);

        //计算总页码
        int totalPages = (int) total % SearchConstant.PAGE_SIZE == 0 ? (int) total / SearchConstant.PAGE_SIZE : ((int) total / SearchConstant.PAGE_SIZE + 1);
        result.setTotalPages(totalPages);

        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNavs.add(i);
        }
        result.setPageNavs(pageNavs);
        return result;
    }
}



