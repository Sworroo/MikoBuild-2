from flask import Flask, request, jsonify
import torch
import os
import base64
import uuid
from hy3dgen.rembg import BackgroundRemover
from hy3dgen.shapegen import Hunyuan3DDiTFlowMatchingPipeline, FaceReducer, FloaterRemover, DegenerateFaceRemover
from hy3dgen.texgen.pipelines import Hunyuan3DPaintPipeline
from hy3dgen.text2image import HunyuanDiTPipeline


app = Flask(__name__)

# Load models - this happens once when the API starts
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"Using device: {device}")

# Create a directory for temporary files
TEMP_DIR = "temp_models"
os.makedirs(TEMP_DIR, exist_ok=True)

def generate_3d_model(prompt):
    rembg = BackgroundRemover()
    t2i = HunyuanDiTPipeline()
    model_path = 'tencent/Hunyuan3D-2'
    image = t2i(prompt)
    print('image generated')
    image = rembg(image)
    print('removed background from image')
    print('clearing text to image model (to prevent lags on full vram)')
    del t2i
    del rembg
    torch.cuda.empty_cache()
    i23d = Hunyuan3DDiTFlowMatchingPipeline.from_pretrained(model_path)
    mesh = i23d(image,     
        num_inference_steps=50,
        octree_resolution=380,
        num_chunks=20000, mc_algo='mc')[0]
    print('mesh generated')
    print('clearing image to 3d model (to prevent lags on full vram)')
    del i23d
    torch.cuda.empty_cache()
    pipeline_texgen = Hunyuan3DPaintPipeline.from_pretrained(model_path)
    mesh = FloaterRemover()(mesh)
    mesh = DegenerateFaceRemover()(mesh)
    mesh = FaceReducer()(mesh)
    mesh = pipeline_texgen(mesh, image=image)
    del pipeline_texgen
    torch.cuda.empty_cache()
    return mesh

def extract_model_with_texture(mesh, output_dir):
    """Extract model with separate texture file using original vertex colors"""
    model_id = str(uuid.uuid4())
    os.makedirs(output_dir, exist_ok=True)
    
    obj_path = os.path.join(output_dir, f"{model_id}.obj")
    mtl_path = os.path.join(output_dir, f"material.mtl")
    texture_path = os.path.join(output_dir, f"material_0.png")

    mesh.export(f"{output_dir}/{model_id}.obj", include_texture=True)
    
    return {
        'obj_path': obj_path,
        'mtl_path': mtl_path,
        'texture_path': texture_path,
        'model_id': model_id
    }

def file_to_base64(file_path):
    """Convert a file to base64 encoding"""
    with open(file_path, 'rb') as f:
        file_data = f.read()
    return base64.b64encode(file_data).decode('utf-8')

@app.route('/generate', methods=['POST'])
def generate():
    try:
        data = request.json
        prompt = data.get('prompt', '')
        
        if not prompt:
            return jsonify({"error": "No prompt provided"}), 400
        
        # Generate the 3D mesh from the prompt
        mesh = generate_3d_model(prompt)
        
        # Create a temp directory for this specific generation
        gen_id = str(uuid.uuid4())
        output_dir = os.path.join(TEMP_DIR, gen_id)
        os.makedirs(output_dir, exist_ok=True)
        
        # Extract model with separate texture file
        files = extract_model_with_texture(mesh, output_dir)
        
        # Prepare response with file data
        response = {
            "success": True,
            "prompt": prompt,
            "files": []
        }
        
        # Add OBJ file
        obj_data = file_to_base64(files['obj_path'])
        response["files"].append({
            "name": os.path.basename(files['obj_path']),
            "type": "obj",
            "data": obj_data
        })
        
        # Add MTL file
        mtl_data = file_to_base64(files['mtl_path'])
        response["files"].append({
            "name": os.path.basename(files['mtl_path']),
            "type": "mtl",
            "data": mtl_data
        })
        
        # Add texture file
        texture_data = file_to_base64(files['texture_path'])
        response["files"].append({
            "name": os.path.basename(files['texture_path']),
            "type": "texture",
            "data": texture_data
        })
        
        # Format the main file in the format expected by the Java code
        response["file"] = {
            "name": os.path.basename(files['obj_path']),
            "data": obj_data
        }
        
        return jsonify(response)
    
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/generate-obj', methods=['POST'])
def generate_obj():
    try:
        data = request.json
        prompt = data.get('prompt', '')
        
        if not prompt:
            return jsonify({"error": "No prompt provided"}), 400
        
        # Generate the 3D mesh from the prompt
        mesh = generate_3d_model(prompt)
        
        # Create a temp directory for this specific generation
        gen_id = str(uuid.uuid4())
        output_dir = os.path.join(TEMP_DIR, gen_id)
        os.makedirs(output_dir, exist_ok=True)
        
        # Extract model with separate texture file
        files = extract_model_with_texture(mesh, output_dir)
        
        # Format the response exactly as expected by the Java code
        obj_data = file_to_base64(files['obj_path'])
        response = {
            "file": {
                "name": os.path.basename(files['obj_path']),
                "data": obj_data
            }
        }
        
        return jsonify(response)
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
