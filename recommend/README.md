# RecBole推荐系统训练指南

## 一、环境准备

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 确保Milvus已启动

```bash
# 检查Milvus是否运行
docker ps | grep milvus
# 或
curl http://localhost:19530/healthz
```

## 二、数据准备

### 1. 从Java服务导出数据

```bash
# 导出竞赛推荐训练数据
curl -X POST "http://localhost:8121/api/data-export/contest" \
  -d "outputPath=/tmp/training_data_contest.csv"

# 导出用户特征数据
curl -X POST "http://localhost:8121/api/data-export/user-features" \
  -d "outputPath=/tmp/user_features.csv"

# 导出物品特征数据
curl -X POST "http://localhost:8121/api/data-export/item-features" \
  -d "itemType=CONTEST&outputPath=/tmp/item_features_contest.csv"
```

### 2. 转换为RecBole格式

```bash
# 准备RecBole数据格式
python prepare_recbole_data.py \
  --training_data data/training_data_contest.csv \
  --user_features data/user_features.csv \
  --item_features data/item_features_contest.csv \
  --item_type CONTEST \
  --output_dir data/recbole
```

生成的文件：
- `data/recbole/recbole_data.inter` - 交互数据（必需）
- `data/recbole/recbole_data.user` - 用户特征（可选）
- `data/recbole/recbole_data.item` - 物品特征（可选）

## 三、训练模型

### 1. 使用DSSM双塔模型（推荐）

```bash
python train_with_recbole.py \
  --model DSSM \
  --dataset recbole_data \
  --data_path data/recbole \
  --config config.yaml \
  --save_embeddings \
  --milvus_host localhost \
  --milvus_port 19530
```

### 2. 使用其他模型

```bash
# BPR模型（协同过滤）
python train_with_recbole.py --model BPR --save_embeddings

# DeepFM模型（特征交叉）
python train_with_recbole.py --model DeepFM --save_embeddings

# NGCF模型（图神经网络）
python train_with_recbole.py --model NGCF --save_embeddings
```

## 四、支持的模型

| 模型 | 类型 | 说明 | 适用场景 |
|------|------|------|----------|
| **DSSM** | 双塔 | 深度学习语义匹配 | 内容推荐、冷启动 |
| **BPR** | 协同过滤 | 贝叶斯个性化排序 | 有用户行为数据 |
| **DeepFM** | 特征交叉 | 深度因子分解机 | 特征丰富的场景 |
| **NGCF** | 图神经网络 | 神经图协同过滤 | 复杂关系建模 |
| **LightGCN** | 图神经网络 | 轻量级图卷积 | 大规模推荐 |

## 五、配置文件说明

`config.yaml` 包含以下主要配置：

- **模型配置**：embedding_size, mlp_hidden_size, dropout_prob
- **训练配置**：learning_rate, batch_size, epochs
- **评估配置**：metrics, topk, 数据划分比例

可以根据需要修改配置文件。

## 六、训练流程

1. **数据准备**：将CSV转换为RecBole格式
2. **模型训练**：使用RecBole训练模型
3. **模型评估**：在测试集上评估性能
4. **生成Embedding**：为所有用户和物品生成向量
5. **保存到Milvus**：将向量保存到Milvus向量数据库

## 七、输出结果

训练完成后会生成：

- **模型文件**：`saved/DSSM-{timestamp}/` 目录
- **日志文件**：`log/` 目录
- **Milvus集合**：
  - `user_embeddings` - 用户向量集合
  - `item_embeddings` - 物品向量集合

## 八、Java服务调用

训练完成后，Java服务可以直接从Milvus检索推荐：

```java
// 从Milvus获取用户向量并推荐
List<Float> userVector = getUserEmbedding(userId);
List<Long> recommendedItems = searchSimilarItems(userVector, topK);
```
