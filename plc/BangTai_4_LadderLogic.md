# Chương trình PLC – 4 băng tải vận chuyển Pallet (Ladder – TIA Portal)

> PLC Siemens S7-1200 / S7-1500 (TIA Portal). Ngôn ngữ **LAD**.
> Mỗi tiếp điểm hiển thị **tên tag** (trên) và **địa chỉ tuyệt đối** (dưới) đúng kiểu TIA.
> **Không có cửa nâng.** Cảm biến loại **NO** (bit = 1 khi có pallet).
> File `BangTai_4.scl` là bản nguồn SCL tương đương (import nhanh).

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

   • A = đầu trái, B = đầu phải (mỗi băng 2 cảm biến NO).
   • Giữa S_iA và S_iB có "VÙNG CHẾT" (pallet không che cảm biến nào).
   • THUẬN : nạp pallet ở S1A, xả ở S4B.  NGHỊCH: nạp ở S4B, xả ở S1A.
```

Giả định (sửa nếu khác phần cứng):
1. 8 cảm biến NO (mỗi băng 2 đầu A/B) → bit = **1 khi có pallet**.
2. 4 box nút (2 trạm × 2 box). Mỗi box: 1 nút **Thuận**, 1 nút **Nghịch**, 1 nút **Khẩn**.
   - Nút Thuận song song → `FWD_PB`; nút Nghịch song song → `REV_PB`.
   - Nút Khẩn (NC) nối tiếp → chuỗi an toàn `Safety_OK`.
3. **Tách hàng:** một băng chỉ thả pallet sang băng kế khi băng đó trống; nếu băng kế bận,
   pallet **dừng chờ ngay tại cảm biến cuối băng** (không dừng ở vùng chết).

### ★ Điểm sửa quan trọng so với bản trước
Trước đây băng chạy theo `Occ_i` tức thời nên **pallet vừa rời cảm biến đầu là băng dừng**
(kẹt ở vùng chết). Bản này dùng bit **chốt `Arr_i`**: pallet vào băng → chốt = 1 và
**giữ nguyên qua vùng chết**; chỉ nhả chốt khi pallet đã sang băng kế (hoặc xả hết ra
ngoài). → Băng chạy liên tục tới khi giao hàng xong.

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
| `EMG_L1` | %I2.0 | Nút khẩn Box L1 (NC = 1 bình thường) |
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
| `Occ1` | %M11.0 | CV1 có cảm biến tác động (tức thời) |
| `Occ2` | %M11.1 | CV2 có cảm biến tác động |
| `Occ3` | %M11.2 | CV3 có cảm biến tác động |
| `Occ4` | %M11.3 | CV4 có cảm biến tác động |
| `Arr1` | %M12.0 | **Chốt:** CV1 đang có pallet (giữ qua vùng chết) |
| `Arr2` | %M12.1 | **Chốt:** CV2 đang có pallet |
| `Arr3` | %M12.2 | **Chốt:** CV3 đang có pallet |
| `Arr4` | %M12.3 | **Chốt:** CV4 đang có pallet |
| `PassedFwd4` | %M12.4 | Pallet đã tới cảm biến xả thuận (S4B) |
| `PassedRev1` | %M12.5 | Pallet đã tới cảm biến xả nghịch (S1A) |

---

## 3. Chương trình LAD

Ký hiệu TIA: `┤ ├` thường mở · `┤/├` thường đóng · `( )` cuộn · `( S )` set · `( R )` reset.

### Network 1 — Chuỗi an toàn (4 nút khẩn NC nối tiếp)
```
    "EMG_L1"   "EMG_L2"   "EMG_R1"   "EMG_R2"               "Safety_OK"
     %I2.0      %I2.1      %I2.2      %I2.3                   %M10.0
  ────┤ ├────────┤ ├────────┤ ├────────┤ ├────────────────────( )──────
