# train_with_recbole.py
"""
使用RecBole框架训练推荐模型

这个脚本整合了数据准备、模型训练、Embedding生成和保存到Milvus的全部流程。

支持多种模型：DSSM（双塔）、BPR、DeepFM、NGCF等
"""
import os
import sys
import pandas as pd
import numpy as np
from recbole.config import Config
from recbole.data import create_dataset, data_preparation
from recbole.model.context_aware_recommender import DSSM
from recbole.data.interaction import Interaction
from recbole.trainer import Trainer
from recbole.utils import init_seed, init_logger, get_model, get_trainer
from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType, utility
import torch
from collections import OrderedDict

# pymilvus2.6.9+Milvus服务器2.6.9
def save_to_milvus(collection_name, embeddings, ids, host='localhost', port='19530'):
    """保存Embedding到Milvus"""
    from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType, utility
    import numpy as np

    print(f"连接Milvus: {host}:{port}")
    connections.connect("default", host=host, port=port, timeout=60)

    # 2. 动态适配主键名：user_embeddings→user_id，contest_item_embeddings→item_id，job_item_embeddings→item_id
    if "user" in collection_name:
        primary_key_name = "user_id"
    else:
        primary_key_name = "item_id" # 统一物品ID字段名
    
    # 3. 检查并删除旧集合
    if utility.has_collection(collection_name):
        utility.drop_collection(collection_name)
        print(f"清理历史集合: {collection_name}")

    # 4. 定义集合Schema
    fields = [
        FieldSchema(name=primary_key_name, dtype=DataType.INT64, is_primary=True, auto_id=False),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=embeddings.shape[1])
    ]
    schema = CollectionSchema(fields=fields, description=f"{collection_name} embeddings")

    # 5. 创建集合+插入数据
    collection = Collection(name=collection_name, schema=schema)
    data = [ids.tolist(), embeddings.tolist()]
    collection.insert(data)
    collection.flush()  # 强制刷盘，确保数据写入

    # 6. 创建索引+加载集合
    index_params = {
        "metric_type": "COSINE",
        "index_type": "IVF_FLAT",
        "params": {"nlist": 128}
    }
    collection.create_index(field_name="embedding", index_params=index_params)
    collection.load()

    print(f"✅ 成功保存 {len(ids)} 条向量到Milvus集合: {collection_name}（主键：{primary_key_name}，维度：{embeddings.shape[1]}）\n")


