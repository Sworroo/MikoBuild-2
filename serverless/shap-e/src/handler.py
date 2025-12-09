import runpod
import torch
import trimesh
import tempfile
import base64
import os
import io
import zipfile
import numpy as np

from shap_e.diffusion.sample import sample_latents
from shap_e.diffusion.gaussian_diffusion import diffusion_from_config
from shap_e.models.download import load_model, load_config
from shap_e.util.notebooks import decode_latent_mesh

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
xm = None
model = None
diffusion = None


def load_models():
    global xm, model, diffusion
    if xm is None:
        print("Загрузка моделей Shap-E...")
        xm = load_model('transmitter', device=device)
        model = load_model('text300M', device=device)
        diffusion = diffusion_from_config(load_config('diffusion'))
        print("Модели загружены!")


def generate_3d_from_prompt(prompt: str, guidance_scale: float = 15.0, num_steps: int = 64):
    load_models()
    
    latents = sample_latents(
        batch_size=1,
        model=model,
        diffusion=diffusion,
        guidance_scale=guidance_scale,
        model_kwargs=dict(texts=[prompt]),
        progress=True,
        clip_denoised=True,
        use_fp16=True,
        use_karras=True,
        karras_steps=num_steps,
        sigma_min=1e-3,
        sigma_max=160,
        s_churn=0,
    )
    
    return decode_latent_mesh(xm, latents[0]).tri_mesh()


def shap_e_to_trimesh(shap_e_mesh) -> trimesh.Trimesh:
    """Конвертация Shap-E mesh в trimesh с vertex colors"""
    vertices = np.array(shap_e_mesh.verts)
    faces = np.array(shap_e_mesh.faces)
    
    vertex_colors = None
    if hasattr(shap_e_mesh, 'vertex_channels') and shap_e_mesh.vertex_channels:
        channels = shap_e_mesh.vertex_channels
        if 'R' in channels and 'G' in channels and 'B' in channels:
            r = np.array(channels['R'])
            g = np.array(channels['G'])
            b = np.array(channels['B'])
            
            if r.max() <= 1.0:
                r = (r * 255).clip(0, 255)
                g = (g * 255).clip(0, 255)
                b = (b * 255).clip(0, 255)
            
            vertex_colors = np.column_stack([
                r.astype(np.uint8),
                g.astype(np.uint8),
                b.astype(np.uint8),
                np.full(len(r), 255, dtype=np.uint8)
            ])
    
    return trimesh.Trimesh(
        vertices=vertices,
        faces=faces,
        vertex_colors=vertex_colors
    )


def fix_rotation(mesh: trimesh.Trimesh) -> trimesh.Trimesh:
    """Поворот -90° по X"""
    rotation = trimesh.transformations.rotation_matrix(
        np.radians(-90), [1, 0, 0], point=[0, 0, 0]
    )
    mesh.apply_transform(rotation)
    return mesh


def export_mesh(mesh: trimesh.Trimesh, output_dir: str) -> dict:
    """
    Экспорт mesh с конвертацией vertex colors в текстуру
    """
    obj_path = os.path.join(output_dir, "model.obj")
    
    # ВОТ КЛЮЧЕВОЙ МОМЕНТ: конвертируем vertex colors в текстуру!
    # Это создаёт UV развёртку и текстуру из vertex colors
    if hasattr(mesh.visual, 'to_texture'):
        try:
            print("Конвертация vertex colors в текстуру...")
            mesh.visual = mesh.visual.to_texture()
            print("Конвертация успешна!")
        except Exception as e:
            print(f"Не удалось конвертировать в текстуру: {e}")
    
    # Теперь экспорт создаст OBJ + MTL + PNG
    mesh.export(obj_path, include_texture=True)
    
    # Собираем созданные файлы
    print(f"Файлы после экспорта:")
    files = {}
    
    for filename in os.listdir(output_dir):
        filepath = os.path.join(output_dir, filename)
        size = os.path.getsize(filepath)
        print(f"  - {filename} ({size} bytes)")
        
        ext = filename.lower().split('.')[-1]
        
        if ext == 'obj':
            files['obj'] = filepath
        elif ext == 'mtl':
            files['mtl'] = filepath
        elif ext == 'png' and 'preview' not in filename.lower():
            files['texture_png'] = filepath
    
    return files


