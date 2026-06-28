# Chương trình PLC – 4 băng tải vận chuyển Pallet (Ladder – TIA Portal)

> PLC Siemens S7-1200 / S7-1500 (TIA Portal). Ngôn ngữ **LAD**.
> Tiếp điểm hiển thị **tên tag** (trên) + **địa chỉ tuyệt đối** (dưới) đúng kiểu TIA.
> Không có cửa nâng. Cảm biến **NO** (bit = 1 khi có pallet).
> File `BangTai_4.scl` là bản nguồn tương đương (FB, có timer) để import nhanh.

---

## 1. Thuật toán tổng quát (theo mô tả của bạn)

Tuyến 4 băng nối tiếp CV1–CV4, chạy **thuận** (CV1→CV4) hoặc **nghịch** (CV4→CV1).

```
   CHIỀU THUẬN  ───────────────────────────────────────►
   ┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐
   │  CV1   │   │  CV2   │   │  CV3   │   │  CV4   │
   │S1A  S1B│   │S2A  S2B│   │S3A  S3B│   │S4A  S4B│
   └────────┘   └────────┘   └────────┘   └────────┘
   ◄───────────────────────────────────────  CHIỀU NGHỊCH
   • A = đầu trái, B = đầu phải.  Giữa 2 cảm biến có "vùng chết".
```

**Nguyên tắc cascade tách hàng (chiều thuận, CV-n → CV-(n+1)):**
1. Pallet ở CV-n → CV-n chạy đưa pallet tiến tới **cảm biến cuối băng** (S_nB).
2. Tại S_nB: kiểm tra CV-(n+1).
   - CV-(n+1) **trống hoàn toàn** (chốt `Arr` của nó = 0) **và đã trống đủ `T_CHECK` giây**
     → cho pallet qua, CV-(n+1) chạy nhận hàng.
   - CV-(n+1) **còn hàng** → CV-n **dừng chờ ngay tại S_nB** tới khi CV-(n+1) thoát hết.
3. **Back-pressure:** nếu băng cuối CV4 đầy/kẹt (S4B có hàng không thoát) → `Arr4` giữ = 1
   → CV3 chờ → CV2 chờ → CV1 chờ. Tức **CV1 chỉ nạp khi phía sau còn chỗ** — đúng ý
   "cảm biến cuối CV4 có tín hiệu = đầy thì dừng".
4. **Chốt hiện diện `Arr_n`** giữ tín hiệu pallet qua vùng chết → băng **không dừng giữa
   chừng** giữa 2 cảm biến (đã sửa ở bản trước, giữ nguyên).

Chiều nghịch đối xứng (CV-n → CV-(n-1), cảm biến cuối băng là S_nA).

---

## 2. Bảng tag (PLC Tags)

### Ngõ vào – DI
| Tag | Địa chỉ | Mô tả |
|-----|---------|-------|
| `S1A`..`S4B` | %I0.0–%I0.7 | 8 cảm biến NO (mỗi băng 2 đầu A/B), 1 = có pallet |
| `PB_L1_FWD` | %I1.0 | Nút Thuận Box L1 |
| `PB_L1_REV` | %I1.1 | Nút Nghịch Box L1 |
| `PB_L2_FWD` | %I1.2 | Nút Thuận Box L2 |
| `PB_L2_REV` | %I1.3 | Nút Nghịch Box L2 |
| `PB_R1_FWD` | %I1.4 | Nút Thuận Box R1 |
| `PB_R1_REV` | %I1.5 | Nút Nghịch Box R1 |
| `PB_R2_FWD` | %I1.6 | Nút Thuận Box R2 |
| `PB_R2_REV` | %I1.7 | Nút Nghịch Box R2 |
| `EMG_L1`..`EMG_R2` | %I2.0–%I2.3 | 4 nút khẩn NC (1 = bình thường) |

(Địa chỉ cảm biến: S1A %I0.0, S1B %I0.1, S2A %I0.2, S2B %I0.3, S3A %I0.4, S3B %I0.5,
S4A %I0.6, S4B %I0.7.)