def prepare_recbole_data_files(raw_data_base_path, output_data_path, dataset_name, item_type):
    """
    将MySQL导出的CSV数据转换为RecBole格式，并放置到指定的输出目录。
    这个函数整合了 prepare_interaction_data, prepare_user_features, prepare_item_features 的逻辑。

    Args:
        raw_data_base_path (str): 原始CSV文件所在的基路径，例如 'recommend/data'。
        output_data_path (str): RecBole数据文件的输出目录，例如 'recommend/data/recbole/contest'。
        dataset_name (str): RecBole数据集名称，例如 'contest_recbole_data'。
        item_type (str): 物品类型，'contest' 或 'job'。
    """
    os.makedirs(output_data_path, exist_ok=True)
    print(f"\n准备RecBole数据到: {output_data_path}")

    # 1. 准备交互数据
    csv_path = os.path.join(raw_data_base_path, f"training_data_{item_type}.csv")
    print(f"读取交互数据: {csv_path}")
    df = pd.read_csv(csv_path)

    recbole_inter_df = pd.DataFrame()
    recbole_inter_df['user_id:token'] = df['user_id']
    recbole_inter_df['item_id:token'] = df['item_id']

    if 'behavior_value' in df.columns:
        recbole_inter_df['rating:float'] = df['behavior_value']
    else:
        behavior_weights = {
            'VIEW': 1.0, 'CLICK': 2.0, 'SHARE': 3.0, 'COLLECT': 5.0, 'APPLY': 10.0
        }
        recbole_inter_df['rating:float'] = df['behavior_type'].map(behavior_weights).fillna(1.0)

    if 'timestamp' in df.columns:
        recbole_inter_df['timestamp:float'] = df['timestamp']
    else:
        recbole_inter_df['timestamp:float'] = range(len(recbole_inter_df))

    output_inter_path = os.path.join(output_data_path, f'{dataset_name}.inter')
    recbole_inter_df.to_csv(output_inter_path, sep='\t', index=False)
    print(f"交互数据已保存到: {output_inter_path}")
    print(f"数据量: {len(recbole_inter_df)} 条")

    # 2. 准备用户特征数据（.user文件） - 用户特征是通用的
    user_features_path = os.path.join(raw_data_base_path, 'user_features.csv')
    if os.path.exists(user_features_path):
        print(f"读取用户特征: {user_features_path}")
        df_user = pd.read_csv(user_features_path)

        recbole_user_df = pd.DataFrame()
        recbole_user_df['user_id:token'] = df_user['user_id']

        if 'major' in df_user.columns:
            recbole_user_df['major:token'] = df_user['major'].fillna('未知')
        if 'grade' in df_user.columns:
            recbole_user_df['grade:token'] = df_user['grade'].fillna('未知')
        if 'school' in df_user.columns:
            recbole_user_df['school:token'] = df_user['school'].fillna('未知')

        output_user_path = os.path.join(output_data_path, f'{dataset_name}.user')
        recbole_user_df.to_csv(output_user_path, sep='\t', index=False)
        print(f"用户特征已保存到: {output_user_path}")
    else:
        print(f"用户特征文件不存在，跳过: {user_features_path}")

    # 3. 准备物品特征数据（.item文件） - 物品特征按类型区分
    item_features_path = os.path.join(raw_data_base_path, f'item_features_{item_type}.csv')
    if os.path.exists(item_features_path):
        print(f"读取物品特征: {item_features_path}")
        df_item = pd.read_csv(item_features_path)

        recbole_item_df = pd.DataFrame()
        recbole_item_df['item_id:token'] = df_item['item_id']

        if item_type == 'contest':
            if 'class_second' in df_item.columns:
                recbole_item_df['category:token'] = df_item['class_second'].fillna('其他')
            if 'level' in df_item.columns:
                recbole_item_df['level:token'] = df_item['level'].fillna('未知')
            if 'contest_name' in df_item.columns:
                # 对于contest_name，RecBole的DSSM可能更适合用TOKEN_SEQ处理文本
                # 这里假设RecBole配置中已将name:token_seq识别为sequence类型
                recbole_item_df['name:token_seq'] = df_item['contest_name'].fillna('')
        elif item_type == 'job':
            if 'job_name' in df_item.columns:
                recbole_item_df['name:token'] = df_item['job_name'].fillna('其他')
            if 'job_content' in df_item.columns:
                recbole_item_df['content:token_seq'] = df_item['job_content'].fillna('')
            if 'company_name' in df_item.columns:
                recbole_item_df['company:token'] = df_item['company_name'].fillna('未知')
            if 'salary' in df_item.columns:
                # 修改：将薪资作为离散的Token处理
                recbole_item_df['salary:token'] = df_item['salary'].fillna('未知')

            if 'work_year' in df_item.columns:
                recbole_item_df['work_year:token'] = df_item['work_year'].fillna('不限')
        # 其他物品类型可以在这里添加

        output_item_path = os.path.join(output_data_path, f'{dataset_name}.item')
        recbole_item_df.to_csv(output_item_path, sep='\t', index=False)
        print(f"物品特征已保存到: {output_item_path}")
    else:
        print(f"物品特征文件不存在，跳过: {item_features_path}")

    print(f"✅ RecBole数据准备完成，文件列表:")
    for file in os.listdir(output_data_path):
        print(f"  - {file}")

    # TODO: 更新config.yaml中物品特征的field_separator, 确保name:token_seq能正确解析
    # 这需要在config.yaml中配置，或者在Config对象创建时传入


