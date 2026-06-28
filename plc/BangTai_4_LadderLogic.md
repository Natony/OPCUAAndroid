# Chương trình PLC – 4 băng tải vận chuyển Pallet (Ladder – TIA Portal)

> PLC Siemens S7-1200 / S7-1500 (TIA Portal V1x). Ngôn ngữ **LAD**.
> Mỗi tiếp điểm hiển thị **tên tag** (trên) và **địa chỉ tuyệt đối** (dưới) đúng kiểu TIA.
> **Đã bỏ cửa nâng** – tuyến chỉ còn 4 băng nối tiếp.
> File `BangTai_4.scl` là bản nguồn SCL tương đương (import nhanh nếu muốn).

---

## 1. Mô tả & giả định

4 băng tải nối tiếp thành 1 tuyến (CV1–CV4). Pallet chạy **thuận** (CV1→CV4) hoặc
**nghịch** (CV4→CV1).

```
   CHIỀU THUẬN  ───────────────────────────────────────►
   ┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐
   │  CV1   │   │  CV2   │   │  CV3   │   │  CV4   │
   │S1A  S1B│   │S2A  S2B│   │S3A  S3B│   │S4A  S4B│
   └────────┘   └────────┘   └────────┘   └────────┘
   ◄───────────────────────────────────────  CHIỀU NGHỊCH

   • A = đầu trái, B = đầu phải (mỗi băng 2 cảm biến).
   • THUẬN : nạp pallet ở S1A, xả ở S4B.
   • NGHỊCH: nạp pallet ở S4B, xả ở S1A.
```

Giả định (sửa nếu khác phần cứng):
1. 8 cảm biến (mỗi băng 2 đầu A/B).
2. 4 box nút (2 trạm × 2 box). Mỗi box: 1 nút **Thuận**, 1 nút **Nghịch**, 1 nút **Khẩn**.
   - Tất cả nút Thuận đấu song song → `FWD_PB`. Tất cả nút Nghịch song song → `REV_PB`.
   - Tất cả nút Khẩn (tiếp điểm NC) đấu **nối tiếp** thành chuỗi an toàn.
3. **Tách hàng (accumulation):** một băng chỉ đẩy pallet sang băng kế khi băng đó đang
   trống → các pallet luôn cách nhau ≥ 1 băng.

---

## 2. Bảng tag (PLC Tags)

### Ngõ vào – DI
| Tag | Địa chỉ | Mô tả |
|-----|---------|-------|
| `S1A` | %I0.0 | Cảm biến CV1 đầu A |
| `S1B` | %I0.1 | Cảm biến CV1 đầu B |
| `S2A` | %I0.2 | Cảm biến CV2 đầu A |
| `S2B` | %I0.3 | Cảm biến CV2 đầu B |
| `S3A` | %I0.4 | Cảm biến CV3 đầu A |
| `S3B` | %I0.5 | Cảm biến CV3 đầu B |
| `S4A` | %I0.6 | Cảm biến CV4 đầu A |
| `S4B` | %I0.7 | Cảm biến CV4 đầu B |
| `PB_L1_FWD` | %I1.0 | Nút Thuận Box L1 |
| `PB_L1_REV` | %I1.1 | Nút Nghịch Box L1 |
| `PB_L2_FWD` | %I1.2 | Nút Thuận Box L2 |
| `PB_L2_REV` | %I1.3 | Nút Nghịch Box L2 |
| `PB_R1_FWD` | %I1.4 | Nút Thuận Box R1 |
| `PB_R1_REV` | %I1.5 | Nút Nghịch Box R1 |
| `PB_R2_FWD` | %I1.6 | Nút Thuận Box R2 |
| `PB_R2_REV` | %I1.7 | Nút Nghịch Box R2 |
| `EMG_L1` | %I2.0 | Nút khẩn Box L1 (NC = 1 khi bình thường) |
| `EMG_L2` | %I2.1 | Nút khẩn Box L2 (NC) |
| `EMG_R1` | %I2.2 | Nút khẩn Box R1 (NC) |
| `EMG_R2` | %I2.3 | Nút khẩn Box R2 (NC) |

