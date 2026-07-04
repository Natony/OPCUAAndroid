# HMI (EasyBuilder Pro) — Đọc dữ liệu vị trí kệ CHỈ 1 LẦN khi nhấn nút

## Vấn đề
Macro đang chạy theo chu kỳ (đặt 0ms) nên nó **liên tục** đọc `DB20` qua đường
truyền PLC và ghi biến hiển thị. Hậu quả:
- Chiếm CPU tối đa của HMI.
- Nghẽn truyền thông với PLC (đọc DB20 không ngừng).
- Biến hiển thị bị ghi liên tục, dễ nhấp nháy.

Mong muốn: **chỉ xử lý đúng 1 lần mỗi khi nhấn vào một ô vị trí trên màn hình.**

## Ý tưởng
Dùng đúng cách bạn đã nghĩ: tạo **1 bit nội bộ làm "cờ trigger"** (ví dụ `LB-100`).
- Nút ô vị trí khi nhấn: (1) ghi offset của ô đó vào `LW-9200`, (2) bật cờ `LB-100 = ON`.
- Macro: nếu cờ = OFF thì **thoát ngay** (không đọc DB); nếu = ON thì xử lý rồi
  **reset cờ về OFF** ⇒ mỗi lần nhấn chỉ chạy đúng 1 lần.

> Lưu ý vùng địa chỉ: tránh dùng bit/word hệ thống. `LB-9000` trở lên và nhiều
> `LW-90xx` là vùng hệ thống của EasyBuilder Pro. Hãy chọn bit trong vùng người
> dùng (ví dụ `LB-100`). `LB-100` ở đây chỉ là ví dụ, bạn đổi tùy dự án.

## Macro đã sửa (dùng được cho cả 2 cách trigger bên dưới)

```c
macro_command main()
    short off, oper, shut, func
    int   pallet
    bool  trig

    // (1) Chi chay khi co "co" yeu cau xu ly (LB-100 do nut nhan bat len)
    GetData(trig, "Local HMI", LB, 100, 1)
    if trig == false then
        return                                   // chua nhan -> thoat ngay, KHONG doc DB
    end if

    // (2) Reset co ngay lap tuc -> moi lan nhan chi xu ly dung 1 lan
    trig = false
    SetData(trig, "Local HMI", LB, 100, 1)

    // (3) Lay offset cua o dang chon
    GetData(off, "Local HMI", LW, 9200, 1)

    // (4) Doc DB20 tai offset dong (giu nguyen dia chi nhu ban dang dung)
    GetData(pallet, "Master", DB20, 26 + off, 1)
    GetData(oper,   "Master", DB20, 30 + off, 1)
    GetData(shut,   "Master", DB20, 32 + off, 1)
    GetData(func,   "Master", DB20, 34 + off, 1)

    // (5) Ghi ra bien man hinh de hien thi
    SetData(off,    "Local HMI", LW, 9203, 1)
    SetData(pallet, "Local HMI", LW, 5000, 1)    // DInt: 2 word
    SetData(oper,   "Local HMI", LW, 5004, 1)
    SetData(shut,   "Local HMI", LW, 5006, 1)
    SetData(func,   "Local HMI", LW, 5008, 1)
end macro_command
```

Thay đổi so với bản gốc:
- Thêm bước (1)(2): đọc/kiểm tra/reset cờ `LB-100`.
- **Bỏ `SYNC_TRIG_MACRO(0)`**: dòng này gọi macro số 0 mỗi lần chạy, không liên
  quan chống polling và góp phần làm nặng máy. Chỉ thêm lại nếu macro 0 là một
  chương trình con bạn thực sự cần.

## Cách trigger

### Cách A — Khuyên dùng: KHÔNG polling, chạy theo sự kiện nhấn
1. Chọn 1 bit người dùng làm cờ: `LB-100`.
2. Mỗi nút ô vị trí dùng **Combo Button** (thực hiện tuần tự nhiều lệnh):
   - Action 1: **Set Word** `LW-9200 = <offset của ô đó>`
   - Action 2: **Set Bit** `LB-100 = ON`
   (Ghi offset trước, bật cờ sau ⇒ không bị đọc nhầm offset cũ.)
3. Vào **Object list → PLC Control → Add**:
   - Type of control: **Execute macro program**
   - Chọn macro ở trên
   - Trigger address: `LB-100`
4. Macro tự reset `LB-100 = OFF` ở bước (2) ⇒ chạy đúng 1 lần mỗi lần nhấn.
5. **Bỏ chế độ chạy chu kỳ** cũ.

### Cách B — Giữ macro chạy chu kỳ nhưng có "cổng" bit
Nếu vì lý do khác vẫn muốn macro chạy chu kỳ:
- Đổi chu kỳ **0ms → 100–200ms** (đừng để 0ms).
- Macro ở trên khi cờ OFF sẽ `return` ngay, gần như không tốn tài nguyên; chỉ khi
  cờ ON mới đọc DB, xong tự tắt cờ.
- Nút ô vị trí cấu hình y như Cách A (Set Word offset + Set Bit `LB-100 = ON`).

> Nếu không dùng được Combo Button, có thể **chồng 2 đối tượng** lên cùng vị trí ô:
> một **Set Word** (ghi offset vào `LW-9200`) và một **Set Bit** (Set ON `LB-100`).

## Ghi chú về offset các ô (theo bố cục màn hình)
- **Kệ 1-1**: có ô **1..17**.
- **Kệ 2-1** và **Kệ 2-2**: mỗi kệ có ô **1..16**.
- Giá trị offset ghi vào `LW-9200` cho từng ô lấy theo bảng của bạn
  (quy tắc: cùng kệ mỗi hàng **+10**; sang kệ kế **+326**).
- Phần sửa "chỉ chạy 1 lần" **không phụ thuộc** vào giá trị offset cụ thể —
  mỗi nút chỉ cần ghi đúng offset của nó rồi bật cờ.

Tùy chọn an toàn: nếu muốn chặn offset ngoài vùng, có thể kiểm tra biên `off`
trong macro trước bước (4) và `return` nếu nằm ngoài dải cho phép.
