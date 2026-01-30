import torch
from transformers import AutoModelForImageTextToText, AutoProcessor

model_id = "google/translategemma-4b-it"
processor = AutoProcessor.from_pretrained(model_id)
model = AutoModelForImageTextToText.from_pretrained(model_id, device_map="auto")


# ---- Text Translation ----
messages = [
    {
        "role": "user",
        "content": [
            {
                "type": "text",
                "source_lang_code": "cs",
                "target_lang_code": "de-DE",
                "text": "V nejhorším případě i k prasknutí čočky.",
            }
        ],
    }
]

inputs = processor.apply_chat_template(
    messages, tokenize=True, add_generation_prompt=True, return_dict=True, return_tensors="pt"
).to(model.device, dtype=torch.bfloat16)
input_len = len(inputs['input_ids'][0])

with torch.inference_mode():
    generation = model.generate(**inputs, do_sample=False)

generation = generation[0][input_len:]
decoded = processor.decode(generation, skip_special_tokens=True)
print(decoded)

# ---- Text Extraction and Translation ----
messages = [
    {
        "role": "user",
        "content": [
            {
                "type": "image",
                "source_lang_code": "cs",
                "target_lang_code": "de-DE",
                "url": "https://c7.alamy.com/comp/2YAX36N/traffic-signs-in-czech-republic-pedestrian-zone-2YAX36N.jpg",
            },
        ],
    }
]

inputs = processor.apply_chat_template(
    messages, tokenize=True, add_generation_prompt=True, return_dict=True, return_tensors="pt"
).to(model.device, dtype=torch.bfloat16)

with torch.inference_mode():
    generation = model.generate(**inputs, do_sample=False)

generation = generation[0][input_len:]
decoded = processor.decode(generation, skip_special_tokens=True)
print(decoded)