### Ngõ ra – DQ
| Tag | Địa chỉ | Mô tả |
|-----|---------|-------|
| `CV1_FWD` | %Q0.0 | Motor CV1 thuận |
| `CV1_REV` | %Q0.1 | Motor CV1 nghịch |
| `CV2_FWD` | %Q0.2 | Motor CV2 thuận |
| `CV2_REV` | %Q0.3 | Motor CV2 nghịch |
| `CV3_FWD` | %Q0.4 | Motor CV3 thuận |
| `CV3_REV` | %Q0.5 | Motor CV3 nghịch |
| `CV4_FWD` | %Q0.6 | Motor CV4 thuận |
| `CV4_REV` | %Q0.7 | Motor CV4 nghịch |
| `Lamp_FWD` | %Q1.0 | Đèn báo chạy thuận (tùy chọn) |
| `Lamp_REV` | %Q1.1 | Đèn báo chạy nghịch (tùy chọn) |
| `Lamp_EMG` | %Q1.2 | Đèn báo sự cố khẩn (tùy chọn) |

### Bit trung gian – M
| Tag | Địa chỉ | Mô tả |
|-----|---------|-------|
| `Safety_OK` | %M10.0 | Chuỗi an toàn lành mạnh |
| `FWD_PB` | %M10.1 | Có nút Thuận đang nhấn |
| `REV_PB` | %M10.2 | Có nút Nghịch đang nhấn |
| `Line_Empty` | %M10.3 | Toàn tuyến trống |
| `FWD_Mode` | %M10.4 | Chế độ thuận đang khóa |
| `REV_Mode` | %M10.5 | Chế độ nghịch đang khóa |
| `Occ1` | %M11.0 | CV1 có pallet |
| `Occ2` | %M11.1 | CV2 có pallet |
| `Occ3` | %M11.2 | CV3 có pallet |
| `Occ4` | %M11.3 | CV4 có pallet |
| `REL1F` | %M12.0 | CV1 đẩy sang CV2 (thuận) |
| `REL2F` | %M12.1 | CV2 đẩy sang CV3 (thuận) |
| `REL3F` | %M12.2 | CV3 đẩy sang CV4 (thuận) |
| `REL4F` | %M12.3 | CV4 xả ra ngoài (thuận) |
| `REL4R` | %M13.0 | CV4 đẩy sang CV3 (nghịch) |
| `REL3R` | %M13.1 | CV3 đẩy sang CV2 (nghịch) |
| `REL2R` | %M13.2 | CV2 đẩy sang CV1 (nghịch) |
| `REL1R` | %M13.3 | CV1 xả ra ngoài (nghịch) |

---

## 3. Chương trình LAD (OB1 hoặc FC "Bang_Tai_4")

Ký hiệu TIA: `┤ ├` thường mở (NO) · `┤/├` thường đóng (NC) · `( )` cuộn dây.

### Network 1 — Chuỗi an toàn (4 nút khẩn NC nối tiếp)
```
    "EMG_L1"   "EMG_L2"   "EMG_R1"   "EMG_R2"               "Safety_OK"
     %I2.0      %I2.1      %I2.2      %I2.3                   %M10.0
  ────┤ ├────────┤ ├────────┤ ├────────┤ ├────────────────────( )──────
```

### Network 2 — Gom nút Thuận
```
   "PB_L1_FWD"                                              "FWD_PB"
     %I1.0                                                   %M10.1
  ────┤ ├───┬───────────────────────────────────────────────( )──────
   "PB_L2_FWD"│
     %I1.2  │
  ────┤ ├───┤
   "PB_R1_FWD"│
     %I1.4  │
  ────┤ ├───┤
   "PB_R2_FWD"│
     %I1.6  │
  ────┤ ├───┘
```