def standardize_files(output_dir: str, files: dict) -> dict:
    """Переименование файлов в стандартные имена"""
    
    # Текстура -> texture.png
    if 'texture_png' in files:
        old_path = files['texture_png']
        old_name = os.path.basename(old_path)
        new_path = os.path.join(output_dir, "texture.png")
        
        if old_path != new_path:
            os.rename(old_path, new_path)
            files['texture_png'] = new_path
            
            # Обновляем MTL
            if 'mtl' in files:
                with open(files['mtl'], 'r') as f:
                    content = f.read()
                content = content.replace(old_name, "texture.png")
                with open(files['mtl'], 'w') as f:
                    f.write(content)
    
    # MTL -> model.mtl
    if 'mtl' in files:
        old_path = files['mtl']
        old_name = os.path.basename(old_path)
        new_path = os.path.join(output_dir, "model.mtl")
        
        if old_path != new_path:
            os.rename(old_path, new_path)
            files['mtl'] = new_path
            
            # Обновляем OBJ
            if 'obj' in files:
                with open(files['obj'], 'r') as f:
                    content = f.read()
                content = content.replace(old_name, "model.mtl")
                with open(files['obj'], 'w') as f:
                    f.write(content)
    
    return files


def create_preview(mesh: trimesh.Trimesh, output_dir: str) -> str:
    """Превью"""
    preview_path = os.path.join(output_dir, "preview.png")
    try:
        scene = mesh.scene()
        png_data = scene.save_image(resolution=[512, 512])
        if png_data:
            with open(preview_path, 'wb') as f:
                f.write(png_data)
            return preview_path
    except Exception as e:
        print(f"Превью не создано: {e}")
    return None


def files_to_base64(file_paths: dict) -> dict:
    result = {}
    for key, filepath in file_paths.items():
        if filepath and os.path.exists(filepath):
            with open(filepath, 'rb') as f:
                result[key] = base64.b64encode(f.read()).decode('utf-8')
    return result


def create_zip(output_dir: str) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as zf:
        for filename in os.listdir(output_dir):
            zf.write(os.path.join(output_dir, filename), filename)
    buffer.seek(0)
    return buffer.read()


def handler(job):
    job_input = job.get("input", {})
    
    prompt = job_input.get("prompt")
    guidance_scale = job_input.get("guidance_scale", 15.0)
    num_steps = job_input.get("num_steps", 64)
    include_zip = job_input.get("include_zip", True)
    fix_rotation_enabled = job_input.get("fix_rotation", True)
    
    if not prompt:
        return {"status": "error", "error": "Параметр 'prompt' обязателен"}
    
    try:
        with tempfile.TemporaryDirectory() as temp_dir:
            # 1. Генерация
            print(f"Генерация: {prompt}")
            shap_e_mesh = generate_3d_from_prompt(prompt, guidance_scale, num_steps)
            
            # 2. Конвертация в trimesh
            print("Конвертация в trimesh...")
            mesh = shap_e_to_trimesh(shap_e_mesh)
            
            # 3. Поворот
            if fix_rotation_enabled:
                print("Исправление ориентации...")
                mesh = fix_rotation(mesh)
            
            # 4. Экспорт (с конвертацией vertex colors -> texture)
            print("Экспорт...")
            files = export_mesh(mesh, temp_dir)
            
            # 5. Стандартизация имён
            files = standardize_files(temp_dir, files)
            
            # 6. Превью
            preview = create_preview(mesh, temp_dir)
            if preview:
                files['preview_png'] = preview
            
            print(f"Итого файлов: {list(files.keys())}")
            
            # 7. Base64
            files_b64 = files_to_base64(files)
            
            response = {
                "status": "success",
                "prompt": prompt,
                "files": files_b64,
                "message": "Модель сгенерирована"
            }
            
            if include_zip:
                response["zip_archive"] = base64.b64encode(create_zip(temp_dir)).decode('utf-8')
            
            return response
            
    except Exception as e:
        import traceback
        return {
            "status": "error",
            "error": str(e),
            "traceback": traceback.format_exc()
        }


runpod.serverless.start({"handler": handler})
