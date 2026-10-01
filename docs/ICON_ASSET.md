# 应用头像图标

输入素材：仓库根目录 `uni-image.png`。使用内置 imagegen 工具处理，未使用 CLI。最终图像保存为 `app/src/main/res/drawable-nodpi/uni_avatar.png`，在 `mipmap-anydpi-v26/ic_launcher.xml` 中作为自适应图标前景，10% 内缩配合浅灰背景，兼容圆形与其他系统图标形状。通知小图标继续使用适合状态栏的单色图形。

最终使用的提示词：

> Edit target: the provided uni-image.png. Create a square Android launcher portrait icon ONLY by tightly reframing/cropping this existing image and scaling it up. Preserve EXACTLY the same anime girl's face, pink-purple eyes, white hair, green accessories, clothes, original drawing and light gray background; no redesign, no added text, no border, no rounded corners. Crop empty gray margins and most of the lower torso so her head and face are larger and centered, with the face and eyes fully visible inside the central circular launcher safe area. Keep both eyes, chin, hair on both sides; top accessory tips may be cropped slightly. The girl's head should fill about 85% of the square width, showing a little collar at the bottom. Faithful source image, sharp clean rendering without changing facial features.