### Network 3 — Gom nút Nghịch
```
   "PB_L1_REV"                                              "REV_PB"
     %I1.1                                                   %M10.2
  ────┤ ├───┬───────────────────────────────────────────────( )──────
   "PB_L2_REV"│
     %I1.3  │
  ────┤ ├───┤
   "PB_R1_REV"│
     %I1.5  │
  ────┤ ├───┤
   "PB_R2_REV"│
     %I1.7  │
  ────┤ ├───┘
```

### Network 4 — Tuyến trống (Line Empty)
```
   "S1A"  "S1B"  "S2A"  "S2B"  "S3A"  "S3B"  "S4A"  "S4B"   "Line_Empty"
   %I0.0  %I0.1  %I0.2  %I0.3  %I0.4  %I0.5  %I0.6  %I0.7     %M10.3
  ──┤/├────┤/├────┤/├────┤/├────┤/├────┤/├────┤/├────┤/├───────( )──────
```

### Network 5 — Chiếm chỗ từng băng (Occupancy)
```
   "S1A"                 "Occ1"           "S2A"                 "Occ2"
   %I0.0                 %M11.0           %I0.2                 %M11.1
  ──┤ ├───┬──────────────( )──────       ──┤ ├───┬──────────────( )──────
   "S1B"  │                               "S2B"  │
   %I0.1  │                               %I0.3  │
  ──┤ ├───┘                               ──┤ ├───┘

   "S3A"                 "Occ3"           "S4A"                 "Occ4"
   %I0.4                 %M11.2           %I0.6                 %M11.3
  ──┤ ├───┬──────────────( )──────       ──┤ ├───┬──────────────( )──────
   "S3B"  │                               "S4B"  │
   %I0.5  │                               %I0.7  │
  ──┤ ├───┘                               ──┤ ├───┘
```

### Network 6 — Khóa chế độ THUẬN (seal-in)
> Đặt: pallet ở S1A + nút Thuận + không đang nghịch. Giữ tới khi tuyến trống / mất an toàn.
```
   "FWD_PB"  "S1A"  "REV_Mode"      "Safety_OK"  "Line_Empty"   "FWD_Mode"
    %M10.1   %I0.0   %M10.5          %M10.0       %M10.3         %M10.4
  ────┤ ├─────┤ ├─────┤/├─────┬────────┤ ├──────────┤/├───────────( )──────
   "FWD_Mode"                 │
    %M10.4                    │
  ────┤ ├───────────────────┘
```

### Network 7 — Khóa chế độ NGHỊCH (seal-in)
```
   "REV_PB"  "S4B"  "FWD_Mode"      "Safety_OK"  "Line_Empty"   "REV_Mode"
    %M10.2   %I0.7   %M10.4          %M10.0       %M10.3         %M10.5
  ────┤ ├─────┤ ├─────┤/├─────┬────────┤ ├──────────┤/├───────────( )──────
   "REV_Mode"                 │
    %M10.5                    │
  ────┤ ├───────────────────┘
```

---

### THUẬN — điều kiện nhả hàng (Release)

### Network 8 — REL1F: CV1 → CV2 (CV2 trống)
```
   "FWD_Mode"  "Occ1"  "Occ2"                                "REL1F"
    %M10.4     %M11.0  %M11.1                                 %M12.0
  ────┤ ├───────┤ ├─────┤/├───────────────────────────────────( )──────
```

### Network 9 — REL2F: CV2 → CV3 (CV3 trống)
```
   "FWD_Mode"  "Occ2"  "Occ3"                                "REL2F"
    %M10.4     %M11.1  %M11.2                                 %M12.1
  ────┤ ├───────┤ ├─────┤/├───────────────────────────────────( )──────
```

