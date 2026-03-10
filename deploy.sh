#!/bin/bash
# 容器名称
container_name=$1
# 镜像tag
image_tag=$2

# compose.yml 文件所在目录
#compose_dir=""

# 判断容器是否存在
if docker ps -a | grep $container_name | awk '{print $1}'; then
  echo "容器 $container_name 存在"
  if docker ps | grep $container_name | awk '{print $1}';then
     echo "关闭正在运行的容器 $container_name"
     docker stop `docker ps | grep $container_name | awk '{print $1}'`
  else
    echo "容器 $container_name 都已关闭"
  fi
  # 删除容器
  echo "删除容器 $container_name"
  docker rm `docker ps -a | grep $container_name | awk '{print $1}'`
else
  echo "容器 $container_name 不存在"
fi

# 使用 docker compose 启动服务
#echo "启动容器 $container_name"
#if [ $container_name = "zt-server" ]; then
#    cd $compose_dir
#
#    if [ -n "$image_tag" ]; then
#        # 更新 compose.yml 中的镜像版本
#        sed -i '' "s/zt-server:.*/zt-server:${image_tag}/" compose.yml
#        echo "镜像版本更新为 zt-server:${image_tag}"
#    fi
#
#    docker compose -f compose.yml up -d $container_name
#fi
