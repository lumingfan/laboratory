#!/usr/bin/env python3
"""
JSON结构提取器 - 分析JSON文件并提取其结构模式

此脚本可以分析JSON文件的内容，提取其中的数据结构模式，
对于复杂嵌套的JSON数据特别有用，能够显示所有可能的字段及其类型。
"""

import json
import sys
import argparse
from typing import Any, Dict, List, Union


def get_type_name(value: Any) -> str:
    """
    获取值的基本类型名称
    
    Args:
        value: 待检测类型的值
        
    Returns:
        类型名称字符串
    """
    if value is None:
        return "null"
    elif isinstance(value, str):
        return "string"
    elif isinstance(value, int):
        return "integer"
    elif isinstance(value, float):
        return "number"
    elif isinstance(value, bool):
        return "boolean"
    elif isinstance(value, list):
        return "array"
    elif isinstance(value, dict):
        return "object"
    else:
        return type(value).__name__


def merge_structures(struct_a: Any, struct_b: Any) -> Any:
    """
    合并两个结构。
    主要用于合并列表中的多个字典，以展示所有可能的字段。
    
    Args:
        struct_a: 第一个结构
        struct_b: 第二个结构
        
    Returns:
        合并后的结构
    """
    if isinstance(struct_a, dict) and isinstance(struct_b, dict):
        merged = struct_a.copy()
        for k, v in struct_b.items():
            if k in merged:
                # 如果key存在，递归合并value的结构
                merged[k] = merge_structures(merged[k], v)
            else:
                merged[k] = v
        return merged
    elif isinstance(struct_a, list) and isinstance(struct_b, list):
        # 列表合并：如果是列表，取并集
        # 实际场景中，列表通常包含同构数据，这里倾向于展示内部结构
        if not struct_a:
            return struct_b
        if not struct_b:
            return struct_a
        return [merge_structures(struct_a[0], struct_b[0])]
    elif struct_a == struct_b:
        return struct_a
    else:
        # 如果类型冲突（比如一个是int一个是str），显示 Union 类型
        return f"{get_type_name(struct_a)} | {get_type_name(struct_b)}"


def extract_structure(data: Any) -> Any:
    """
    递归提取 JSON 结构
    
    Args:
        data: 待分析的JSON数据
        
    Returns:
        提取的结构模式
    """
    if isinstance(data, dict):
        # 递归处理字典的每一个 value
        return {k: extract_structure(v) for k, v in data.items()}
    
    elif isinstance(data, list):
        if not data:
            return ["<empty_array>"]
        
        # 策略：遍历列表所有元素，尝试合并它们的结构
        # 这样如果列表里有的对象有字段 A，有的有字段 B，结果会显示 A 和 B 都有
        common_structure = None
        
        for item in data:
            current_struct = extract_structure(item)
            if common_structure is None:
                common_structure = current_struct
            else:
                common_structure = merge_structures(common_structure, current_struct)
        
        return [common_structure]
    
    else:
        return get_type_name(data)


def format_structure(structure: Any, indent_level: int = 0) -> str:
    """
    格式化结构输出，使其更易读
    
    Args:
        structure: 待格式化的结构
        indent_level: 缩进级别
        
    Returns:
        格式化后的字符串
    """
    indent = "  " * indent_level
    if isinstance(structure, dict):
        lines = ["{\n"]
        for key, value in structure.items():
            formatted_value = format_structure(value, indent_level + 1)
            lines.append(f'{indent}  "{key}": {formatted_value},\n')
        if lines[-1].endswith(",\n"):
            lines[-1] = lines[-1][:-2] + "\n"  # 移除最后一个逗号
        lines.append(f"{indent}}}")
        return "".join(lines)
    elif isinstance(structure, list):
        if len(structure) == 1:
            return f"[{format_structure(structure[0], indent_level)}]"
        else:
            items = ", ".join(format_structure(item, indent_level) for item in structure)
            return f"[{items}]"
    else:
        return str(structure)


def parse_arguments():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(
        description="分析JSON文件并提取其结构模式",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例用法:
  %(prog)s data.json                           # 从文件读取
  %(prog)s -i data.json -o schema.json         # 指定输入输出文件
  %(prog)s -f pretty data.json                 # 格式化输出
  cat data.json | %(prog)s                     # 从管道读取
  %(prog)s --help                              # 显示此帮助信息

注意:
  - 如果不指定输入文件，脚本将从标准输入读取
  - 对于大型JSON文件，处理可能需要一些时间
  - 合并列表中的不同结构时，会显示所有可能的字段
        """
    )
    
    parser.add_argument(
        'input_file',
        nargs='?',
        help='输入JSON文件路径 (如果省略，则从标准输入读取)'
    )
    
    parser.add_argument(
        '-o', '--output',
        type=str,
        help='输出文件路径 (如果省略，则输出到标准输出)'
    )
    
    parser.add_argument(
        '-f', '--format',
        choices=['compact', 'pretty'],
        default='pretty',
        help='输出格式 (默认: pretty)'
    )
    
    parser.add_argument(
        '-t', '--type-only',
        action='store_true',
        help='仅显示类型信息，不显示具体字段名'
    )
    
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='显示详细处理信息'
    )
    
    return parser.parse_args()


def read_input(source: str = None) -> str:
    """
    从指定源读取JSON内容
    
    Args:
        source: 输入源文件路径，None表示从标准输入读取
        
    Returns:
        JSON内容字符串
    """
    if source:
        with open(source, 'r', encoding='utf-8') as f:
            return f.read()
    elif not sys.stdin.isatty():
        # 检查是否有管道输入
        return sys.stdin.read()
    else:
        return None


def main():
    """主函数"""
    args = parse_arguments()
    
    # 读取输入
    try:
        content = read_input(args.input_file)
        
        if content is None:
            print("错误: 未提供输入文件，也未检测到标准输入数据", file=sys.stderr)
            print("\n用法:", file=sys.stderr)
            print(f"  {sys.argv[0]} <json_file>     # 从文件读取", file=sys.stderr)
            print(f"  cat <file> | {sys.argv[0]}   # 从管道读取", file=sys.stderr)
            sys.exit(1)
        
        if args.verbose:
            print(f"正在解析JSON数据...")
        
        data = json.loads(content)
        
        if args.verbose:
            print(f"JSON数据加载成功，开始提取结构...")
        
        # 提取结构
        structure = extract_structure(data)
        
        # 根据选项处理输出
        if args.type_only:
            # 如果只显示类型，简化输出
            result = json.dumps(structure, indent=2, ensure_ascii=False)
        elif args.format == 'compact':
            result = json.dumps(structure, ensure_ascii=False)
        else:  # pretty format
            result = json.dumps(structure, indent=4, ensure_ascii=False)
        
        # 输出结果
        if args.output:
            with open(args.output, 'w', encoding='utf-8') as f:
                f.write(result)
            if args.verbose:
                print(f"结构已保存到 {args.output}")
        else:
            print(result)
        
        if args.verbose:
            print("处理完成")
            
    except json.JSONDecodeError as e:
        print(f"错误: 输入不是有效的 JSON 格式", file=sys.stderr)
        print(f"JSON解析错误: {e}", file=sys.stderr)
        sys.exit(1)
    except FileNotFoundError:
        print(f"错误: 找不到文件 {args.input_file}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n操作被用户中断", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"发生未知错误: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

