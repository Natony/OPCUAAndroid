# Chương trình PLC – Hệ thống 4 băng tải vận chuyển Pallet (Ladder Logic)

> Viết cho PLC Siemens S7-1200/1500 (TIA Portal). Logic là chuẩn nên có thể chuyển sang
> Mitsubishi / Omron / Delta dễ dàng – chỉ cần đổi địa chỉ I/O.
> Kèm bản **LAD (ASCII)** ở dưới và bản **SCL tương đương** trong file `BangTai_4.scl`.

---

## 1. Mô tả & giả định thiết kế

Hệ thống gồm **4 băng tải nối tiếp thành 1 tuyến** (CV1–CV4). Pallet có thể chạy theo
**chiều thuận** (CV1 → CV2 → CV3 → CV4) hoặc **chiều nghịch** (CV4 → CV3 → CV2 → CV1).

```
   CHIỀU THUẬN  ───────────────────────────────────────►
   ┌───────┐   ┌───────┐   ╔═══════╗   ┌───────┐   ┌───────┐
   │  CV1  │   │  CV2  │   ║ CỬA   ║   │  CV3  │   │  CV4  │
   │ S1A S1B│  │ S2A S2B│  ║ NÂNG  ║  │ S3A S3B│  │ S4A S4B│
   └───────┘   └───────┘   ╚═══════╝   └───────┘   └───────┘
   ◄───────────────────────────────────────  CHIỀU NGHỊCH

   • A = đầu trái mỗi băng,  B = đầu phải mỗi băng (mỗi băng có 2 cảm biến).
   • CHIỀU THUẬN : nạp pallet ở S1A (trái CV1), xả ở S4B (phải CV4).
   • CHIỀU NGHỊCH: nạp pallet ở S4B (phải CV4), xả ở S1A (trái CV1).
   • CỬA NÂNG nằm GIỮA tuyến, ở ranh giới CV2 ↔ CV3.
```

**Các giả định (sửa lại nếu khác phần cứng của bạn):**

1. Mỗi băng tải có **2 cảm biến** (đầu A và đầu B) phát hiện pallet → tổng 8 cảm biến.
2. Có **2 trạm vận hành** (2 đầu tuyến), mỗi trạm **2 box nút nhấn** → 4 box.
   Mỗi box: 1 nút **Thuận (FWD)**, 1 nút **Nghịch (REV)**, 1 nút **Khẩn (EMG)**.
   - Tất cả nút **Thuận** đấu song song (OR) → 1 lệnh `FWD_PB`.
   - Tất cả nút **Nghịch** đấu song song (OR) → 1 lệnh `REV_PB`.
   - Tất cả nút **Khẩn (NC)** đấu nối tiếp (chuỗi an toàn) → bất kỳ nút nào nhấn là dừng toàn bộ.
3. **Cửa nâng** có cơ cấu nâng (xy-lanh/motor) nhận lệnh `Gate_Up`, và trả về
   tín hiệu **`Gate_Up_FB`** (cửa đã nâng hết) – chỉ khi tín hiệu này = 1 thì hàng mới
   được phép đi qua ranh giới CV2 ↔ CV3.
4. **Chức năng tách hàng (accumulation/zero-pressure):** mỗi băng = 1 "zone".
   Một zone **chỉ đẩy pallet sang zone kế tiếp khi zone đó đang trống** → các pallet
   luôn giữ khoảng cách, không dồn cục, không va chạm.

---

## 2. Bảng địa chỉ I/O (Tag Table)

### Ngõ vào (Digital Input)