```

### Network 2 — Gom nút Thuận
```
   "PB_L1_FWD"   "PB_L2_FWD"   "PB_R1_FWD"   "PB_R2_FWD"     "FWD_PB"
     %I1.0         %I1.2         %I1.4         %I1.6          %M10.1
  ────┤ ├───┬───────┤ ├───┬───────┤ ├───┬───────┤ ├────────────( )──────
            └─────────────┴─────────────┘  (đấu song song)
```

### Network 3 — Gom nút Nghịch
```
   "PB_L1_REV"   "PB_L2_REV"   "PB_R1_REV"   "PB_R2_REV"     "REV_PB"
     %I1.1         %I1.3         %I1.5         %I1.7          %M10.2
  ────┤ ├───┬───────┤ ├───┬───────┤ ├───┬───────┤ ├────────────( )──────
            └─────────────┴─────────────┘  (đấu song song)
```

### Network 4 — Tuyến trống (Line Empty)
```
   "S1A"  "S1B"  "S2A"  "S2B"  "S3A"  "S3B"  "S4A"  "S4B"   "Line_Empty"
   %I0.0  %I0.1  %I0.2  %I0.3  %I0.4  %I0.5  %I0.6  %I0.7     %M10.3
  ──┤/├────┤/├────┤/├────┤/├────┤/├────┤/├────┤/├────┤/├───────( )──────
```

### Network 5 — Cảm biến tức thời từng băng (Occupancy)
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

### CHỐT HIỆN DIỆN PALLET (xử lý vùng chết)

### Network 8 — PassedFwd4: pallet đã tới cảm biến xả thuận
```
   "FWD_Mode"  "S4B"                                         "PassedFwd4"
    %M10.4     %I0.7                                          %M12.4
  ────┤ ├───────┤ ├────────────────────────────────────────────( S )────

   "Arr4"                                                     "PassedFwd4"
   %M12.3                                                      %M12.4
  ───┤/├───────────────────────────────────────────────────────( R )────
```

### Network 9 — PassedRev1: pallet đã tới cảm biến xả nghịch
```
   "REV_Mode"  "S1A"                                         "PassedRev1"
    %M10.5     %I0.0                                          %M12.5
  ────┤ ├───────┤ ├────────────────────────────────────────────( S )────

   "Arr1"                                                     "PassedRev1"
   %M12.0                                                      %M12.5
  ───┤/├───────────────────────────────────────────────────────( R )────
```

### Network 10 — Arr1 (chốt pallet trên CV1)
> SET: có cảm biến trên CV1. RESET: pallet đã rời CV1 sang CV2 (thuận) hoặc xả nghịch.
```
   "Occ1"                                                     "Arr1"
   %M11.0                                                      %M12.0
  ────┤ ├──────────────────────────────────────────────────────( S )────

   "Occ1"     "FWD_Mode"  "Occ2"                              "Arr1"
   %M11.0     %M10.4      %M11.1                               %M12.0
  ───┤/├───┬────┤ ├─────────┤ ├──────┐                          ( R )────
           │  "REV_Mode" "PassedRev1"│
           │   %M10.5     %M12.5     │
           └────┤ ├─────────┤ ├──────┘
```

### Network 11 — Arr2 (chốt pallet trên CV2)
```
   "Occ2"                                                     "Arr2"
   %M11.1                                                      %M12.1
  ────┤ ├──────────────────────────────────────────────────────( S )────

   "Occ2"     "FWD_Mode"  "Occ3"                              "Arr2"
   %M11.1     %M10.4      %M11.2                               %M12.1
  ───┤/├───┬────┤ ├─────────┤ ├──────┐                          ( R )────
           │  "REV_Mode"  "Occ1"     │
           │   %M10.5     %M11.0      │
           └────┤ ├─────────┤ ├──────┘