### Network 10 — REL3F: CV3 → CV4 (CV4 trống)
```
   "FWD_Mode"  "Occ3"  "Occ4"                                "REL3F"
    %M10.4     %M11.2  %M11.3                                 %M12.2
  ────┤ ├───────┤ ├─────┤/├───────────────────────────────────( )──────
```

### Network 11 — REL4F: CV4 xả ra ngoài
```
   "FWD_Mode"  "Occ4"                                        "REL4F"
    %M10.4     %M11.3                                         %M12.3
  ────┤ ├───────┤ ├────────────────────────────────────────────( )──────
```

---

### NGHỊCH — điều kiện nhả hàng (Release)

### Network 12 — REL4R: CV4 → CV3 (CV3 trống)
```
   "REV_Mode"  "Occ4"  "Occ3"                                "REL4R"
    %M10.5     %M11.3  %M11.2                                 %M13.0
  ────┤ ├───────┤ ├─────┤/├───────────────────────────────────( )──────
```

### Network 13 — REL3R: CV3 → CV2 (CV2 trống)
```
   "REV_Mode"  "Occ3"  "Occ2"                                "REL3R"
    %M10.5     %M11.2  %M11.1                                 %M13.1
  ────┤ ├───────┤ ├─────┤/├───────────────────────────────────( )──────
```

### Network 14 — REL2R: CV2 → CV1 (CV1 trống)
```
   "REV_Mode"  "Occ2"  "Occ1"                                "REL2R"
    %M10.5     %M11.1  %M11.0                                 %M13.2
  ────┤ ├───────┤ ├─────┤/├───────────────────────────────────( )──────
```

### Network 15 — REL1R: CV1 xả ra ngoài
```
   "REV_Mode"  "Occ1"                                        "REL1R"
    %M10.5     %M11.0                                         %M13.3
  ────┤ ├───────┤ ├────────────────────────────────────────────( )──────
```

---

### Ngõ ra MOTOR  (chạy = đang nhả ra HOẶC nhận từ băng trước)

### Network 16 — CV1 THUẬN
```
   "REL1F"   "Safety_OK"                                     "CV1_FWD"
    %M12.0    %M10.0                                          %Q0.0
  ────┤ ├──────┤ ├─────────────────────────────────────────────( )──────
```

### Network 17 — CV2 THUẬN (nhả REL2F hoặc nhận từ CV1 = REL1F)
```
   "REL2F"   "Safety_OK"                                     "CV2_FWD"
    %M12.1    %M10.0                                          %Q0.2
  ────┤ ├──┬───┤ ├─────────────────────────────────────────────( )──────
   "REL1F" │
    %M12.0 │
  ────┤ ├──┘
```

### Network 18 — CV3 THUẬN (nhả REL3F hoặc nhận từ CV2 = REL2F)
```
   "REL3F"   "Safety_OK"                                     "CV3_FWD"
    %M12.2    %M10.0                                          %Q0.4
  ────┤ ├──┬───┤ ├─────────────────────────────────────────────( )──────
   "REL2F" │
    %M12.1 │
  ────┤ ├──┘
```

### Network 19 — CV4 THUẬN (xả REL4F hoặc nhận từ CV3 = REL3F)
```
   "REL4F"   "Safety_OK"                                     "CV4_FWD"
    %M12.3    %M10.0                                          %Q0.6
  ────┤ ├──┬───┤ ├─────────────────────────────────────────────( )──────
   "REL3F" │
    %M12.2 │
  ────┤ ├──┘
```

### Network 20 — CV4 NGHỊCH
```
   "REL4R"   "Safety_OK"                                     "CV4_REV"
    %M13.0    %M10.0                                          %Q0.7
  ────┤ ├──────┤ ├─────────────────────────────────────────────( )──────
```

