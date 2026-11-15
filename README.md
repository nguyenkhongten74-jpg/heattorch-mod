# Heat Torch (Đuốc Rực Lửa) – Fabric mod cho Minecraft Java 26.3

## Yêu cầu
- **JDK 25** (bắt buộc cho Minecraft 26.x)
- Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.166.2+26.3** (hoặc mới hơn)

## Build ra file .jar
Mở terminal trong thư mục dự án:

```
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

File mod nằm ở `build/libs/heattorch-1.0.0.jar` (đừng dùng file `-sources.jar`).
Bỏ vào thư mục `mods` cùng với Fabric API.

Chạy thử trong môi trường dev: `./gradlew runClient`.

Dự án cũng mở được bằng IntelliJ IDEA (Open → chọn thư mục này, đặt Gradle JVM = JDK 25).

## Công thức chế tạo
Xếp chéo từ góc trên-phải xuống góc dưới-trái:
```
. . B       B = Khối Than
. S .       S = Que
S . .
```

## Cách lấy enchant
- **Reverse**, **Critical Melt**: bàn enchant (hoặc `/enchant @s heattorch:reverse`).
- **Internal Cooldown I**: rương tàu End City (25% có sách). Ghép 2 sách I ở anvil ra II.
- Lấy nhanh để thử: `/give @s enchanted_book[stored_enchantments={"heattorch:internal_cooldown":1}]`

## Cơ chế đã làm
- Sát thương tổng 7, tốc độ đánh tổng 1.5, độ bền 32, không knockback (kể cả sweep), không phá/xuyên khiên.
- Đánh thường: gây cháy 6s (Fire Aspect I/II: 8s/10s), không cộng dồn.
- Mục tiêu đang freeze (`ticksFrozen > 0`): x2 sát thương, xóa freeze, hồi chiêu 2.5s theo từng mục tiêu. Trong lúc hồi chiêu vẫn triệt tiêu trạng thái nhưng không x2.
- Reverse: ngược lại (gây freeze 6s, x2 khi mục tiêu đang cháy). Không ghép được với Fire Aspect.
- Critical Melt: chỉ cộng hệ số chí mạng khi đòn vừa chí mạng vừa là melt (+0.4 / +0.8 / +1.2 vào 1.5).
- Internal Cooldown: hồi chiêu melt 2.25s / 2s.
- Sweep attack cũng melt được (tính theo sát thương sweep của từng mục tiêu).
- Mặc da (dù 1 mảnh) → `canFreeze()` = false nên không bị freeze; xuống nước hoặc vào tuyết lún thì mất cháy – đều là cơ chế gốc của Minecraft.

## Các hằng số dễ chỉnh
- `HeatTorchCombat.java`: thời gian cháy/freeze, hồi chiêu, hệ số x2, hệ số crit.
- `ModItems.java`: `ATTACK_DAMAGE_MODIFIER` (6.0 = tổng 7) và `ATTACK_SPEED_MODIFIER` (-2.5 = tổng 1.5).
- `data/heattorch/tags/entity_type/naturally_burning.json`: danh sách sinh vật "cháy sẵn" (Blaze, Magma Cube).

## Giới hạn đã biết
- Vì đuốc phải nằm trong tag `swords` để có sweep attack, nó cũng nhận được cả Smite, Bane of Arthropods, Knockback (vô tác dụng vì đã chặn knockback), và thêm Density / Wind Burst do thêm vào tag `enchantable/mace` để có Breach.
- Phát hiện đòn chí mạng được tính lại theo điều kiện của Minecraft gốc (nhảy rơi, không chạy, không ở dưới nước...). Nếu bản 26.3 đổi điều kiện này, Critical Melt có thể lệch.
