package com.zeticai.yolo26seg

object Constants {
    const val ZETIC_ACCESS_TOKEN = "ztp_374183520dc34acea84fe112ea577350"
    const val MODEL_ID = "vaibhav-zetic/yolo26-seg"
    
    // Inference Parameters
    const val INPUT_SIZE = 640
    const val CONF_THRESHOLD = 0.35f
    const val IOU_THRESHOLD = 0.45f
    const val MASK_THRESHOLD = 0.5f
    
    // COCO Labels (80 classes)
    val LABELS = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train",
        "truck", "boat", "traffic light", "fire hydrant", "stop sign",
        "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella",
        "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard",
        "sports ball", "kite", "baseball bat", "baseball glove", "skateboard",
        "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork",
        "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
        "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
        "toaster", "sink", "refrigerator", "book", "clock", "vase",
        "scissors", "teddy bear", "hair drier", "toothbrush"
    )
}
