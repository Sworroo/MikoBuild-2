FROM runpod/pytorch:2.1.0-py3.10-cuda11.8.0-devel-ubuntu22.04

WORKDIR /app

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \
    git \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Копируем requirements и устанавливаем зависимости
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Предзагрузка моделей Shap-E (опционально, для быстрого холодного старта)
RUN python -c "from shap_e.models.download import load_model; load_model('transmitter', device='cpu'); load_model('text300M', device='cpu')"

# Копируем код
COPY src/ /app/src/

# Запуск handler
CMD ["python", "-u", "/app/src/handler.py"]