```

### Network 12 — Arr3 (chốt pallet trên CV3)
```
   "Occ3"                                                     "Arr3"
   %M11.2                                                      %M12.2
  ────┤ ├──────────────────────────────────────────────────────( S )────

   "Occ3"     "FWD_Mode"  "Occ4"                              "Arr3"
   %M11.2     %M10.4      %M11.3                               %M12.2
  ───┤/├───┬────┤ ├─────────┤ ├──────┐                          ( R )────
           │  "REV_Mode"  "Occ2"     │
           │   %M10.5     %M11.1      │
           └────┤ ├─────────┤ ├──────┘
```

### Network 13 — Arr4 (chốt pallet trên CV4)
```
   "Occ4"                                                     "Arr4"
   %M11.3                                                      %M12.3
  ────┤ ├──────────────────────────────────────────────────────( S )────

   "Occ4"    "FWD_Mode" "PassedFwd4"                          "Arr4"
   %M11.3     %M10.4     %M12.4                                %M12.3
  ───┤/├───┬────┤ ├─────────┤ ├──────┐                          ( R )────
           │  "REV_Mode"  "Occ3"     │
           │   %M10.5     %M11.2      │
           └────┤ ├─────────┤ ├──────┘
```

---

### NGÕ RA MOTOR
> Chạy = đang khóa chiều đó **AND** trên băng có pallet (chốt) **AND** không phải đang
> "đứng chờ ở cảm biến cuối vì băng kế bận" **AND** an toàn OK.
> Điều kiện chờ NOT(S_iB AND Occ_kế) = nhánh song song ┤/S_iB├ // ┤/Occ_kế├ (định luật De Morgan).

### Network 14 — CV1 THUẬN
```
   "FWD_Mode" "Arr1"  "Safety_OK"   "S1B"            "CV1_FWD"
    %M10.4    %M12.0   %M10.0        %I0.1            %Q0.0
  ────┤ ├──────┤ ├──────┤ ├─────┬─────┤/├────┬─────────( )──────
                              │   "Occ2"   │
                              │   %M11.1   │
                              └─────┤/├────┘
```

### Network 15 — CV2 THUẬN
```
   "FWD_Mode" "Arr2"  "Safety_OK"   "S2B"            "CV2_FWD"
    %M10.4    %M12.1   %M10.0        %I0.3            %Q0.2
  ────┤ ├──────┤ ├──────┤ ├─────┬─────┤/├────┬─────────( )──────
                              │   "Occ3"   │
                              │   %M11.2   │
                              └─────┤/├────┘
```

### Network 16 — CV3 THUẬN
```
   "FWD_Mode" "Arr3"  "Safety_OK"   "S3B"            "CV3_FWD"
    %M10.4    %M12.2   %M10.0        %I0.5            %Q0.4
  ────┤ ├──────┤ ├──────┤ ├─────┬─────┤/├────┬─────────( )──────
                              │   "Occ4"   │
                              │   %M11.3   │
                              └─────┤/├────┘
```

### Network 17 — CV4 THUẬN (băng xả, không có băng kế)
```
   "FWD_Mode" "Arr4"  "Safety_OK"                    "CV4_FWD"
    %M10.4    %M12.3   %M10.0                          %Q0.6
  ────┤ ├──────┤ ├──────┤ ├────────────────────────────( )──────
```

### Network 18 — CV4 NGHỊCH
```
   "REV_Mode" "Arr4"  "Safety_OK"   "S4A"            "CV4_REV"
    %M10.5    %M12.3   %M10.0        %I0.6            %Q0.7
  ────┤ ├──────┤ ├──────┤ ├─────┬─────┤/├────┬─────────( )──────
                              │   "Occ3"   │
                              │   %M11.2   │
                              └─────┤/├────┘
```

### Network 19 — CV3 NGHỊCH
```
   "REV_Mode" "Arr3"  "Safety_OK"   "S3A"            "CV3_REV"
    %M10.5    %M12.2   %M10.0        %I0.4            %Q0.5
  ────┤ ├──────┤ ├──────┤ ├─────┬─────┤/├────┬─────────( )──────
                              │   "Occ2"   │
                              │   %M11.1   │
                              └─────┤/├────┘
