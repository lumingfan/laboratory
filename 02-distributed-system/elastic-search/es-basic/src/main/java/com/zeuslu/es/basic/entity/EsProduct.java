package com.zeuslu.es.basic.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(indexName = "product_db")
public class EsProduct {
    @Id
    private String id;
    @Field(type = FieldType.Text, analyzer = "ik_product")
    private String name;
    @Field(type = FieldType.Text, analyzer = "ik_product")
    private String keywords;
    @Field(type = FieldType.Text, analyzer = "ik_product")
    private String subTitle;
    private Long price;
    private Long promotionPrice;
    private Long originalPrice;
    @Field(type = FieldType.Keyword, index = false)
    private String pic;
    @Field(type = FieldType.Keyword, index = false)
    private String brandImg;
    private Integer sale;
    private Integer salecount;
    private Boolean hasStock;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonSerialize(using = LocalDateSerializer.class)
    private LocalDate putawayDate;

    private Long brandId;
    @Field(type = FieldType.Keyword)
    private String brandName;
    private Long categoryId;
    @Field(type = FieldType.Keyword)
    private String categoryName;


    @Field(type = FieldType.Nested)
    private List<EsProductAttr> attrs;
}