| Địa chỉ | Tên (Symbol) | Mô tả |
|--------|--------------|-------|
| I0.0 | `S1A` | Cảm biến CV1 – đầu A (trái) |
| I0.1 | `S1B` | Cảm biến CV1 – đầu B (phải) |
| I0.2 | `S2A` | Cảm biến CV2 – đầu A (trái) |
| I0.3 | `S2B` | Cảm biến CV2 – đầu B (phải) – sát cửa |
| I0.4 | `S3A` | Cảm biến CV3 – đầu A (trái) – sát cửa |
| I0.5 | `S3B` | Cảm biến CV3 – đầu B (phải) |
| I0.6 | `S4A` | Cảm biến CV4 – đầu A (trái) |
| I0.7 | `S4B` | Cảm biến CV4 – đầu B (phải) |
| I1.0 | `PB_L1_FWD` | Nút Thuận – Box L1 |
| I1.1 | `PB_L1_REV` | Nút Nghịch – Box L1 |
| I1.2 | `PB_L2_FWD` | Nút Thuận – Box L2 |
| I1.3 | `PB_L2_REV` | Nút Nghịch – Box L2 |
| I1.4 | `PB_R1_FWD` | Nút Thuận – Box R1 |
| I1.5 | `PB_R1_REV` | Nút Nghịch – Box R1 |
| I1.6 | `PB_R2_FWD` | Nút Thuận – Box R2 |
| I1.7 | `PB_R2_REV` | Nút Nghịch – Box R2 |
| I2.0 | `EMG_L1` | Nút khẩn Box L1 (NC, đóng = bình thường) |
| I2.1 | `EMG_L2` | Nút khẩn Box L2 (NC) |
| I2.2 | `EMG_R1` | Nút khẩn Box R1 (NC) |
| I2.3 | `EMG_R2` | Nút khẩn Box R2 (NC) |
| I2.4 | `Gate_Up_FB` | Phản hồi: CỬA ĐÃ NÂNG HẾT |
| I2.5 | `Gate_Dn_FB` | Phản hồi: cửa đã hạ hết (tùy chọn) |

### Ngõ ra (Digital Output)

| Địa chỉ | Tên (Symbol) | Mô tả |
|--------|--------------|-------|
| Q0.0 | `CV1_FWD` | Motor CV1 chạy thuận |
| Q0.1 | `CV1_REV` | Motor CV1 chạy nghịch |
| Q0.2 | `CV2_FWD` | Motor CV2 chạy thuận |
| Q0.3 | `CV2_REV` | Motor CV2 chạy nghịch |
| Q0.4 | `CV3_FWD` | Motor CV3 chạy thuận |
| Q0.5 | `CV3_REV` | Motor CV3 chạy nghịch |
| Q0.6 | `CV4_FWD` | Motor CV4 chạy thuận |
| Q0.7 | `CV4_REV` | Motor CV4 chạy nghịch |
| Q1.0 | `Gate_Up` | Lệnh NÂNG cửa |
| Q1.1 | `Lamp_FWD` | Đèn báo đang chạy thuận (tùy chọn) |
| Q1.2 | `Lamp_REV` | Đèn báo đang chạy nghịch (tùy chọn) |
| Q1.3 | `Lamp_EMG` | Đèn báo sự cố khẩn (tùy chọn) |

### Bit trung gian (Memory)

| Địa chỉ | Tên | Mô tả |
|--------|-----|-------|
| M10.0 | `Safety_OK` | Chuỗi an toàn lành mạnh (không có nút khẩn nào nhấn) |
| M10.1 | `FWD_PB` | Có ít nhất 1 nút Thuận đang nhấn |
| M10.2 | `REV_PB` | Có ít nhất 1 nút Nghịch đang nhấn |
| M10.3 | `Line_Empty` | Toàn tuyến không có pallet |
| M10.4 | `FWD_Mode` | Chế độ chạy THUẬN đang khóa (latched) |
| M10.5 | `REV_Mode` | Chế độ chạy NGHỊCH đang khóa (latched) |
| M11.0 | `Occ1` | CV1 có pallet (S1A OR S1B) |
| M11.1 | `Occ2` | CV2 có pallet |
| M11.2 | `Occ3` | CV3 có pallet |
| M11.3 | `Occ4` | CV4 có pallet |
| M12.0 | `REL1F` | CV1 được phép đẩy pallet sang CV2 (thuận) |
| M12.1 | `REL2F` | CV2 được phép đẩy sang CV3 (thuận, qua cửa) |
| M12.2 | `REL3F` | CV3 được phép đẩy sang CV4 (thuận) |
| M12.3 | `REL4F` | CV4 xả pallet ra ngoài (thuận) |
| M13.0 | `REL4R` | CV4 đẩy sang CV3 (nghịch) |
| M13.1 | `REL3R` | CV3 đẩy sang CV2 (nghịch, qua cửa) |
| M13.2 | `REL2R` | CV2 đẩy sang CV1 (nghịch) |
| M13.3 | `REL1R` | CV1 xả pallet ra ngoài (nghịch) |

