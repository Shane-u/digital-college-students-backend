# prepare_recbole_data.py
"""
将MySQL导出的CSV数据转换为RecBole格式
RecBole需要的数据格式：
1. .inter文件：用户-物品交互数据（user_id:token, item_id:token, rating:float, timestamp:float）
2. .user文件：用户特征（可选）
3. .item文件：物品特征（可选）
"""
import pandas as pd
import os

def prepare_interaction_data(csv_path, output_dir='data/recbole'):
    """
    准备交互数据（.inter文件）
    
    Args:
        csv_path: 训练数据CSV路径（包含user_id, item_id, behavior_type, behavior_value, timestamp等）
        output_dir: 输出目录
    """
    print(f"读取数据: {csv_path}")
    df = pd.read_csv(csv_path)
    
    # RecBole需要的列：user_id:token, item_id:token, rating:float, timestamp:float
    recbole_df = pd.DataFrame()
    recbole_df['user_id:token'] = df['user_id']
    recbole_df['item_id:token'] = df['item_id']
    
    # 将behavior_value作为rating（如果没有，使用behavior_type的权重）
    if 'behavior_value' in df.columns:
        recbole_df['rating:float'] = df['behavior_value']
    else:
        behavior_weights = {
            'VIEW': 1.0,
            'CLICK': 2.0,
            'SHARE': 3.0,
            'COLLECT': 5.0,
            'APPLY': 10.0
        }
        recbole_df['rating:float'] = df['behavior_type'].map(behavior_weights).fillna(1.0)
    
    # 时间戳（如果有）
    if 'timestamp' in df.columns:
        recbole_df['timestamp:float'] = df['timestamp']
    else:
        # 如果没有时间戳，使用序号
        recbole_df['timestamp:float'] = range(len(recbole_df))
    
    # 保存为.inter文件
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, 'recbole_data.inter')
    recbole_df.to_csv(output_path, sep='\t', index=False)
    print(f"交互数据已保存到: {output_path}")
    print(f"数据量: {len(recbole_df)} 条")
    
    return output_path


def prepare_user_features(user_features_path, output_dir='data/recbole'):
    """
    准备用户特征数据（.user文件，可选）
    
    Args:
        user_features_path: 用户特征CSV路径
        output_dir: 输出目录
    """
    if not os.path.exists(user_features_path):
        print(f"用户特征文件不存在，跳过: {user_features_path}")
        return None
    
    print(f"读取用户特征: {user_features_path}")
    df = pd.read_csv(user_features_path)
    
    # RecBole格式：user_id:token, major:token, grade:token, school:token
    recbole_df = pd.DataFrame()
    recbole_df['user_id:token'] = df['user_id']
    
    # 添加用户特征列
    if 'major' in df.columns:
        recbole_df['major:token'] = df['major'].fillna('未知')
    if 'grade' in df.columns:
        recbole_df['grade:token'] = df['grade'].fillna('未知')
    if 'school' in df.columns:
        recbole_df['school:token'] = df['school'].fillna('未知')
    
    output_path = os.path.join(output_dir, 'recbole_data.user')
    recbole_df.to_csv(output_path, sep='\t', index=False)
    print(f"用户特征已保存到: {output_path}")
    
    return output_path


def prepare_item_features(item_features_path, item_type='CONTEST', output_dir='data/recbole'):
    """
    准备物品特征数据（.item文件，可选）
    
    Args:
        item_features_path: 物品特征CSV路径
        item_type: 物品类型（CONTEST或JOB）
        output_dir: 输出目录
    """
    if not os.path.exists(item_features_path):
        print(f"物品特征文件不存在，跳过: {item_features_path}")
        return None
    
    print(f"读取物品特征: {item_features_path}")
    df = pd.read_csv(item_features_path)
    
    # RecBole格式：item_id:token, category:token, level:token等
    recbole_df = pd.DataFrame()
    recbole_df['item_id:token'] = df['item_id']
    
    if item_type == 'CONTEST':
        if 'class_second' in df.columns:
            recbole_df['category:token'] = df['class_second'].fillna('其他')
        if 'level' in df.columns:
            recbole_df['level:token'] = df['level'].fillna('未知')
        if 'contest_name' in df.columns:
            recbole_df['name:token_seq'] = df['contest_name'].fillna('')
    else:  # JOB
        if 'job_name' in df.columns:
            recbole_df['name:token'] = df['job_name'].fillna('其他')
        if 'company_name' in df.columns:
            recbole_df['company:token'] = df['company_name'].fillna('未知')
    
    output_path = os.path.join(output_dir, 'recbole_data.item')
    recbole_df.to_csv(output_path, sep='\t', index=False)
    print(f"物品特征已保存到: {output_path}")
    
    return output_path


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='准备RecBole数据格式')
    parser.add_argument('--training_data', type=str, required=True,
                       help='训练数据CSV路径')
    parser.add_argument('--user_features', type=str, default=None,
                       help='用户特征CSV路径（可选）')
    parser.add_argument('--item_features', type=str, default=None,
                       help='物品特征CSV路径（可选）')
    parser.add_argument('--item_type', type=str, default='CONTEST',
                       choices=['CONTEST', 'JOB'],
                       help='物品类型')
    parser.add_argument('--output_dir', type=str, default='data/recbole',
                       help='输出目录')
    
    args = parser.parse_args()
    
    # 准备交互数据（必需）
    prepare_interaction_data(args.training_data, args.output_dir)
    
    # 准备用户特征（可选）
    if args.user_features:
        prepare_user_features(args.user_features, args.output_dir)
    
    # 准备物品特征（可选）
    if args.item_features:
        prepare_item_features(args.item_features, args.item_type, args.output_dir)
    
    print("\n数据准备完成！")
    print(f"数据文件保存在: {args.output_dir}/")
    print("文件列表:")
    for file in os.listdir(args.output_dir):
        print(f"  - {file}")
