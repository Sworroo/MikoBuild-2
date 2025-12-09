import sys
sys.path.insert(0, 'src')

from handler import handler
import json
import base64
import os

# Загружаем тестовый input
with open('test_input.json', 'r') as f:
    test_job = json.load(f)

# Запускаем handler
result = handler(test_job)

print("Результат:", json.dumps(result, indent=2, ensure_ascii=False)[:500])

# Сохраняем файлы локально
if result.get("status") == "success":
    output_dir = "output"
    os.makedirs(output_dir, exist_ok=True)
    
    for filename, b64_content in result["files"].items():
        filepath = os.path.join(output_dir, filename)
        with open(filepath, 'wb') as f:
            f.write(base64.b64decode(b64_content))
        print(f"Сохранён: {filepath}")