### Ngõ ra – DQ
| Tag | Địa chỉ | | Tag | Địa chỉ |
|-----|---------|-|-----|---------|
| `CV1_FWD` | %Q0.0 | | `CV1_REV` | %Q0.1 |
| `CV2_FWD` | %Q0.2 | | `CV2_REV` | %Q0.3 |
| `CV3_FWD` | %Q0.4 | | `CV3_REV` | %Q0.5 |
| `CV4_FWD` | %Q0.6 | | `CV4_REV` | %Q0.7 |
| `Lamp_FWD` | %Q1.0 | | `Lamp_REV` | %Q1.1 |
| `Lamp_EMG` | %Q1.2 | | | |

### Bit trung gian – M & Timer
| Tag | Địa chỉ | Mô tả |
|-----|---------|-------|
| `Safety_OK` | %M10.0 | Chuỗi an toàn lành mạnh |
| `FWD_PB` / `REV_PB` | %M10.1 / %M10.2 | Có nút Thuận / Nghịch đang nhấn |
| `Line_Empty` | %M10.3 | Toàn tuyến trống |
| `FWD_Mode` / `REV_Mode` | %M10.4 / %M10.5 | Chế độ thuận / nghịch đang khóa |
| `Occ1`..`Occ4` | %M11.0–%M11.3 | Cảm biến tức thời từng băng (S_A OR S_B) |
| `Arr1`..`Arr4` | %M12.0–%M12.3 | **Chốt pallet trên băng** (giữ qua vùng chết) |
| `PassedFwd4` | %M12.4 | Pallet đã tới cảm biến xả thuận (S4B) |
| `PassedRev1` | %M12.5 | Pallet đã tới cảm biến xả nghịch (S1A) |
| `Release_F1`..`F3` | %M13.0–%M13.2 | Chốt cho phép thả pallet qua (thuận, CV1/2/3) |
| `Release_R2`..`R4` | %M13.3–%M13.5 | Chốt cho phép thả pallet qua (nghịch, CV2/3/4) |
| `T_F1`,`T_F2`,`T_F3` | IEC TON | Delay xác nhận CV2/3/4 trống (thuận) |
| `T_R2`,`T_R3`,`T_R4` | IEC TON | Delay xác nhận CV1/2/3 trống (nghịch) |
| `T_CHECK` | const | Thời gian delay kiểm tra (mặc định **T#1S**, chỉnh được) |

---

## 3. Chương trình LAD

Ký hiệu: `┤ ├` thường mở · `┤/├` thường đóng · `( )` cuộn · `( S )` set · `( R )` reset.

### Network 1 — Chuỗi an toàn
```
    "EMG_L1"   "EMG_L2"   "EMG_R1"   "EMG_R2"               "Safety_OK"
     %I2.0      %I2.1      %I2.2      %I2.3                   %M10.0
  ────┤ ├────────┤ ├────────┤ ├────────┤ ├────────────────────( )──────
```

### Network 2 — Gom nút Thuận (4 nút song song)
```
   "PB_L1_FWD"   "PB_L2_FWD"   "PB_R1_FWD"   "PB_R2_FWD"     "FWD_PB"
     %I1.0         %I1.2         %I1.4         %I1.6          %M10.1
  ────┤ ├───┬───────┤ ├───┬───────┤ ├───┬───────┤ ├────────────( )──────
            └─────────────┴─────────────┘
```

### Network 3 — Gom nút Nghịch (4 nút song song)
```
   "PB_L1_REV"   "PB_L2_REV"   "PB_R1_REV"   "PB_R2_REV"     "REV_PB"
     %I1.1         %I1.3         %I1.5         %I1.7          %M10.2
  ────┤ ├───┬───────┤ ├───┬───────┤ ├───┬───────┤ ├────────────( )──────
            └─────────────┴─────────────┘
```

### Network 4 — Tuyến trống
```
   "S1A"  "S1B"  "S2A"  "S2B"  "S3A"  "S3B"  "S4A"  "S4B"   "Line_Empty"
  ──┤/├────┤/├────┤/├────┤/├────┤/├────┤/├────┤/├────┤/├───────( )──────
```

### Network 5 — Cảm biến tức thời từng băng (Occ)
```
   "S1A"                 "Occ1"           "S2A"                 "Occ2"
  ──┤ ├───┬──────────────( )──────       ──┤ ├───┬──────────────( )──────
   "S1B"  │                               "S2B"  │
  ──┤ ├───┘                               ──┤ ├───┘

   "S3A"                 "Occ3"           "S4A"                 "Occ4"
  ──┤ ├───┬──────────────( )──────       ──┤ ├───┬──────────────( )──────
   "S3B"  │                               "S4B"  │
  ──┤ ├───┘                               ──┤ ├───┘
```

### Network 6 — Khóa chế độ THUẬN (seal-in)
```
   "FWD_PB"  "S1A"  "REV_Mode"      "Safety_OK"  "Line_Empty"   "FWD_Mode"
  ────┤ ├─────┤ ├─────┤/├─────┬────────┤ ├──────────┤/├───────────( )──────
   "FWD_Mode"                 │
  ────┤ ├───────────────────┘
```

### Network 7 — Khóa chế độ NGHỊCH (seal-in)
```
   "REV_PB"  "S4B"  "FWD_Mode"      "Safety_OK"  "Line_Empty"   "REV_Mode"
  ────┤ ├─────┤ ├─────┤/├─────┬────────┤ ├──────────┤/├───────────( )──────
   "REV_Mode"                 │
  ────┤ ├───────────────────┘
```

### Network 8 — PassedFwd4 (đã tới cảm biến xả thuận)
```
   "FWD_Mode"  "S4B"                  "PassedFwd4"      "Arr4"    "PassedFwd4"
  ────┤ ├───────┤ ├────────────────────( S )──────     ─┤/├────────( R )──────
```

### Network 9 — PassedRev1 (đã tới cảm biến xả nghịch)
```
   "REV_Mode"  "S1A"                  "PassedRev1"      "Arr1"    "PassedRev1"
  ────┤ ├───────┤ ├────────────────────( S )──────     ─┤/├────────( R )──────
```

### Network 10–13 — Chốt pallet trên băng Arr1..Arr4
> SET khi băng có cảm biến tác động; RESET khi pallet đã sang băng kế (thuận: Occ kế;
> nghịch: Occ kế) hoặc đã xả ra ngoài (PassedFwd4 / PassedRev1).
```
N10 Arr1:  ─┤ ├ "Occ1" ────────────────────────────────( S )"Arr1"
           ─┤/├"Occ1"─┬─┤ ├"FWD_Mode"─┤ ├"Occ2"────────┐ ( R )"Arr1"
                      └─┤ ├"REV_Mode"─┤ ├"PassedRev1"───┘

N11 Arr2:  ─┤ ├ "Occ2" ────────────────────────────────( S )"Arr2"
           ─┤/├"Occ2"─┬─┤ ├"FWD_Mode"─┤ ├"Occ3"────────┐ ( R )"Arr2"
                      └─┤ ├"REV_Mode"─┤ ├"Occ1"─────────┘

N12 Arr3:  ─┤ ├ "Occ3" ────────────────────────────────( S )"Arr3"
           ─┤/├"Occ3"─┬─┤ ├"FWD_Mode"─┤ ├"Occ4"────────┐ ( R )"Arr3"
                      └─┤ ├"REV_Mode"─┤ ├"Occ2"─────────┘

N13 Arr4:  ─┤ ├ "Occ4" ────────────────────────────────( S )"Arr4"
           ─┤/├"Occ4"─┬─┤ ├"FWD_Mode"─┤ ├"PassedFwd4"──┐ ( R )"Arr4"
                      └─┤ ├"REV_Mode"─┤ ├"Occ3"─────────┘
```

---

### TIMER DELAY KIỂM TRA + CHỐT NHẢ (Release)

### Network 14 — Timer THUẬN: đếm khi băng kế đã trống
> IN = FWD_Mode AND (CV kế trống = NOT Arr kế). Q lên sau `T_CHECK` (T#1S).
```
   "FWD_Mode"  "Arr2"          T_F1(TON)              "FWD_Mode"  "Arr3"          T_F2(TON)
  ────┤ ├───────┤/├──── IN   Q ──(T_F1.Q)            ────┤ ├───────┤/├──── IN   Q ──(T_F2.Q)
                  PT=T#1S                                              PT=T#1S

   "FWD_Mode"  "Arr4"          T_F3(TON)
  ────┤ ├───────┤/├──── IN   Q ──(T_F3.Q)
                  PT=T#1S
```

### Network 15 — Chốt nhả THUẬN (Release_F1..F3)
> SET khi pallet ở cảm biến cuối băng (S_nB) AND timer xong; RESET khi pallet đã rời băng (NOT Arr_n).
```
N15a:  ─┤ ├"S1B"─┤ ├"T_F1.Q"──( S )"Release_F1"   ;  ─┤/├"Arr1"──( R )"Release_F1"
N15b:  ─┤ ├"S2B"─┤ ├"T_F2.Q"──( S )"Release_F2"   ;  ─┤/├"Arr2"──( R )"Release_F2"
N15c:  ─┤ ├"S3B"─┤ ├"T_F3.Q"──( S )"Release_F3"   ;  ─┤/├"Arr3"──( R )"Release_F3"
```

### Network 16 — Timer NGHỊCH
```
   "REV_Mode"  "Arr1"  → T_R2.Q     "REV_Mode"  "Arr2"  → T_R3.Q     "REV_Mode"  "Arr3"  → T_R4.Q
  ────┤ ├───────┤/├ IN(PT=T#1S)    ────┤ ├───────┤/├ IN(PT=T#1S)    ────┤ ├───────┤/├ IN(PT=T#1S)
```

### Network 17 — Chốt nhả NGHỊCH (Release_R2..R4)
```
N17a:  ─┤ ├"S2A"─┤ ├"T_R2.Q"──( S )"Release_R2"   ;  ─┤/├"Arr2"──( R )"Release_R2"
N17b:  ─┤ ├"S3A"─┤ ├"T_R3.Q"──( S )"Release_R3"   ;  ─┤/├"Arr3"──( R )"Release_R3"
N17c:  ─┤ ├"S4A"─┤ ├"T_R4.Q"──( S )"Release_R4"   ;  ─┤/├"Arr4"──( R )"Release_R4"
```

---

### NGÕ RA MOTOR
> Băng chạy = đang khóa chiều đó AND có pallet trên băng (Arr) AND an toàn OK AND
> (CHƯA tới cảm biến cuối **HOẶC** đã được phép nhả qua). Khi tới cảm biến cuối mà chưa
> được nhả → dừng chờ tại đó.

### Network 18 — CV1 THUẬN
```
   "FWD_Mode" "Arr1"  "Safety_OK"     "S1B"               "CV1_FWD"
  ────┤ ├──────┤ ├──────┤ ├──────┬─────┤/├──────┬───────────( )──────
                              │ "Release_F1"  │
                              └─────┤ ├───────┘
```

### Network 19 — CV2 THUẬN
```
   "FWD_Mode" "Arr2"  "Safety_OK"     "S2B"               "CV2_FWD"
  ────┤ ├──────┤ ├──────┤ ├──────┬─────┤/├──────┬───────────( )──────
                              │ "Release_F2"  │
                              └─────┤ ├───────┘
```

### Network 20 — CV3 THUẬN
```
   "FWD_Mode" "Arr3"  "Safety_OK"     "S3B"               "CV3_FWD"
  ────┤ ├──────┤ ├──────┤ ├──────┬─────┤/├──────┬───────────( )──────
                              │ "Release_F3"  │
                              └─────┤ ├───────┘
```

### Network 21 — CV4 THUẬN (băng xả)
```
   "FWD_Mode" "Arr4"  "Safety_OK"                         "CV4_FWD"
  ────┤ ├──────┤ ├──────┤ ├──────────────────────────────── ( )──────
```

### Network 22 — CV4 NGHỊCH
```
   "REV_Mode" "Arr4"  "Safety_OK"     "S4A"               "CV4_REV"
  ────┤ ├──────┤ ├──────┤ ├──────┬─────┤/├──────┬───────────( )──────
                              │ "Release_R4"  │
                              └─────┤ ├───────┘
```

### Network 23 — CV3 NGHỊCH
```
   "REV_Mode" "Arr3"  "Safety_OK"     "S3A"               "CV3_REV"
  ────┤ ├──────┤ ├──────┤ ├──────┬─────┤/├──────┬───────────( )──────
                              │ "Release_R3"  │
                              └─────┤ ├───────┘
```

### Network 24 — CV2 NGHỊCH
```
   "REV_Mode" "Arr2"  "Safety_OK"     "S2A"               "CV2_REV"
  ────┤ ├──────┤ ├──────┤ ├──────┬─────┤/├──────┬───────────( )──────
                              │ "Release_R2"  │
                              └─────┤ ├───────┘
```

### Network 25 — CV1 NGHỊCH (băng xả)
```
   "REV_Mode" "Arr1"  "Safety_OK"                         "CV1_REV"
  ────┤ ├──────┤ ├──────┤ ├──────────────────────────────── ( )──────
```

### Network 26 — Đèn báo (tùy chọn)
```
   "FWD_Mode" ──( )"Lamp_FWD"   ;   "REV_Mode" ──( )"Lamp_REV"   ;   ─┤/├"Safety_OK"──( )"Lamp_EMG"
```

---

## 4. Diễn giải một chu trình (chiều thuận)

1. Đặt pallet lên CV1 (`S1A=1`), nhấn nút Thuận → `FWD_Mode` (N6). `Occ1=1` → `Arr1` SET → CV1 chạy.
2. Pallet qua vùng chết của CV1 (`Occ1=0` nhưng `Arr1` vẫn 1) → **CV1 chạy liên tục**, tới `S1B`.
3. Tại `S1B`:
   - Nếu CV2 trống (`Arr2=0`) → timer `T_F1` đếm; sau `T_CHECK` → `T_F1.Q=1` → chốt
     `Release_F1` SET → CV1 thả pallet qua CV2. CV2 thấy hàng (`Arr2=1`) → CV2 chạy nhận.
   - Nếu CV2 **còn hàng** (`Arr2=1`) → `T_F1` không đếm → CV1 **dừng chờ tại `S1B`** tới
     khi CV2 thoát hết (`Arr2=0`) + đủ delay.
4. Khi pallet rời hẳn CV1 (`Occ1=0` & `Occ2=1`) → `Arr1` RESET → CV1 dừng; `Release_F1` RESET.
5. Lặp lại qua CV3, CV4. CV4 xả ra ngoài; nếu lối ra bị chặn → `Arr4` giữ 1 →
   **back-pressure** chặn CV3→CV2→CV1 (CV1 ngừng nạp khi tuyến đầy).
6. Hết hàng → `Line_Empty=1` → nhả `FWD_Mode`. Nhấn **Khẩn** → cắt toàn bộ motor.

Chiều nghịch đối xứng (nạp `S4B`, xả `S1A`, dùng `Release_R*`).

---

## 5. Đưa vào TIA Portal & chỉnh
- Import nhanh: External source files → Add `BangTai_4.scl` → *Generate blocks from source*
  (đây là **FB** có sẵn 6 timer TON, gọi từ OB1 với 1 instance DB).
- **Chỉnh delay:** đổi hằng `T_CHECK` (mặc định `T#1S`) trong file SCL hoặc PT của 6 timer.
- Muốn delay khác nhau giữa các điểm → đặt PT riêng cho từng timer.

## 6. Khuyến nghị
- Nối tiếp relay nhiệt / lỗi biến tần vào mỗi network ngõ ra motor.
- Cảm biến NO fail-danger khi đứt dây; cần an toàn cao thì dùng NC.
- Nếu muốn **dừng motor băng xả khi lối ra đầy** (thay vì quay không tải dưới pallet kẹt):
  thêm 1 cảm biến "lối ra đầy" và nối tiếp tiếp điểm `┤/├` của nó vào N21 (CV4_FWD) /
  N25 (CV1_REV).
