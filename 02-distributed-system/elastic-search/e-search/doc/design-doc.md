> Mapping 设计

由于本地环境只有一台单点的ES docker镜像实例, 因此 settings 只设计了

```json
"number_of_shards": 1,
"number_of_replicas": 0
```



title 需要支持一定的模糊容错, 可以在查询时通过fuzziness完成, 创建索引时额外增加ngram分词器会导致索引大幅膨胀, 因此不考虑

skus 直接扁平化存储(1 个 SKU = 1 个 ES 文档), 因为如果采用nested结构, 库存更新时会导致底层所有的nested 子文档被标记为删除后进行新建.

- 大型架构可以考虑将库存分离到redis中通过es获取sku_id, 然后再redis中查询库存
- 扁平化存储会导致搜索一个商品时出现多个sku列表, 可以使用 ES 的 **`collapse` (字段折叠)** 功能，在搜索时按 `product_id` 去重。



> 


















