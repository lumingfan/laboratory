#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from elasticsearch import Elasticsearch, helpers
from faker import Faker
import random
import time
import hashlib
from tqdm import tqdm

# ================= 配置区域 =================
ES_HOST = "http://localhost:9200"
INDEX_NAME = "products"
BULK_SIZE = 500
TOTAL_DOCS = 10000

# 数据枚举
COLORS = ["red", "blue"]
SIZES = ["S", "M", "L"]
BRANDS = ["Nike", "Adidas", "Uniqlo", "ZARA", "H&M", "LiNing", "Anta"]
STATUS_OPTIONS = [True, False]

# 👇 决定商品种类的核心三要素
TITLE_TEMPLATES = [
    "夏季新款T恤{}",
    "时尚短袖上衣{}",
    "休闲衣服{}",
    "潮流服装{}",
    "经典T恤衫{}",
]
SUFFIXES = ["纯棉", "透气", "舒适", "宽松", "修身"]  # 👈 提取为全局常量

# ================= 初始化 =================
fake = Faker("zh_CN")
es = Elasticsearch([ES_HOST])

# ================= 核心：生成稳定的 product_id =================
def generate_product_id(brand, template, suffix):
    """
    根据 brand + title_template + suffix 生成唯一且稳定的 product_id
    相同三要素组合 → 永远返回相同 ID
    """
    key = f"{brand}:{template}:{suffix}"
    hash_hex = hashlib.md5(key.encode('utf-8')).hexdigest()
    return hash_hex[:8]  # 8位十六进制，足够唯一且简短

# ================= 数据生成函数 =================
def generate_title(template, suffix):
    """根据模板和后缀生成完整标题"""
    return template.format(suffix)

def generate_product_doc(doc_id):
    # 👇 先确定决定商品种类的核心三要素
    brand = random.choice(BRANDS)
    template = random.choice(TITLE_TEMPLATES)
    suffix = random.choice(SUFFIXES)

    # 👇 基于三要素生成稳定的 product_id（175种组合 → 175个唯一ID）
    product_id = generate_product_id(brand, template, suffix)

    # 👇 其他随机属性（不影响商品种类）
    color = random.choice(COLORS)
    size = random.choice(SIZES)

    return {
        "_index": INDEX_NAME,
        "_id": str(doc_id),  # ES 文档唯一ID（自增流水号）
        "_source": {
            "id": str(doc_id),              # 业务流水ID（每条记录唯一）
            "productId": product_id,       # 👈 核心：商品种类ID（175种）
            "title": generate_title(template, suffix),  # 标题由 template+suffix 决定
            "brand": brand,
            "price": random.randint(59, 999),
            "sales_volume": random.randint(0, 10000),
            "status": random.choice(STATUS_OPTIONS),
            "color": color,      # 随机属性，不影响 product_id
            "size": size,        # 随机属性，不影响 product_id
            "stock": random.randint(0, 500)
        }
    }

# ================= 主函数 =================
def main():
    print(f"🔌 连接 Elasticsearch: {ES_HOST}")
    if not es.ping():
        print("❌ 无法连接 Elasticsearch，请检查服务是否启动")
        return

    if not es.indices.exists(index=INDEX_NAME):
        print(f"⚠️  索引 '{INDEX_NAME}' 不存在，请先创建 mapping")
        return

    # 👇 计算理论唯一商品种类数
    unique_count = len(BRANDS) * len(TITLE_TEMPLATES) * len(SUFFIXES)
    print(f"🚀 开始生成并导入 {TOTAL_DOCS} 条测试数据...")
    print(f"📦 理论唯一商品种类: {len(BRANDS)} × {len(TITLE_TEMPLATES)} × {len(SUFFIXES)} = {unique_count} 种")

    start_time = time.time()
    success_count = 0
    error_count = 0

    actions = []
    try:
        with tqdm(total=TOTAL_DOCS, desc="导入进度", unit="条") as pbar:
            for i in range(1, TOTAL_DOCS + 1):
                actions.append(generate_product_doc(i))

                if len(actions) >= BULK_SIZE or i == TOTAL_DOCS:
                    try:
                        result = helpers.bulk(es, actions, raise_on_error=False)
                        success_count += result[0]
                        if len(result[1]) > 0:
                            error_count += len(result[1])
                    except Exception as e:
                        print(f"\n❌ Bulk 写入错误：{e}")
                        error_count += len(actions)

                    actions = []
                    # 正确计算本次更新的条数
                    batch_size = BULK_SIZE if i >= BULK_SIZE else (i % BULK_SIZE or BULK_SIZE)
                    pbar.update(batch_size if i != TOTAL_DOCS else (TOTAL_DOCS - pbar.n))

    except KeyboardInterrupt:
        print("\n⚠️  用户中断导入")
    except Exception as e:
        print(f"\n❌ 发生错误：{e}")
    finally:
        elapsed = time.time() - start_time
        print("\n" + "="*50)
        print(f"✅ 导入完成！")
        print(f"📊 成功：{success_count} 条")
        print(f"❌ 失败：{error_count} 条")
        print(f"⏱️  耗时：{elapsed:.2f} 秒")
        print(f"🚀 速度：{success_count / elapsed:.2f} 条/秒")
        print("="*50)

        es.indices.refresh(index=INDEX_NAME)
        print("🔄 索引已刷新，数据可搜索")

if __name__ == "__main__":
    main()