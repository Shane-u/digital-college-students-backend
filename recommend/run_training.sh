#!/bin/bash
# 一键训练脚本

set -e

echo "=========================================="
echo "RecBole推荐系统训练脚本"
echo "=========================================="

# 配置
MODEL=${1:-DSSM}  # 默认使用DSSM
DATA_DIR="data/recbole"
CONFIG_FILE="config.yaml"
MILVUS_HOST=${MILVUS_HOST:-localhost}
MILVUS_PORT=${MILVUS_PORT:-19530}

echo "模型: $MODEL"
echo "数据目录: $DATA_DIR"
echo "Milvus: $MILVUS_HOST:$MILVUS_PORT"

# 检查数据文件是否存在
if [ ! -f "$DATA_DIR/recbole_data.inter" ]; then
    echo "错误: 数据文件不存在，请先运行数据准备脚本"
    echo "运行: python prepare_recbole_data.py --training_data <csv_path>"
    exit 1
fi

# 检查Milvus连接
echo "检查Milvus连接..."
if ! curl -s "http://$MILVUS_HOST:$MILVUS_PORT/healthz" > /dev/null 2>&1; then
    echo "警告: 无法连接到Milvus，Embedding将无法保存"
    echo "继续训练（不保存Embedding）..."
    SAVE_EMBEDDINGS=""
else
    echo "Milvus连接正常"
    SAVE_EMBEDDINGS="--save_embeddings"
fi

# 开始训练
echo ""
echo "开始训练..."
python train_with_recbole.py \
    --model $MODEL \
    --dataset recbole_data \
    --data_path $DATA_DIR \
    --config $CONFIG_FILE \
    $SAVE_EMBEDDINGS \
    --milvus_host $MILVUS_HOST \
    --milvus_port $MILVUS_PORT

echo ""
echo "=========================================="
echo "训练完成！"
echo "=========================================="
