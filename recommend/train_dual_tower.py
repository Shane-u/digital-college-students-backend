# train_dual_tower.py
import pandas as pd
import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.model_selection import train_test_split
import pickle
import os
from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType, utility
import json

class DualTowerRecommender:
    def __init__(self, embedding_dim=128):
        self.embedding_dim = embedding_dim
        self.user_model = None
        self.item_model = None
        self.user_encoders = {}
        self.item_encoders = {}
        self.scalers = {}
        
    def load_data(self, csv_path):
        """加载训练数据"""
        print(f"加载数据: {csv_path}")
        df = pd.read_csv(csv_path)
        print(f"数据量: {len(df)} 条")
        print(f"数据列: {df.columns.tolist()}")
        return df
    
    def prepare_features(self, df, item_type='CONTEST'):
        """特征工程"""
        print("开始特征工程...")
        
        # 1. 用户特征编码
        user_features = []
        
        # 专业编码
        if 'user_major' in df.columns:
            le_major = LabelEncoder()
            df['user_major_encoded'] = le_major.fit_transform(df['user_major'].fillna('未知'))
            self.user_encoders['major'] = le_major
            user_features.append('user_major_encoded')
        
        # 年级编码
        if 'user_grade' in df.columns:
            grade_map = {'大一': 1, '大二': 2, '大三': 3, '大四': 4, '研一': 5, '研二': 6, '研三': 7}
            df['user_grade_encoded'] = df['user_grade'].map(grade_map).fillna(0)
            user_features.append('user_grade_encoded')
        
        # 学校编码（如果学校数量不多）
        if 'user_school' in df.columns:
            le_school = LabelEncoder()
            df['user_school_encoded'] = le_school.fit_transform(df['user_school'].fillna('未知'))
            self.user_encoders['school'] = le_school
            user_features.append('user_school_encoded')
        
        # 2. 物品特征编码
        item_features = []
        
        if item_type == 'CONTEST':
            # 竞赛分类编码
            if 'contest_class_second' in df.columns:
                le_class = LabelEncoder()
                df['contest_class_encoded'] = le_class.fit_transform(
                    df['contest_class_second'].fillna('其他')
                )
                self.item_encoders['class'] = le_class
                item_features.append('contest_class_encoded')
            
            # 级别编码
            if 'contest_level' in df.columns:
                level_map = {
                    '校级': 1, '市级': 2, '省级': 3, 
                    '全国性': 4, '全球性': 5
                }
                df['contest_level_encoded'] = df['contest_level'].map(level_map).fillna(0)
                item_features.append('contest_level_encoded')
        
        else:  # JOB
            # 职业名称关键词提取（简化版）
            if 'job_name' in df.columns:
                le_job = LabelEncoder()
                df['job_name_encoded'] = le_job.fit_transform(
                    df['job_name'].fillna('其他')
                )
                self.item_encoders['job_name'] = le_job
                item_features.append('job_name_encoded')
        
        # 3. 构建标签（正样本：收藏、报名；负样本：浏览）
        df['label'] = df['behavior_type'].apply(
            lambda x: 1 if x in ['COLLECT', 'APPLY'] else 0
        )
        
        # 4. 构建特征矩阵
        user_feature_matrix = df[user_features].values.astype(np.float32)
        item_feature_matrix = df[item_features].values.astype(np.float32)
        labels = df['label'].values.astype(np.float32)
        
        print(f"用户特征维度: {user_feature_matrix.shape}")
        print(f"物品特征维度: {item_feature_matrix.shape}")
        print(f"正样本比例: {labels.mean():.2%}")
        
        return user_feature_matrix, item_feature_matrix, labels, df
    
    def build_user_tower(self, input_dim):
        """构建用户塔"""
        user_input = keras.Input(shape=(input_dim,), name='user_input')
        
        # 全连接层
        x = layers.Dense(256, activation='relu', name='user_dense1')(user_input)
        x = layers.BatchNormalization()(x)
        x = layers.Dropout(0.3)(x)
        
        x = layers.Dense(128, activation='relu', name='user_dense2')(x)
        x = layers.BatchNormalization()(x)
        x = layers.Dropout(0.2)(x)
        
        # Embedding层
        user_embedding = layers.Dense(
            self.embedding_dim, 
            activation=None, 
            name='user_embedding'
        )(x)
        
        # L2归一化
        user_embedding = layers.Lambda(
            lambda x: tf.nn.l2_normalize(x, axis=1)
        )(user_embedding)
        
        self.user_model = keras.Model(inputs=user_input, outputs=user_embedding)
        return self.user_model
    
    def build_item_tower(self, input_dim):
        """构建物品塔"""
        item_input = keras.Input(shape=(input_dim,), name='item_input')
        
        # 全连接层
        x = layers.Dense(256, activation='relu', name='item_dense1')(item_input)
        x = layers.BatchNormalization()(x)
        x = layers.Dropout(0.3)(x)
        
        x = layers.Dense(128, activation='relu', name='item_dense2')(x)
        x = layers.BatchNormalization()(x)
        x = layers.Dropout(0.2)(x)
        
        # Embedding层
        item_embedding = layers.Dense(
            self.embedding_dim,
            activation=None,
            name='item_embedding'
        )(x)
        
        # L2归一化
        item_embedding = layers.Lambda(
            lambda x: tf.nn.l2_normalize(x, axis=1)
        )(item_embedding)
        
        self.item_model = keras.Model(inputs=item_input, outputs=item_embedding)
        return self.item_model
    
    def build_dual_tower_model(self, user_input_dim, item_input_dim):
        """构建双塔模型"""
        # 构建用户塔和物品塔
        user_tower = self.build_user_tower(user_input_dim)
        item_tower = self.build_item_tower(item_input_dim)
        
        # 获取用户和物品的Embedding
        user_embedding = user_tower.output
        item_embedding = item_tower.output
        
        # 计算相似度（点积，因为已经L2归一化，点积=余弦相似度）
        dot_product = layers.Dot(axes=1, name='dot_product')(
            [user_embedding, item_embedding]
        )
        
        # 输出层（二分类）
        output = layers.Dense(1, activation='sigmoid', name='output')(dot_product)
        
        # 组合模型
        model = keras.Model(
            inputs=[user_tower.input, item_tower.input],
            outputs=output
        )
        
        model.compile(
            optimizer=keras.optimizers.Adam(learning_rate=0.001),
            loss='binary_crossentropy',
            metrics=['accuracy', 'precision', 'recall']
        )
        
        return model
    
    def train(self, user_features, item_features, labels, epochs=20, batch_size=1024):
        """训练模型"""
        print("开始训练双塔模型...")
        
        # 划分训练集和验证集
        X_user_train, X_user_val, X_item_train, X_item_val, y_train, y_val = \
            train_test_split(
                user_features, item_features, labels,
                test_size=0.2, random_state=42, stratify=labels
            )
        
        # 构建模型
        model = self.build_dual_tower_model(
            user_features.shape[1],
            item_features.shape[1]
        )
        
        print(model.summary())
        
        # 训练
        history = model.fit(
            [X_user_train, X_item_train],
            y_train,
            validation_data=([X_user_val, X_item_val], y_val),
            epochs=epochs,
            batch_size=batch_size,
            verbose=1,
            callbacks=[
                keras.callbacks.EarlyStopping(
                    monitor='val_loss',
                    patience=5,
                    restore_best_weights=True
                ),
                keras.callbacks.ReduceLROnPlateau(
                    monitor='val_loss',
                    factor=0.5,
                    patience=3,
                    min_lr=1e-6
                )
            ]
        )
        
        # 保存模型
        os.makedirs('models', exist_ok=True)
        model.save('models/dual_tower_model.h5')
        self.user_model.save('models/user_tower.h5')
        self.item_model.save('models/item_tower.h5')
        
        # 保存编码器
        with open('models/user_encoders.pkl', 'wb') as f:
            pickle.dump(self.user_encoders, f)
        with open('models/item_encoders.pkl', 'wb') as f:
            pickle.dump(self.item_encoders, f)
        
        print("模型训练完成并已保存")
        return model, history
    
    def generate_embeddings(self, user_features_df, item_features_df, item_type='CONTEST'):
        """生成所有用户和物品的Embedding"""
        print("生成Embedding向量...")
        
        # 加载模型
        self.user_model = keras.models.load_model('models/user_tower.h5')
        self.item_model = keras.models.load_model('models/item_tower.h5')
        
        # 加载编码器
        with open('models/user_encoders.pkl', 'rb') as f:
            self.user_encoders = pickle.load(f)
        with open('models/item_encoders.pkl', 'rb') as f:
            self.item_encoders = pickle.load(f)
        
        # 处理用户特征
        user_features = self.prepare_user_features_for_inference(user_features_df)
        user_embeddings = self.user_model.predict(user_features, batch_size=1024)
        
        # 处理物品特征
        item_features = self.prepare_item_features_for_inference(item_features_df, item_type)
        item_embeddings = self.item_model.predict(item_features, batch_size=1024)
        
        print(f"用户Embedding形状: {user_embeddings.shape}")
        print(f"物品Embedding形状: {item_embeddings.shape}")
        
        return user_embeddings, item_embeddings
    
    def prepare_user_features_for_inference(self, df):
        """为推理准备用户特征"""
        features = []
        
        if 'major' in df.columns and 'major' in self.user_encoders:
            df['major_encoded'] = df['major'].map(
                lambda x: self.user_encoders['major'].transform([x])[0] 
                if x in self.user_encoders['major'].classes_ 
                else 0
            ).fillna(0)
            features.append('major_encoded')
        
        if 'grade' in df.columns:
            grade_map = {'大一': 1, '大二': 2, '大三': 3, '大四': 4}
            df['grade_encoded'] = df['grade'].map(grade_map).fillna(0)
            features.append('grade_encoded')
        
        if 'school' in df.columns and 'school' in self.user_encoders:
            df['school_encoded'] = df['school'].map(
                lambda x: self.user_encoders['school'].transform([x])[0]
                if x in self.user_encoders['school'].classes_
                else 0
            ).fillna(0)
            features.append('school_encoded')
        
        return df[features].values.astype(np.float32)
    
    def prepare_item_features_for_inference(self, df, item_type):
        """为推理准备物品特征"""
        features = []
        
        if item_type == 'CONTEST':
            if 'class_second' in df.columns and 'class' in self.item_encoders:
                df['class_encoded'] = df['class_second'].map(
                    lambda x: self.item_encoders['class'].transform([x])[0]
                    if x in self.item_encoders['class'].classes_
                    else 0
                ).fillna(0)
                features.append('class_encoded')
            
            if 'level' in df.columns:
                level_map = {'校级': 1, '市级': 2, '省级': 3, '全国性': 4, '全球性': 5}
                df['level_encoded'] = df['level'].map(level_map).fillna(0)
                features.append('level_encoded')
        
        return df[features].values.astype(np.float32)