---

## 3. Nguyên lý hoạt động (tóm tắt)

- **Khởi động THUẬN:** đặt pallet lên `S1A` (cảm biến đầu tiên của chiều thuận) **và**
  nhấn nút Thuận → khóa `FWD_Mode`. Tuyến chạy thuận cho tới khi hết pallet (`Line_Empty`)
  hoặc nhấn khẩn.
- **Khởi động NGHỊCH:** đặt pallet lên `S4B` **và** nhấn nút Nghịch → khóa `REV_Mode`.
- **Khóa chéo (interlock):** không cho phép vừa thuận vừa nghịch cùng lúc.
- **Tách hàng:** zone i chỉ chạy đẩy hàng khi zone kế tiếp trống (`/Occ`), nhờ vậy
  pallet luôn cách nhau ≥ 1 zone.
- **Cửa giữa:** khi pallet tới ranh giới giữa (`S2B` thuận / `S3A` nghịch) → ra lệnh
  `Gate_Up`. Băng chỉ được phép đẩy hàng qua ranh giới CV2↔CV3 **khi `Gate_Up_FB` = 1**
  (cửa đã nâng hết). Hết pallet ở ranh giới → cửa hạ.
- **An toàn:** mất `Safety_OK` → reset cả 2 chế độ và cắt toàn bộ motor ngay lập tức.

---

## 4. CHƯƠNG TRÌNH LADDER (LAD)

Ký hiệu: `─] [─` tiếp điểm thường mở (NO) · `─]/[─` tiếp điểm thường đóng (NC) ·
`─( )─` cuộn dây · `─(S)─`/`─(R)─` set/reset.

### Network 1 — Chuỗi an toàn (Emergency chain)
> 4 nút khẩn đấu NC nối tiếp. Tất cả đóng → `Safety_OK` = 1.
```
  EMG_L1   EMG_L2   EMG_R1   EMG_R2                              Safety_OK
───] [──────] [──────] [──────] [────────────────────────────────( )──────
```

### Network 2 — Gom nút Thuận
```
  PB_L1_FWD                                                        FWD_PB
───] [───┬───────────────────────────────────────────────────────( )──────
  PB_L2_FWD│
───] [───┤
  PB_R1_FWD│
───] [───┤
  PB_R2_FWD│
───] [───┘
```

### Network 3 — Gom nút Nghịch
```
  PB_L1_REV                                                        REV_PB
───] [───┬───────────────────────────────────────────────────────( )──────
  PB_L2_REV│
───] [───┤
  PB_R1_REV│
───] [───┤
  PB_R2_REV│
───] [───┘
```

### Network 4 — Cờ tuyến trống (Line Empty)
> Không cảm biến nào tác động → toàn tuyến trống.
```
  S1A  S1B  S2A  S2B  S3A  S3B  S4A  S4B                        Line_Empty
──]/[──]/[──]/[──]/[──]/[──]/[──]/[──]/[─────────────────────────( )──────
```

### Network 5 — Tính chiếm chỗ (Occupancy) từng băng
```
  S1A                         Occ1          S2A                         Occ2
───] [───┬───────────────────( )──────     ───] [───┬───────────────────( )
  S1B    │                                   S2B    │
───] [───┘                                  ───] [───┘

  S3A                         Occ3          S4A                         Occ4
───] [───┬───────────────────( )──────     ───] [───┬───────────────────( )
  S3B    │                                   S4B    │
───] [───┘                                  ───] [───┘
```

### Network 6 — Khóa chế độ THUẬN (FWD_Mode, seal-in)
> Điều kiện đặt: pallet ở S1A + nút Thuận + không đang nghịch.
> Tự giữ tới khi tuyến trống hoặc mất an toàn.
```
  FWD_PB   S1A   REV_Mode                Safety_OK  Line_Empty      FWD_Mode
───] [─────] [────]/[────┬────────────────] [────────]/[────────────( )─────
  FWD_Mode              │
───] [─────────────────┘   (mạch tự giữ - seal in)
```

