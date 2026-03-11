# 📄[Design Doc] 高并发电商商品检索与分析系统 (Project E-Search)

## 1. 背景与目标 (Context & Objective)
我们正在重构内部代号为 "E-Search" 的电商核心商品搜索引擎。旧系统直接查 MySQL，遇到高并发和复杂多条件组合查询时直接宕机。
**目标：** 基于 Java 和 Elasticsearch 构建一个高性能、高可用的商品检索微服务。要求支持海量商品的复杂过滤、基于业务规则的相关性算分（不仅仅是文本匹配），以及实时的多维聚合分析。

## 2. 技术栈约束 (Tech Stack Constraints)
作为 G-Arch，我强制要求你使用现代化的技术栈，抛弃那些已经被官方废弃的旧玩具：
*   **语言框架：** Java 17+ & Spring Boot 3.x
*   **ES 客户端：** **强制使用全新的 `Elasticsearch Java API Client`** (`co.elastic.clients:elasticsearch-java`)。**绝对不允许**使用已经被彻底废弃的 `RestHighLevelClient`。
*   **基础设施：** 本地使用 Docker 运行单节点或伪集群 ES (8.x 版本)。

## 3. 数据模型设计 (Data Modeling)
你需要为 `products` 索引设计一个 Production-ready 的 Mapping。一个 `Product` 包含以下基本结构，你必须将之前面试中讨论的理论落地：

*   `id` (唯一标识)
*   `title` (商品标题，需支持中/英文分词，支持一定的模糊容错)
*   `brand` (品牌名，用于精确过滤和聚合)
*   `price` (基础价格)
*   `sales_volume` (历史销量，用于算分干预)
*   `status` (状态：ACTIVE, INACTIVE)
*   `skus` (**关键：包含多个 SKU 的列表**，如颜色、尺码、库存。必须解决我们在 L1 讨论过的跨字段匹配陷阱！如果库存为 0，该 SKU 不应被检索到。)
*   `tags` (商品标签，如 "新品", "包邮")

## 4. 核心 API 契约 (Core APIs to Implement)

你需要实现以下三个核心的 RESTful (或内部 RPC 类) 接口的具体逻辑：

### API 1: 容错批量写入 (Resilient Bulk Ingestion)
*   **Endpoint:** `POST /api/v1/products/bulk`
*   **要求：** 接收一个商品列表，使用 ES 的 Bulk API 进行批量写入。
*   **SRE 考量：** 必须实现**重试机制（Retry）**。如果 ES 集群瞬间抖动返回 `429 Too Many Requests` (线程池满)，你的代码怎么处理？（不能直接抛弃数据，也不能死循环重试打挂 ES，需要 Exponential Backoff）。

### API 2: 复杂电商检索 (Complex Search & Scoring)
*   **Endpoint:** `POST /api/v1/products/search`
*   **参数：** 关键词 (keyword), 品牌过滤 (brand), 价格区间 (min_price, max_price), SKU 属性 (如：color=Red, size=XL)。
*   **要求：**
    1.  **查询上下文分离：** 品牌、价格、SKU 属性、状态(必须为 ACTIVE) 必须放在 `Filter Context` 中以利用缓存。关键词搜索放在 `Query Context` 中。
    2.  **Nested 查询：** 正确实现对 `skus` 的查询。
    3.  **算分干预 (Function Score)：** 搜索结果不能仅仅依赖 BM25。业务要求：**“销量（sales_volume）越高的商品，在文本相关性相近的情况下，排名必须越靠前。”** 你需要用代码实现这个干预逻辑。

### API 3: 实时多维聚合分析 (Real-time Analytics)
*   **Endpoint:** `GET /api/v1/products/analytics`
*   **参数：** 关键词 (可选)
*   **要求：**
    1.  统计匹配该关键词的商品中，**各个品牌 (brand) 的商品数量分布** (Terms Aggregation)。
    2.  统计匹配该关键词的商品中，**价格分布直方图** (Histogram Aggregation，例如每 100 元一个区间，返回每个区间的商品数)。