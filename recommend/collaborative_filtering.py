# collaborative_filtering.py
import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from scipy.sparse import csr_matrix
import pickle

class CollaborativeFiltering:
    def __init__(self):
        self.user_item_matrix = None
        self.user_similarity = None
        self.item_similarity = None
        
    def build_user_item_matrix(self, df):
        """构建用户-物品交互矩阵"""
        print("构建用户-物品矩阵...")
        
        # 计算用户对物品的偏好分数（加权）
        behavior_weights = {
            'VIEW': 1.0,
            'CLICK': 2.0,
            'SHARE': 3.0,
            'COLLECT': 5.0,
            'APPLY': 10.0
        }
        
        df['weight'] = df['behavior_type'].map(behavior_weights)
        
        # 聚合：同一用户对同一物品的多次行为，取最大值或求和
        user_item_scores = df.groupby(['user_id', 'item_id'])['weight'].sum().reset_index()
        
        # 构建稀疏矩阵
        users = user_item_scores['user_id'].unique()
        items = user_item_scores['item_id'].unique()
        
        user_to_idx = {u: i for i, u in enumerate(users)}
        item_to_idx = {it: i for i, it in enumerate(items)}
        
        rows = [user_to_idx[u] for u in user_item_scores['user_id']]
        cols = [item_to_idx[it] for it in user_item_scores['item_id']]
        data = user_item_scores['weight'].values
        
        self.user_item_matrix = csr_matrix(
            (data, (rows, cols)),
            shape=(len(users), len(items))
        )
        
        self.user_ids = users
        self.item_ids = items
        self.user_to_idx = user_to_idx
        self.item_to_idx = item_to_idx
        
        print(f"矩阵形状: {self.user_item_matrix.shape}")
        print(f"稀疏度: {(1 - self.user_item_matrix.nnz / (len(users) * len(items))) * 100:.2f}%")
        
        return self.user_item_matrix
    
    def compute_user_similarity(self):
        """计算用户相似度矩阵"""
        print("计算用户相似度...")
        # 使用余弦相似度
        self.user_similarity = cosine_similarity(self.user_item_matrix)
        return self.user_similarity
    
    def compute_item_similarity(self):
        """计算物品相似度矩阵"""
        print("计算物品相似度...")
        # 转置矩阵计算物品相似度
        self.item_similarity = cosine_similarity(self.user_item_matrix.T)
        return self.item_similarity
    
    def user_based_recommend(self, user_id, top_k=10, n_similar_users=20):
        """基于用户的协同过滤推荐"""
        if user_id not in self.user_to_idx:
            return []
        
        user_idx = self.user_to_idx[user_id]
        
        # 获取相似用户
        user_sim_scores = self.user_similarity[user_idx]
        similar_users_idx = np.argsort(user_sim_scores)[::-1][1:n_similar_users+1]
        
        # 计算推荐分数
        recommendations = {}
        user_vector = self.user_item_matrix[user_idx].toarray().flatten()
        
        for sim_user_idx in similar_users_idx:
            similarity = user_sim_scores[sim_user_idx]
            sim_user_vector = self.user_item_matrix[sim_user_idx].toarray().flatten()
            
            # 只推荐用户未交互过的物品
            for item_idx, score in enumerate(sim_user_vector):
                if score > 0 and user_vector[item_idx] == 0:
                    item_id = self.item_ids[item_idx]
                    if item_id not in recommendations:
                        recommendations[item_id] = 0
                    recommendations[item_id] += similarity * score
        
        # 排序并返回Top-K
        sorted_recommendations = sorted(
            recommendations.items(),
            key=lambda x: x[1],
            reverse=True
        )[:top_k]
        
        return [(item_id, score) for item_id, score in sorted_recommendations]
    
    def item_based_recommend(self, user_id, top_k=10):
        """基于物品的协同过滤推荐"""
        if user_id not in self.user_to_idx:
            return []
        
        user_idx = self.user_to_idx[user_id]
        user_vector = self.user_item_matrix[user_idx].toarray().flatten()
        
        # 用户已交互的物品
        interacted_items_idx = np.where(user_vector > 0)[0]
        
        # 计算推荐分数
        recommendations = {}
        for item_idx in interacted_items_idx:
            item_sim_scores = self.item_similarity[item_idx]
            
            for other_item_idx, similarity in enumerate(item_sim_scores):
                if other_item_idx not in interacted_items_idx and similarity > 0:
                    item_id = self.item_ids[other_item_idx]
                    if item_id not in recommendations:
                        recommendations[item_id] = 0
                    recommendations[item_id] += similarity * user_vector[item_idx]
        
        # 排序并返回Top-K
        sorted_recommendations = sorted(
            recommendations.items(),
            key=lambda x: x[1],
            reverse=True
        )[:top_k]
        
        return [(item_id, score) for item_id, score in sorted_recommendations]
    
    def hybrid_recommend(self, user_id, top_k=10, alpha=0.7):
        """混合推荐：结合用户协同和物品协同"""
        user_based = dict(self.user_based_recommend(user_id, top_k * 2))
        item_based = dict(self.item_based_recommend(user_id, top_k * 2))
        
        # 合并结果
        all_items = set(user_based.keys()) | set(item_based.keys())
        hybrid_scores = {}
        
        for item_id in all_items:
            user_score = user_based.get(item_id, 0)
            item_score = item_based.get(item_id, 0)
            # 加权融合
            hybrid_scores[item_id] = alpha * user_score + (1 - alpha) * item_score
        
        # 排序并返回Top-K
        sorted_recommendations = sorted(
            hybrid_scores.items(),
            key=lambda x: x[1],
            reverse=True
        )[:top_k]
        
        return [(item_id, score) for item_id, score in sorted_recommendations]
    
    def save_model(self, filepath):
        """保存模型"""
        with open(filepath, 'wb') as f:
            pickle.dump({
                'user_item_matrix': self.user_item_matrix,
                'user_similarity': self.user_similarity,
                'item_similarity': self.item_similarity,
                'user_ids': self.user_ids,
                'item_ids': self.item_ids,
                'user_to_idx': self.user_to_idx,
                'item_to_idx': self.item_to_idx
            }, f)
        print(f"模型已保存到: {filepath}")
    
    def load_model(self, filepath):
        """加载模型"""
        with open(filepath, 'rb') as f:
            data = pickle.load(f)
            self.user_item_matrix = data['user_item_matrix']
            self.user_similarity = data['user_similarity']
            self.item_similarity = data['item_similarity']
            self.user_ids = data['user_ids']
            self.item_ids = data['item_ids']
            self.user_to_idx = data['user_to_idx']
            self.item_to_idx = data['item_to_idx']
        print(f"模型已从 {filepath} 加载")


if __name__ == '__main__':
    # 加载数据
    df = pd.read_csv('data/training_data_contest.csv')
    
    # 初始化协同过滤
    cf = CollaborativeFiltering()
    
    # 构建矩阵
    cf.build_user_item_matrix(df)
    
    # 计算相似度
    cf.compute_user_similarity()
    cf.compute_item_similarity()
    
    # 保存模型
    cf.save_model('models/collaborative_filtering.pkl')
    
    # 测试推荐
    test_user_id = df['user_id'].iloc[0]
    recommendations = cf.hybrid_recommend(test_user_id, top_k=10)
    print(f"用户 {test_user_id} 的推荐结果:")
    for item_id, score in recommendations:
        print(f"  物品 {item_id}: {score:.4f}")