### Network 7 — Khóa chế độ NGHỊCH (REV_Mode, seal-in)
```
  REV_PB   S4B   FWD_Mode                Safety_OK  Line_Empty      REV_Mode
───] [─────] [────]/[────┬────────────────] [────────]/[────────────( )─────
  REV_Mode              │
───] [─────────────────┘
```

### Network 8 — Lệnh nâng cửa & cho phép qua cửa
> Có pallet tại ranh giới giữa (S2B chiều thuận, S3A chiều nghịch) → nâng cửa.
```
  S2B                                                               Gate_Up
───] [───┬────────────────────────────────────────────────────────( )──────
  S3A    │
───] [───┘
```
> `Gate_Up_FB` (cửa đã nâng hết) sẽ được dùng làm điều kiện cho REL2F / REL3R bên dưới.

---

### CHIỀU THUẬN — Điều kiện "nhả hàng" (Release)

### Network 9 — REL1F: CV1 đẩy sang CV2 (CV2 trống)
```
  FWD_Mode   Occ1   Occ2                                            REL1F
───] [────────] [────]/[───────────────────────────────────────────( )─────
```

### Network 10 — REL2F: CV2 đẩy sang CV3 qua CỬA (CV3 trống + cửa đã nâng)
```
  FWD_Mode   Occ2   Occ3   Gate_Up_FB                               REL2F
───] [────────] [────]/[──────] [───────────────────────────────────( )─────
```

### Network 11 — REL3F: CV3 đẩy sang CV4 (CV4 trống)
```
  FWD_Mode   Occ3   Occ4                                            REL3F
───] [────────] [────]/[───────────────────────────────────────────( )─────
```

### Network 12 — REL4F: CV4 xả pallet ra ngoài
```
  FWD_Mode   Occ4                                                   REL4F
───] [────────] [──────────────────────────────────────────────────( )─────
```

---

### CHIỀU NGHỊCH — Điều kiện "nhả hàng" (Release)

### Network 13 — REL4R: CV4 đẩy sang CV3 (CV3 trống)
```
  REV_Mode   Occ4   Occ3                                            REL4R
───] [────────] [────]/[───────────────────────────────────────────( )─────
```

### Network 14 — REL3R: CV3 đẩy sang CV2 qua CỬA (CV2 trống + cửa đã nâng)
```
  REV_Mode   Occ3   Occ2   Gate_Up_FB                               REL3R
───] [────────] [────]/[──────] [───────────────────────────────────( )─────
```

### Network 15 — REL2R: CV2 đẩy sang CV1 (CV1 trống)
```
  REV_Mode   Occ2   Occ1                                            REL2R
───] [────────] [────]/[───────────────────────────────────────────( )─────
```

### Network 16 — REL1R: CV1 xả pallet ra ngoài
```
  REV_Mode   Occ1                                                   REL1R
───] [────────] [──────────────────────────────────────────────────( )─────
```

---

### Ngõ ra MOTOR (chạy = "đang nhả ra" HOẶC "nhận từ băng trước")

### Network 17 — CV1 THUẬN  (chỉ nhả; CV1 là băng đầu chiều thuận)
```
  REL1F      Safety_OK                                              CV1_FWD
───] [────────] [──────────────────────────────────────────────────( )─────
```

### Network 18 — CV2 THUẬN  (nhả REL2F, hoặc nhận từ CV1: REL1F)
```
  REL2F      Safety_OK                                              CV2_FWD
───] [────┬───] [──────────────────────────────────────────────────( )─────
  REL1F   │
───] [────┘
```

### Network 19 — CV3 THUẬN  (nhả REL3F, hoặc nhận từ CV2: REL2F)
```
  REL3F      Safety_OK                                              CV3_FWD
───] [────┬───] [──────────────────────────────────────────────────( )─────
  REL2F   │
───] [────┘
```

### Network 20 — CV4 THUẬN  (xả REL4F, hoặc nhận từ CV3: REL3F)
```
  REL4F      Safety_OK                                              CV4_FWD
───] [────┬───] [──────────────────────────────────────────────────( )─────
  REL3F   │
───] [────┘
```

