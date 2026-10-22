import json

with open(r'C:\Users\renop\Downloads\renotube\led_layout.json') as f:
    boxes = json.load(f)

print(f"Total boxes: {len(boxes)}")

with open(r'C:\Users\renop\Downloads\renotube\HyperHdrBoxes.kt', 'w') as out:
    out.write("// Auto-generated from user HyperHDR led_layout.json (228 LEDs)\n")
    out.write("package com.renotube.tv.ambilight\n\n")
    out.write("val HYPER_HDR_228_BOXES: Array<AmbilightProcessor.LedBox> = arrayOf(\n")
    for i, b in enumerate(boxes):
        hmin = float(b['hmin'])
        hmax = float(b['hmax'])
        vmin = float(b['vmin'])
        vmax = float(b['vmax'])
        out.write(f"    AmbilightProcessor.LedBox({hmin:.6f}f, {hmax:.6f}f, {vmin:.6f}f, {vmax:.6f}f),\n")
    out.write(")\n")

print("Generated HyperHdrBoxes.kt successfully!")
