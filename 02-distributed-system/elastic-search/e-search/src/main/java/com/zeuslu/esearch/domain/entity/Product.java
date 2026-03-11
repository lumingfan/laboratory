package com.zeuslu.esearch.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
/**
 * 采用SKU为基础存储的Product文档
 */
public class Product {
    /**
     * ID, 唯一标识一个SKU-Product文档
     */
    @Id
    private String id;
    /**
     * 商品ID，唯一标识一个商品
     */
    @Field(type = FieldType.Keyword)
    private String productId;
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;
    @Field(type = FieldType.Keyword)
    private String brand;
    @Field(type = FieldType.Long)
    private Long price;
    @Field(type = FieldType.Integer)
    private Integer salesVolume;
    @Field(type = FieldType.Boolean)
    private Boolean status;
    @Field(type = FieldType.Keyword)
    private String color;
    @Field(type = FieldType.Keyword)
    private String size;
    @Field(type = FieldType.Integer)
    private Integer stock;
}
