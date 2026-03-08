#!/usr/bin/env python3
"""
arXiv元数据过滤器 - 从大型JSON文件中提取特定类别的论文数据

此脚本可以从Kaggle下载的arXiv元数据快照中筛选出特定计算机科学领域的论文，
并按年份进行过滤，生成适用于后续分析的小型数据集。

支持的领域包括：
- cs.AI: 人工智能
- cs.LG: 机器学习
- cs.CL: 计算与语言
- cs.CV: 计算机视觉
"""

import argparse
import json
import re
import sys
from typing import Set, Optional

YEAR_PATTERN = re.compile(r'\d{4}')

def get_year_for_filtering(paper):
    try:
        # 优先看 v1 时间
        versions = paper.get('versions', [])
        if versions:
            return int(YEAR_PATTERN.search(versions[0].get('created', '')) .group())
        # 兜底
        return int(paper.get('update_date', '')[:4])
    except:
        return 0

def parse_arguments():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(
        description="从arXiv元数据中筛选特定领域的论文",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例用法:
  %(prog)s --input arxiv-metadata.json --output filtered-papers.json
  %(prog)s --input data.json --output result.json --categories cs.AI cs.LG --year 2021
  %(prog)s --input input.json --output output.json --limit 5000 --verbose

支持的类别:
  - cs.AI: 人工智能
  - cs.LG: 机器学习  
  - cs.CL: 计算与语言
  - cs.CV: 计算机视觉
  - 更多类别请参考arXiv分类系统
        """
    )
    
    parser.add_argument(
        '--input',
        '-i',
        type=str,
        default='arxiv-metadata-oai-snapshot.json',
        help='输入文件路径 (默认: arxiv-metadata-oai-snapshot.json)'
    )
    
    parser.add_argument(
        '--output', 
        '-o',
        type=str,
        default='arxiv-mvp-subset.json',
        help='输出文件路径 (默认: arxiv-mvp-subset.json)'
    )
    
    parser.add_argument(
        '--categories',
        '-c',
        nargs='+',
        default=['cs.AI', 'cs.LG', 'cs.CL', 'cs.CV'],
        help='要筛选的arXiv类别列表 (默认: cs.AI cs.LG cs.CL cs.CV)'
    )
    
    parser.add_argument(
        '--year',
        '-y',
        type=int,
        default=2020,
        help='最小年份阈值，只保留此年份及以后的论文 (默认: 2020)'
    )
    
    parser.add_argument(
        '--limit',
        '-l',
        type=int,
        default=10000,
        help='最大输出论文数量限制 (默认: 10000, 0表示无限制)'
    )
    
    parser.add_argument(
        '--verbose',
        '-v',
        action='store_true',
        help='启用详细输出模式'
    )
    
    return parser.parse_args()


def filter_arxiv_metadata(input_file: str, output_file: str, target_categories: Set[str], 
                         min_year: int, max_count: int, verbose: bool) -> int:
    """
    过滤arXiv元数据并写入输出文件
    
    Args:
        input_file: 输入文件路径
        output_file: 输出文件路径
        target_categories: 目标类别集合
        min_year: 最小年份
        max_count: 最大论文数量限制
        verbose: 是否显示详细信息
        
    Returns:
        成功处理的论文数量
    """
    count = 0
    
    try:
        with open(output_file, 'w', encoding='utf-8') as f_out:
            with open(input_file, 'r', encoding='utf-8') as f_in:
                for line_num, line in enumerate(f_in, 1):
                    try:
                        paper = json.loads(line)
                        
                        # 1. 过滤分类
                        # categories 字符串如: "cs.LG cs.AI stat.ML"
                        cats = set(paper.get('categories', '').split())
                        if not cats.intersection(target_categories):
                            continue
                        
                        # 2. 过滤年份 (使用 update_date)
                        year = get_year_for_filtering(paper)
                        if year < min_year:
                            continue
                        
                        # 3. 写入新文件
                        f_out.write(json.dumps(paper) + '\n')
                        count += 1
                        
                        # 显示进度
                        if verbose and count % 1000 == 0:
                            print(f"已收集 {count} 篇论文...")
                        
                        # 检查数量限制
                        if max_count > 0 and count >= max_count:
                            if verbose:
                                print(f"达到数量限制 {max_count}，停止处理")
                            break
                            
                    except json.JSONDecodeError as e:
                        if verbose:
                            print(f"警告: 第 {line_num} 行 JSON 解析失败: {e}")
                        continue
                    except Exception as e:
                        if verbose:
                            print(f"警告: 处理第 {line_num} 行时发生错误: {e}")
                        continue
                        
    except FileNotFoundError:
        print(f"错误: 输入文件 '{input_file}' 不存在", file=sys.stderr)
        raise
    except PermissionError:
        print(f"错误: 没有权限读取输入文件 '{input_file}' 或写入输出文件 '{output_file}'", file=sys.stderr)
        raise
    except Exception as e:
        print(f"错误: 文件操作失败: {e}", file=sys.stderr)
        raise
    
    return count


def main():
    """主函数"""
    args = parse_arguments()
    
    # 将类别转换为集合以便快速查找
    target_categories = set(args.categories)
    
    if args.verbose:
        print(f"开始处理...")
        print(f"输入文件: {args.input}")
        print(f"输出文件: {args.output}")
        print(f"目标类别: {target_categories}")
        print(f"最小年份: {args.year}")
        print(f"数量限制: {args.limit if args.limit > 0 else '无限制'}")
    
    try:
        count = filter_arxiv_metadata(
            input_file=args.input,
            output_file=args.output,
            target_categories=target_categories,
            min_year=args.year,
            max_count=args.limit,
            verbose=args.verbose
        )
        
        print(f"完成! 已保存 {count} 篇论文到 {args.output}")
        
    except Exception as e:
        print(f"处理过程中发生错误: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