### Network 21 — CV4 NGHỊCH (chỉ nhả; CV4 là băng đầu chiều nghịch)
```
  REL4R      Safety_OK                                              CV4_REV
───] [────────] [──────────────────────────────────────────────────( )─────
```

### Network 22 — CV3 NGHỊCH (nhả REL3R, hoặc nhận từ CV4: REL4R)
```
  REL3R      Safety_OK                                              CV3_REV
───] [────┬───] [──────────────────────────────────────────────────( )─────
  REL4R   │
───] [────┘
```

### Network 23 — CV2 NGHỊCH (nhả REL2R, hoặc nhận từ CV3: REL3R)
```
  REL2R      Safety_OK                                              CV2_REV
───] [────┬───] [──────────────────────────────────────────────────( )─────
  REL3R   │
───] [────┘
```

### Network 24 — CV1 NGHỊCH (xả REL1R, hoặc nhận từ CV2: REL2R)
```
  REL1R      Safety_OK                                              CV1_REV
───] [────┬───] [──────────────────────────────────────────────────( )─────
  REL2R   │
───] [────┘
```

### Network 25 — Đèn báo (tùy chọn)
```
  FWD_Mode                                                          Lamp_FWD
───] [─────────────────────────────────────────────────────────────( )─────

  REV_Mode                                                          Lamp_REV
───] [─────────────────────────────────────────────────────────────( )─────

  Safety_OK                                                         Lamp_EMG
──]/[──────────────────────────────────────────────────────────────( )─────
```

---

## 5. Diễn giải một chu trình (chiều thuận)

1. Đặt pallet lên đầu CV1 → `S1A=1`. Nhấn nút Thuận → `FWD_PB=1` → **N6 khóa `FWD_Mode`**.
2. `Occ1=1`, `Occ2=0` → `REL1F=1` → **CV1 & CV2 cùng chạy** (N17, N18) đưa pallet sang CV2.
3. Pallet sang CV2 → `Occ2=1`, `Occ1=0` → `REL1F=0` (CV1 dừng). Pallet chạy tới `S2B`
   → **N8 ra lệnh `Gate_Up`**. Chờ `Gate_Up_FB=1` (cửa nâng hết).
4. `Occ3=0` + `Gate_Up_FB=1` → `REL2F=1` → **CV2 & CV3 chạy**, pallet qua cửa sang CV3.
5. Tương tự pallet sang CV4 (`REL3F`) rồi xả ra ngoài (`REL4F`).
6. Khi muốn xếp nhiều pallet: do **tách hàng**, pallet sau chỉ tiến khi zone trước nó đã
   trống → tự giữ khoảng cách. Hết pallet → `Line_Empty=1` → nhả `FWD_Mode`.
7. Bất kỳ lúc nào nhấn **Khẩn** → `Safety_OK=0` → reset chế độ, cắt toàn bộ motor.

Chiều nghịch hoạt động đối xứng (nạp ở `S4B`, xả ở `S1A`, cửa cho qua bằng `REL3R`).

---

## 6. Ghi chú triển khai

- **Khử nhiễu cảm biến:** nên thêm timer ON-delay ~50–100 ms cho mỗi cảm biến nếu
  môi trường rung động (chống nhấp nháy).
- **Bảo vệ motor:** nối tiếp thêm tiếp điểm relay nhiệt / báo lỗi biến tần vào từng rung
  ngõ ra motor.
- **Cửa an toàn:** nên thêm timeout cho `Gate_Up` – nếu sau X giây không có `Gate_Up_FB`
  thì báo lỗi và dừng. (Có thể bổ sung dễ dàng bằng 1 timer.)
- **Khóa chéo FWD/REV ở ngõ ra:** vì `FWD_Mode` và `REV_Mode` đã loại trừ nhau (N6/N7)
  nên ngõ ra thuận/nghịch của cùng motor không bao giờ bật đồng thời. Nếu muốn an toàn
  tuyệt đối có thể thêm tiếp điểm `]/[ CVx_REV` nối tiếp ở rung `CVx_FWD` và ngược lại.

Xem bản **SCL tương đương** trong `BangTai_4.scl` để nạp nhanh vào TIA Portal.
