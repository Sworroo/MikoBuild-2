FROM nvidia/cuda:11.8.0-cudnn8-devel-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1

# Установка Python и зависимостей
RUN apt-get update && apt-get install -y \
    python3.10 \
    python3-pip \
    git \
    wget \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Создание рабочей директории
WORKDIR /app

# Копирование requirements
COPY requirements.txt .

# Установка Python пакетов
RUN pip3 install --no-cache-dir --upgrade pip && \
    pip3 install --no-cache-dir -r requirements.txt

# Установка hy3dgen (если это отдельный пакет)
RUN pip3 install --no-cache-dir git+https://github.com/Tencent/Hunyuan3D-2.git || \
    pip3 install hy3dgen || echo "Install hy3dgen manually if needed"

# Копирование приложения
COPY app.py .

# Создание директории для временных файлов
RUN mkdir -p /app/temp_models

# Открытие порта
EXPOSE 5000

# Запуск приложения
CMD ["python3", "app.py"]