def save_to_milvus(collection_name, embeddings, ids, host='localhost', port='19530'):
    """保存Embedding到Milvus"""
    print(f"连接Milvus: {host}:{port}")
    connections.connect("default", host=host, port=port)
    
    # 检查集合是否存在
    if utility.has_collection(collection_name):
        utility.drop_collection(collection_name)
    
    # 定义字段
    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=False),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=embeddings.shape[1])
    ]
    
    schema = CollectionSchema(fields=fields, description=f"{collection_name} embeddings")
    
    # 创建集合
    collection = Collection(name=collection_name, schema=schema)
    
    # 准备数据
    data = [
        ids.tolist(),
        embeddings.tolist()
    ]
    
    # 插入数据
    collection.insert(data)
    collection.flush()
    
    # 创建索引
    index_params = {
        "metric_type": "COSINE",
        "index_type": "IVF_FLAT",
        "params": {"nlist": 1024}
    }
    collection.create_index(field_name="embedding", index_params=index_params)
    
    # 加载集合
    collection.load()
    
    print(f"成功保存 {len(ids)} 条向量到Milvus集合: {collection_name}")


if __name__ == '__main__':
    # 配置
    ITEM_TYPE = 'CONTEST'  # 或 'JOB'
    TRAINING_DATA_PATH = 'data/training_data_contest.csv'
    USER_FEATURES_PATH = 'data/user_features.csv'
    ITEM_FEATURES_PATH = 'data/item_features_contest.csv'
    EMBEDDING_DIM = 128
    MILVUS_HOST = 'localhost'
    MILVUS_PORT = '19530'
    
    # 1. 初始化推荐器
    recommender = DualTowerRecommender(embedding_dim=EMBEDDING_DIM)
    
    # 2. 加载训练数据
    df = recommender.load_data(TRAINING_DATA_PATH)
    
    # 3. 特征工程
    user_features, item_features, labels, processed_df = \
        recommender.prepare_features(df, item_type=ITEM_TYPE)
    
    # 4. 训练模型
    model, history = recommender.train(
        user_features, item_features, labels,
        epochs=20, batch_size=1024
    )
    
    # 5. 生成所有Embedding
    user_features_df = pd.read_csv(USER_FEATURES_PATH)
    item_features_df = pd.read_csv(ITEM_FEATURES_PATH)
    
    user_embeddings, item_embeddings = recommender.generate_embeddings(
        user_features_df, item_features_df, item_type=ITEM_TYPE
    )
    
    # 6. 保存到Milvus
    user_ids = user_features_df['user_id'].values
    item_ids = item_features_df['item_id'].values
    
    save_to_milvus(
        'user_embeddings',
        user_embeddings,
        user_ids,
        host=MILVUS_HOST,
        port=MILVUS_PORT
    )
    
    save_to_milvus(
        'item_embeddings',
        item_embeddings,
        item_ids,
        host=MILVUS_HOST,
        port=MILVUS_PORT
    )
    
    print("训练完成！")