```

### Network 20 — CV2 NGHỊCH
```
   "REV_Mode" "Arr2"  "Safety_OK"   "S2A"            "CV2_REV"
    %M10.5    %M12.1   %M10.0        %I0.2            %Q0.3
  ────┤ ├──────┤ ├──────┤ ├─────┬─────┤/├────┬─────────( )──────
                              │   "Occ1"   │
                              │   %M11.0   │
                              └─────┤/├────┘
```

### Network 21 — CV1 NGHỊCH (băng xả nghịch, không có băng kế)
```
   "REV_Mode" "Arr1"  "Safety_OK"                    "CV1_REV"
    %M10.5    %M12.0   %M10.0                          %Q0.1
  ────┤ ├──────┤ ├──────┤ ├────────────────────────────( )──────
```

### Network 22 — Đèn báo (tùy chọn)
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

## 4. Diễn giải một chu trình (chiều thuận) — vì sao hết kẹt vùng chết

1. Đặt pallet lên đầu CV1 → `S1A=1` → `Occ1=1`. Nhấn nút Thuận → khóa `FWD_Mode` (N6).
2. `Occ1=1` → **`Arr1` SET** (N10). CV1 chạy (N14).
3. Pallet rời S1A, **chưa tới S1B** → `Occ1=0` **nhưng `Arr1` vẫn = 1** (điều kiện reset
   cần pallet đã sang CV2 — chưa xảy ra) → **CV1 tiếp tục chạy** qua vùng chết. ✅ Hết kẹt.
4. Pallet tới S1B. Nếu CV2 trống (`Occ2=0`) → CV1 chạy tiếp, pallet sang CV2.
   `Occ2=1` → `Arr2` SET, CV2 chạy nhận hàng; khi đuôi pallet rời hẳn CV1
   (`Occ1=0` & `Occ2=1`) → **`Arr1` RESET** → CV1 dừng.
5. **Tách hàng:** nếu pallet tới S1B mà CV2 đang bận (`Occ2=1`) → nhánh `NOT(S1B AND Occ2)=0`
   → CV1 **dừng chờ ngay tại S1B** (tại cảm biến, không phải vùng chết). CV2 trống thì chạy lại.
6. Tương tự qua CV3, CV4. Ở CV4, khi pallet tới `S4B` → `PassedFwd4` SET; xả hết ra ngoài
   (`Occ4=0` & `PassedFwd4=1`) → `Arr4` RESET → CV4 dừng.
7. Hết pallet → `Line_Empty=1` → nhả `FWD_Mode`. Nhấn **Khẩn** → cắt toàn bộ motor.

Chiều nghịch đối xứng (nạp `S4B`, xả `S1A`, dùng `PassedRev1`).

---

## 5. Đưa vào TIA Portal
- **LAD trực tiếp:** tạo Tag table (Mục 2) rồi vẽ 22 network (Mục 3) vào OB1/FC.
- **Nhanh:** External source files → Add `BangTai_4.scl` → *Generate blocks from source*.

## 6. Khuyến nghị
- Nối tiếp tiếp điểm **relay nhiệt / lỗi biến tần** vào mỗi network ngõ ra motor.
- Thêm ON-delay ~30–80 ms khử nhiễu cảm biến nếu rung động.
- **Cảm biến NO** sẽ fail-danger khi đứt dây (đọc = trống). Nếu cần an toàn cao, dùng cảm
  biến NC. Nút Khẩn đã để NC (fail-safe).
- Sau khi nhấn Khẩn giữa chừng, `FWD_Mode/REV_Mode` bị nhả (vì cần `Safety_OK`); muốn chạy
  lại phải đặt pallet ở cảm biến đầu chiều tương ứng rồi nhấn nút lại (các chốt `Arr_i`
  vẫn giữ vị trí pallet trong tuyến).