### Network 21 — CV3 NGHỊCH (nhả REL3R hoặc nhận từ CV4 = REL4R)
```
   "REL3R"   "Safety_OK"                                     "CV3_REV"
    %M13.1    %M10.0                                          %Q0.5
  ────┤ ├──┬───┤ ├─────────────────────────────────────────────( )──────
   "REL4R" │
    %M13.0 │
  ────┤ ├──┘
```

### Network 22 — CV2 NGHỊCH (nhả REL2R hoặc nhận từ CV3 = REL3R)
```
   "REL2R"   "Safety_OK"                                     "CV2_REV"
    %M13.2    %M10.0                                          %Q0.3
  ────┤ ├──┬───┤ ├─────────────────────────────────────────────( )──────
   "REL3R" │
    %M13.1 │
  ────┤ ├──┘
```

### Network 23 — CV1 NGHỊCH (xả REL1R hoặc nhận từ CV2 = REL2R)
```
   "REL1R"   "Safety_OK"                                     "CV1_REV"
    %M13.3    %M10.0                                          %Q0.1
  ────┤ ├──┬───┤ ├─────────────────────────────────────────────( )──────
   "REL2R" │
    %M13.2 │
  ────┤ ├──┘
```

### Network 24 — Đèn báo (tùy chọn)
```
   "FWD_Mode"                                                "Lamp_FWD"
    %M10.4                                                    %Q1.0
  ────┤ ├──────────────────────────────────────────────────────( )──────

   "REV_Mode"                                                "Lamp_REV"
    %M10.5                                                    %Q1.1
  ────┤ ├──────────────────────────────────────────────────────( )──────

   "Safety_OK"                                               "Lamp_EMG"
    %M10.0                                                    %Q1.2
  ───┤/├───────────────────────────────────────────────────────( )──────
```

---

## 4. Diễn giải một chu trình (chiều thuận)

1. Đặt pallet lên đầu CV1 → `S1A=1`. Nhấn nút Thuận → khóa `FWD_Mode` (N6).
2. `Occ1=1`, `Occ2=0` → `REL1F=1` → CV1 & CV2 cùng chạy (N16, N17) đưa pallet sang CV2.
3. Pallet sang CV2 → `Occ2=1`, `Occ1=0` → `REL1F=0` (CV1 dừng); `REL2F` đưa tiếp sang CV3…
4. Lần lượt qua CV3 (`REL3F`), CV4 (`REL3F` nhận), rồi xả ra ngoài (`REL4F`).
5. **Tách hàng:** pallet sau chỉ tiến khi băng trước nó đã trống → tự giữ khoảng cách.
6. Hết pallet → `Line_Empty=1` → nhả `FWD_Mode`. Nhấn **Khẩn** bất cứ lúc nào → `Safety_OK=0`
   → cắt toàn bộ motor + reset chế độ.

Chiều nghịch đối xứng (nạp ở `S4B`, xả ở `S1A`).

---

## 5. Cách đưa vào TIA Portal

- **Cách 1 (LAD trực tiếp):** tạo Tag table theo Mục 2, rồi vẽ lại 24 network ở Mục 3
  trong khối OB1 hoặc FC (mỗi network 1 đoạn).
- **Cách 2 (nhanh):** Project tree → **External source files → Add new external file** →
  chọn `BangTai_4.scl` → chuột phải **Generate blocks from source**. Sau đó có thể
  đổi ngôn ngữ khối sang LAD nếu muốn.

## 6. Khuyến nghị an toàn (nên bổ sung)
- Nối tiếp tiếp điểm **relay nhiệt / lỗi biến tần** vào từng network ngõ ra motor.
- Thêm ON-delay ~50–100 ms khử nhiễu cảm biến nếu rung động nhiều.
- `FWD_Mode` và `REV_Mode` đã loại trừ nhau (N6/N7) nên ngõ ra thuận/nghịch cùng motor
  không bật đồng thời; muốn chắc chắn có thể thêm `┤/├ CVx_REV` nối tiếp ở rung `CVx_FWD`.
