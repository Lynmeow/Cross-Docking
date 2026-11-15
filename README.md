# Cổng quản lý kho trung chuyển Cross-Docking

Đây là đồ án môn Phương pháp phát triển phần mềm hướng đối tượng (OOSD), lớp 12_ĐH_CNPM2, Trường Đại học Tài nguyên và Môi trường TP. Hồ Chí Minh.

Sinh viên thực hiện: Lưu Thị Ngọc Linh (MSSV 1250080099)

Giảng viên hướng dẫn: ThS. Phạm Trọng Huynh

## Giới thiệu

Website giúp quản lý kho trung chuyển theo mô hình cross-docking: hàng đến kho được phân loại rồi chuyển ngay lên các chuyến xe đi tiếp. Hệ thống quản lý cửa dock, lịch xe, thông báo giao hàng (ASN), pallet và các ngoại lệ phát sinh trong quá trình chuyển tải.

Đề tài tập trung vào hai việc chính:

- Theo dõi chuỗi giám sát của từng pallet. Mỗi lần pallet đổi trạng thái hoặc đổi người giữ, hệ thống ghi lại một sự kiện có mã băm SHA-256 nối với sự kiện trước, nên nếu ai đó sửa dữ liệu trực tiếp trong CSDL thì hệ thống sẽ phát hiện được.
- Không cho một pallet bị gán vào hai chuyến xe, kể cả khi nhiều người thao tác cùng lúc.

## Công nghệ sử dụng

- Java 25
- Spring Boot 3.5, Spring Security 6, Spring Data JPA (Hibernate)
- Thymeleaf, Bootstrap 5
- Microsoft SQL Server
- Maven, Visual Studio Code, Git

## Các vai trò trong hệ thống

- Admin: cấp và quản lý tài khoản, xem nhật ký hoạt động.
- Quản lý kho: quản lý cửa dock, lên lịch xe, tạo ASN, in lịch xe, theo dõi tổng quan và xử lý ngoại lệ.
- Nhân viên Inbound: nhận xe nhập, nhận pallet theo ASN, in tem pallet, bàn giao pallet cho bên xuất.
- Nhân viên Outbound: nhận bàn giao, gán pallet vào chuyến, xếp xe, niêm phong và cho xe xuất bến, in biên bản.

## Cấu trúc thư mục

- docs/so-do: các sơ đồ dùng trong báo cáo (ảnh PNG)
- docs/so-do-drawio: các sơ đồ dạng draw.io, file TatCaSoDo.drawio chứa tất cả sơ đồ
- docs/giao-dien: ảnh chụp giao diện website
- src/main/java/com/crossdock: mã nguồn Java, chia theo các gói config, controller, service, model, repository, security, exception, util
- src/main/resources/templates: các trang giao diện Thymeleaf
- src/main/resources/application.properties: file cấu hình
- pom.xml: khai báo thư viện cho Maven

## Cài đặt và chạy

1. Cài JDK 25, Maven và Microsoft SQL Server. Trong SQL Server cần bật đăng nhập bằng tài khoản SQL (SQL Server Authentication) và bật TCP/IP.

2. Tạo cơ sở dữ liệu trống tên CrossDocking. Các bảng sẽ được tự tạo ở lần chạy đầu tiên.

```sql
CREATE DATABASE CrossDocking;
```

3. Mở file src/main/resources/application.properties, sửa lại địa chỉ và cổng SQL Server nếu máy bạn khác (mặc định là localhost, cổng 1450).

4. Khai báo mật khẩu SQL Server. Mật khẩu không được lưu trên GitHub nên bạn chọn một trong hai cách sau.

Cách 1: đặt biến môi trường trong CMD trước khi chạy

```
set DB_PASSWORD=mat_khau_cua_ban
```

Cách 2: tạo file src/main/resources/application-secret.properties với nội dung

```
spring.datasource.password=mat_khau_cua_ban
```

File này đã được bỏ qua trong .gitignore nên sẽ không bị đưa lên GitHub.

5. Chạy chương trình

```
mvn spring-boot:run
```

6. Mở trình duyệt và vào http://localhost:8080

## Tài khoản dùng thử

Ở lần chạy đầu tiên, hệ thống tự tạo dữ liệu mẫu với các tài khoản: admin (Admin), quanly (Quản lý kho), inbound1 (Nhân viên Inbound), outbound1 và outbound2 (Nhân viên Outbound). Mật khẩu ban đầu được khai báo trong file DataSeeder.java thuộc gói config. Nên đổi mật khẩu sau khi đăng nhập.