class RecBoleTrainer:
    def __init__(self, config_file='config.yaml', model_name='DSSM', item_type=None):
        """
        初始化RecBole训练器
        
        Args:
            config_file: 配置文件路径
            model_name: 模型名称（DSSM, BPR, DeepFM等）
            item_type: 物品类型，例如 'contest' 或 'job'，用于区分数据和保存模型名
        """
        self.config_file = os.path.join(os.path.dirname(__file__), config_file)
        self.model_name = model_name
        self.item_type = item_type
        self.config = None
        self.model = None
        self.trainer = None
        
    def init_config(self, dataset_name='recbole_data', data_path='data/recbole', model_path=None):
        """
        初始化配置
        """
        config_dict = {
            'model': self.model_name,
            'dataset': dataset_name,
            'data_path': data_path,
            'config_file': self.config_file,
        }
        
        self.config = Config(
            model=self.model_name,
            dataset=dataset_name,
            config_file_list=[self.config_file],
            config_dict=config_dict
        )
        
        if model_path:
            self.config['checkpoint_dir'] = model_path
            print(f"模型保存路径已设置为: {self.config['checkpoint_dir']}")

        init_seed(self.config['seed'], self.config['reproducibility'])
        init_logger(self.config)
        
        print(f"配置初始化完成")
        print(f"模型: {self.model_name}")
        print(f"数据集: {dataset_name}")
        print(f"数据路径: {data_path}")
        if self.item_type:
            print(f"物品类型: {self.item_type}")
        
    def prepare_data(self):
        """准备数据"""
        print("\n准备数据...")
        dataset = create_dataset(self.config)
        
        print(f"数据集信息:")
        print(f"  用户数: {dataset.user_num}")
        print(f"  物品数: {dataset.item_num}")
        print(f"  交互数: {dataset.inter_num}")
        
        train_data, valid_data, test_data = data_preparation(
            self.config, dataset
        )
        
        return dataset, train_data, valid_data, test_data
    
    def build_model(self, dataset):
        """构建模型"""
        print(f"\n构建模型: {self.model_name}")
        self.model = get_model(self.model_name)(self.config, dataset).to(self.config['device'])
        print(f"模型参数数量: {sum(p.numel() for p in self.model.parameters() if p.requires_grad)}")
        return self.model
    
    def train(self, train_data, valid_data):
        """训练模型"""
        print("\n开始训练...")
        self.trainer = get_trainer(self.config['MODEL_TYPE'], self.model_name)(self.config, self.model)
        
        # 训练
        best_valid_score, best_valid_result = self.trainer.fit(
            train_data, valid_data, verbose=True, saved=True
        )
        
        print(f"\n训练完成！")
        print(f"最佳验证分数: {best_valid_score}")
        print(f"最佳验证结果: {best_valid_result}")
        
        return best_valid_score, best_valid_result
    
    def evaluate(self, test_data):
        """评估模型"""
        print("\n评估模型...")
        test_result = self.trainer.evaluate(test_data, load_best_model=True)
        print(f"测试结果: {test_result}")
        return test_result
    
    def generate_embeddings(self, dataset):
        """
        生成用户和物品的Embedding向量
        
        Args:
            dataset: RecBole数据集对象
        """
        print("\n生成Embedding向量...")
        
        self.model.eval()
        
        all_internal_user_ids = torch.arange(0, dataset.user_num).to(self.config['device'])
        all_internal_item_ids = torch.arange(0, dataset.item_num).to(self.config['device'])
        
        user_embeddings = []
        item_embeddings = []
        
        final_original_user_ids = []
        final_original_item_ids = []

        # 创建从内部ID到原始ID的映射
        original_user_id_map = dataset.field2token_id['user_id']
        original_item_id_map = dataset.field2token_id['item_id']
        
        internal_to_original_user_id_map = {v: int(k) for k, v in original_user_id_map.items() if k != '[PAD]'}
        internal_to_original_item_id_map = {v: int(k) for k, v in original_item_id_map.items() if k != '[PAD]'}

        batch_size = 1024
        
        with torch.no_grad():
            # 生成用户Embedding
            print("生成用户Embedding...")
            for i in range(0, len(all_internal_user_ids), batch_size):
                batch_internal_user_ids_tensor = all_internal_user_ids[i:i+batch_size]
                
                # 过滤掉内部ID为0（[PAD]）的Embedding，并收集原始用户ID
                valid_internal_user_ids = []
                current_original_user_ids = []
                for internal_uid_val in batch_internal_user_ids_tensor.cpu().numpy():
                    if internal_uid_val in internal_to_original_user_id_map:
                        valid_internal_user_ids.append(internal_uid_val)
                        current_original_user_ids.append(internal_to_original_user_id_map[internal_uid_val])
                
                if not valid_internal_user_ids:
                    continue
                
                valid_internal_user_ids_tensor = torch.tensor(valid_internal_user_ids, dtype=torch.long).to(self.config['device'])

                if hasattr(self.model, 'user_embedding'):
                    batch_user_emb = self.model.user_embedding(valid_internal_user_ids_tensor)
                elif hasattr(self.model, 'get_user_embedding'):
                    batch_user_emb = self.model.get_user_embedding(valid_internal_user_ids_tensor)
                else:
                    batch_user_emb = self._get_user_embedding_for_model(valid_internal_user_ids_tensor, dataset)
                
                user_embeddings.append(batch_user_emb.cpu().numpy())
                final_original_user_ids.extend(current_original_user_ids)
            
            # 生成物品Embedding
            print("生成物品Embedding...")
            for i in range(0, len(all_internal_item_ids), batch_size):
                batch_internal_item_ids_tensor = all_internal_item_ids[i:i+batch_size]
                
                # 过滤掉内部ID为0（[PAD]）的Embedding，并收集原始物品ID
                valid_internal_item_ids = []
                current_original_item_ids = []
                for internal_iid_val in batch_internal_item_ids_tensor.cpu().numpy():
                    if internal_iid_val in internal_to_original_item_id_map:
                        valid_internal_item_ids.append(internal_iid_val)
                        current_original_item_ids.append(internal_to_original_item_id_map[internal_iid_val])
                
                if not valid_internal_item_ids:
                    continue

                valid_internal_item_ids_tensor = torch.tensor(valid_internal_item_ids, dtype=torch.long).to(self.config['device'])

                if hasattr(self.model, 'item_embedding'):
                    batch_item_emb = self.model.item_embedding(valid_internal_item_ids_tensor)
                elif hasattr(self.model, 'get_item_embedding'):
                    batch_item_emb = self.model.get_item_embedding(valid_internal_item_ids_tensor)
                else:
                    batch_item_emb = self._get_item_embedding_for_model(valid_internal_item_ids_tensor, dataset)
                
                item_embeddings.append(batch_item_emb.cpu().numpy())
                final_original_item_ids.extend(current_original_item_ids)
        
        user_embeddings_np = np.vstack(user_embeddings) if user_embeddings else np.array([])
        item_embeddings_np = np.vstack(item_embeddings) if item_embeddings else np.array([])
        
        final_user_ids_np = np.array(final_original_user_ids, dtype=np.int64)
        final_item_ids_np = np.array(final_original_item_ids, dtype=np.int64)
        
        print(f"用户Embedding形状: {user_embeddings_np.shape}")
        print(f"物品Embedding形状: {item_embeddings_np.shape}")
        print(f"最终用户ID数量: {len(final_user_ids_np)}")
        print(f"最终物品ID数量: {len(final_item_ids_np)}")
        
        return user_embeddings_np, item_embeddings_np, final_user_ids_np, final_item_ids_np
    
    def _get_user_embedding_for_model(self, user_ids, dataset):
        """为特定模型获取用户Embedding（DSSM等需要特征输入）"""
        from recbole.utils import FeatureType

        if self.model_name == 'DSSM':
            batch_size = len(user_ids)
            # 获取用户特征
            user_feat = dataset.get_user_feature()

            user_feat_batch = None
            is_dataframe = isinstance(user_feat, pd.DataFrame)

            if user_feat is not None:
                if is_dataframe:
                    # DataFrame indexing needs numpy array and iloc for positional indexing
                    # Internal IDs map to rows
                    user_feat_batch = user_feat.iloc[user_ids.cpu().numpy()]
                else:
                    # Interaction indexing handles tensor directly
                    user_feat_batch = user_feat[user_ids]

            dummy_interaction_data = {}
            for field_name in self.model.user_field_names:
                if field_name == 'user_id':
                    # user_id 特殊处理，直接使用传入的 user_ids
                    dummy_interaction_data[field_name] = user_ids
                    continue

                found = False
                if user_feat_batch is not None:
                    if is_dataframe:
                        if field_name in user_feat_batch.columns:
                            val = user_feat_batch[field_name].values
                            # Convert values to tensor based on FeatureType
                            if dataset.field2type[field_name] == FeatureType.TOKEN:
                                dummy_interaction_data[field_name] = torch.tensor(val, dtype=torch.long).to(self.config['device'])
                            elif dataset.field2type[field_name] == FeatureType.FLOAT:
                                dummy_interaction_data[field_name] = torch.tensor(val, dtype=torch.float).to(self.config['device'])
                            else: # Default
                                dummy_interaction_data[field_name] = torch.tensor(val, dtype=torch.long).to(self.config['device'])
                            found = True
                    else:
                        # Interaction object
                        if field_name in user_feat_batch:
                            dummy_interaction_data[field_name] = user_feat_batch[field_name].to(self.config['device'])
                            found = True

                if not found:
                    # 缺失特征填充 0
                    if dataset.field2type[field_name] == FeatureType.TOKEN:
                        dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])
                    elif dataset.field2type[field_name] == FeatureType.FLOAT:
                        dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.float).to(self.config['device'])
                    else: # Assuming TOKEN_SEQ, FLOAT_SEQ for now, default to long
                        dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])

            # Fill item fields with dummy zeros
            for field_name in self.model.item_field_names:
                if dataset.field2type[field_name] == FeatureType.TOKEN:
                    dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])
                elif dataset.field2type[field_name] == FeatureType.FLOAT:
                    dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.float).to(self.config['device'])
                else:
                    dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])

            # Create a dummy Interaction object
            interaction_for_user_embedding = Interaction(dummy_interaction_data).to(self.config['device'])

            # Use DSSM's internal logic to get user embedding
            embed_result = self.model.double_tower_embed_input_fields(interaction_for_user_embedding)
            user_sparse_embedding, user_dense_embedding = embed_result[:2]

            user_embeddings_list = []
            if user_sparse_embedding is not None:
                user_embeddings_list.append(user_sparse_embedding)
            if user_dense_embedding is not None and len(user_dense_embedding.shape) == 3:
                user_embeddings_list.append(user_dense_embedding)
            
            embed_user_concatenated = torch.cat(user_embeddings_list, dim=1) # Concatenate along feature dim
            user_final_embedding = self.model.user_mlp_layers(embed_user_concatenated.view(batch_size, -1))
            return user_final_embedding
        else:
            # 其他模型的回退逻辑
            return self.model.user_embedding(user_ids)
    
    def _get_item_embedding_for_model(self, item_ids, dataset):
        """为特定模型获取物品Embedding"""
        from recbole.utils import FeatureType

        if self.model_name == 'DSSM':
            batch_size = len(item_ids)
            # 获取物品特征
            item_feat = dataset.get_item_feature()

            item_feat_batch = None
            is_dataframe = isinstance(item_feat, pd.DataFrame)

            if item_feat is not None:
                if is_dataframe:
                     # DataFrame indexing needs numpy array and iloc for positional indexing
                     item_feat_batch = item_feat.iloc[item_ids.cpu().numpy()]
                else:
                     # Interaction indexing
                     item_feat_batch = item_feat[item_ids]

            dummy_interaction_data = {}
            # Fill user fields with dummy zeros
            for field_name in self.model.user_field_names:
                if dataset.field2type[field_name] == FeatureType.TOKEN:
                    dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])
                elif dataset.field2type[field_name] == FeatureType.FLOAT:
                    dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.float).to(self.config['device'])
                else:
                    dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])

            # Populate item fields
            for field_name in self.model.item_field_names:
                if field_name == 'item_id':
                     dummy_interaction_data[field_name] = item_ids
                     continue

                found = False
                if item_feat_batch is not None:
                    if is_dataframe:
                        if field_name in item_feat_batch.columns:
                            val = item_feat_batch[field_name].values
                            if dataset.field2type[field_name] == FeatureType.TOKEN:
                                dummy_interaction_data[field_name] = torch.tensor(val, dtype=torch.long).to(self.config['device'])
                            elif dataset.field2type[field_name] == FeatureType.FLOAT:
                                dummy_interaction_data[field_name] = torch.tensor(val, dtype=torch.float).to(self.config['device'])
                            else:
                                dummy_interaction_data[field_name] = torch.tensor(val, dtype=torch.long).to(self.config['device'])
                            found = True
                    else:
                        if field_name in item_feat_batch:
                            # 获取特征Tensor并确保在正确的设备上
                            dummy_interaction_data[field_name] = item_feat_batch[field_name].to(self.config['device'])
                            found = True

                if not found:
                    # 缺失特征填充 0
                    if dataset.field2type[field_name] == FeatureType.TOKEN:
                        dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])
                    elif dataset.field2type[field_name] == FeatureType.FLOAT:
                        dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.float).to(self.config['device'])
                    else:
                        dummy_interaction_data[field_name] = torch.zeros(batch_size, dtype=torch.long).to(self.config['device'])

            # Create a dummy Interaction object
            interaction_for_item_embedding = Interaction(dummy_interaction_data).to(self.config['device'])

            # Use DSSM's internal logic to get item embedding
            embed_result = self.model.double_tower_embed_input_fields(interaction_for_item_embedding)
            _, _, item_sparse_embedding, item_dense_embedding = embed_result[:]

            item_embeddings_list = []
            if item_sparse_embedding is not None:
                item_embeddings_list.append(item_sparse_embedding)
            if item_dense_embedding is not None and len(item_dense_embedding.shape) == 3:
                item_embeddings_list.append(item_dense_embedding)

            embed_item_concatenated = torch.cat(item_embeddings_list, dim=1) # Concatenate along feature dim
            item_final_embedding = self.model.item_mlp_layers(embed_item_concatenated.view(batch_size, -1))
            return item_final_embedding
        else:
            # 其他模型的回退逻辑
            return self.model.item_embedding(item_ids)
    
    def save_embeddings_to_milvus(self, user_embeddings, item_embeddings, 
                                  user_ids, item_ids,
                                  milvus_host='localhost', milvus_port='19530',
                                  item_collection_name_suffix="_item_embeddings"):
        """
        保存Embedding到Milvus
        """
        print("\n保存Embedding到Milvus...")
        
        # 保存用户Embedding
        save_to_milvus(
            'user_embeddings',
            user_embeddings,
            user_ids,
            host=milvus_host,
            port=milvus_port
        )
        
        # 保存物品Embedding，集合名称包含物品类型
        collection_name = self.item_type + item_collection_name_suffix
        save_to_milvus(
            collection_name,
            item_embeddings,
            item_ids,
            host=milvus_host,
            port=milvus_port
        )
        
        print("Embedding已保存到Milvus")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='使用RecBole训练推荐模型')
    parser.add_argument('--model', type=str, default='DSSM',
                       choices=['DSSM', 'BPR', 'DeepFM', 'NGCF', 'LightGCN'],
                       help='模型名称')
    parser.add_argument('--dataset', type=str, default='recbole_data',
                       help='数据集名称')
    parser.add_argument('--data_path', type=str, default='recommend/data/recbole',
                       help='数据路径，例如 recommend/data/recbole')
    parser.add_argument('--raw_data_base_path', type=str, default='recommend/data',
                       help='原始CSV文件所在的基路径，例如 recommend/data')
    parser.add_argument('--config', type=str, default='config.yaml',
                       help='配置文件路径')
    parser.add_argument('--milvus_host', type=str, default='154.201.70.202',
                       help='Milvus主机地址')
    parser.add_argument('--milvus_port', type=str, default='19530',
                       help='Milvus端口')
    parser.add_argument('--save_embeddings', action='store_true',
                       help='是否保存Embedding到Milvus')
    
    args = parser.parse_args()
    
    # 为竞赛和职业分别训练模型和生成Embedding
    item_types = ['contest', 'job']

    # 用户Embedding只需要训练一次，因为用户的兴趣模型是通用的
    user_embeddings = None
    user_ids = None

    for item_type in item_types:
        print(f"\n{'='*30}\n开始处理 {item_type} 类型物品\n{'='*30}")

        # 定义原始数据路径和RecBole数据输出路径
        raw_data_base_path = args.raw_data_base_path
        current_dataset_name = item_type + "_recbole_data"
        output_data_path = os.path.join(args.data_path, current_dataset_name)

        # 0. 准备RecBole格式的数据文件
        prepare_recbole_data_files(
            raw_data_base_path=raw_data_base_path,
            output_data_path=output_data_path,
            dataset_name=current_dataset_name,
            item_type=item_type
        )

        # 1. 初始化训练器
        trainer = RecBoleTrainer(config_file=args.config, model_name=args.model, item_type=item_type)

        # 动态设置数据集名称和数据路径以匹配不同物品类型的数据
        current_data_path = args.data_path

        trainer.init_config(dataset_name=current_dataset_name, data_path=current_data_path)
        
        # 准备数据
        dataset, train_data, valid_data, test_data = trainer.prepare_data()
        
        # 构建模型
        trainer.build_model(dataset)
        
        # 训练
        trainer.train(train_data, valid_data)
        
        # 评估
        trainer.evaluate(test_data)

        # 生成并保存Embedding
        if args.save_embeddings:
            current_user_embeddings, item_embeddings, current_user_ids, item_ids = \
                trainer.generate_embeddings(dataset)
            
            if user_embeddings is None: # 只在第一次训练时保存用户Embedding
                user_embeddings = current_user_embeddings
                user_ids = current_user_ids

            print(f"DEBUG: 准备插入Milvus的原始用户ID（前10个）: {user_ids[:10]}")
            print(f"DEBUG: 准备插入Milvus的原始物品ID（前10个，类型: {item_type}）: {item_ids[:10]}")
            print(f"DEBUG: 原始用户ID到RecBole内部ID的映射示例：")
            original_to_internal_user_map = dataset.field2token_id['user_id']
            for original_id_str, internal_id in list(original_to_internal_user_map.items())[:10]:
                print(f"  原始ID: {original_id_str}, 内部ID: {internal_id}")
            print(f"DEBUG: 原始物品ID到RecBole内部ID的映射示例（类型: {item_type}）：")
            original_to_internal_item_map = dataset.field2token_id['item_id']
            for original_id_str, internal_id in list(original_to_internal_item_map.items())[:10]:
                print(f"  原始ID: {original_id_str}, 内部ID: {internal_id}")

            # 为每个物品类型保存不同的物品Embedding集合
            trainer.save_embeddings_to_milvus(
                user_embeddings, item_embeddings,
                user_ids, item_ids,
                milvus_host=args.milvus_host,
                milvus_port=args.milvus_port,
                item_collection_name_suffix="_item_embeddings"
            )

    print("\n训练流程完成！")


if __name__ == '__main__':
    main()
