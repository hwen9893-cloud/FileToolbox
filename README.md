# 文件工具箱 (FileToolbox)

一款 Android 本地文件处理工具，支持照片、PDF、Word、Excel 四大类文件的常用操作，所有处理均在设备端完成，无需联网。

## 功能概览

### 照片处理
| 功能 | 说明 |
|------|------|
| 背景替换 | 选择图片后，从预设颜色（白/蓝/红/浅蓝/灰/深蓝）中选取背景色进行替换 |
| 扫描成PDF | 选择多张图片（最多20张），按顺序生成 A4 尺寸的 PDF 文档 |
| 图片压缩 | 通过滑块设定目标大小（50KB\~2MB），二分法自动寻找最优压缩质量 |

### PDF 工具
| 功能 | 说明 |
|------|------|
| PDF拆分 | 选择起止页码范围，将 PDF 拆分为独立文件 |
| 提取页面 | 输入页码，提取单页为新 PDF |
| 提取图片 | 遍历 PDF 所有页面的 XObject，提取嵌入图片并以 PNG 保存 |

### Word 文档
| 功能 | 说明 |
|------|------|
| Word转PDF | 读取 .docx 的段落（含标题层级、对齐方式）和表格，生成 PDF |
| 文档合并 | 将多个 .docx 按顺序合并，保留文字样式，文档间自动分页 |
| 提取图片 | 提取 .docx 中所有嵌入图片，支持单张或批量保存 |

### Excel 表格
| 功能 | 说明 |
|------|------|
| Excel转PDF | 将 .xlsx/.xls 所有工作表转为 PDF 表格，支持多种单元格类型 |
| CSV转Excel | 解析 CSV（含引号字段），转为 .xlsx，自动识别数字、列宽自适应 |
| 表格合并 | 将多个 Excel 文件的工作表汇总到一个文件中 |

## 技术栈

- **UI**: Jetpack Compose + Material 3
- **导航**: Navigation Compose
- **PDF 处理**: iText7
- **Word/Excel 处理**: Apache POI
- **图片加载**: Coil
- **扫描**: CameraX + ML Kit Document Scanner
- **最低 SDK**: 26 (Android 8.0)
- **目标 SDK**: 34 (Android 14)

## 项目结构

```
app/src/main/java/com/toolbox/filetoolbox/
├── FileToolboxApp.kt          # Application 类
├── MainActivity.kt            # 入口 Activity
├── navigation/
│   ├── NavRoutes.kt           # 路由定义
│   └── NavGraph.kt            # 导航图
└── ui/
    ├── components/
    │   └── CommonComponents.kt # 公共组件（TopBar、FeatureCard、GradientButton 等）
    ├── theme/
    │   ├── Theme.kt           # 主题色（深色/浅色）
    │   └── Type.kt            # 字体样式
    └── screens/
        ├── home/HomeScreen.kt
        ├── photo/
        │   ├── PhotoScreen.kt
        │   ├── PhotoBackgroundReplaceScreen.kt
        │   ├── PhotoCompressScreen.kt
        │   └── PhotoScanToPdfScreen.kt
        ├── pdf/
        │   ├── PdfScreen.kt
        │   ├── PdfSplitScreen.kt
        │   ├── PdfExtractPageScreen.kt
        │   └── PdfExtractImageScreen.kt
        ├── word/
        │   ├── WordScreen.kt
        │   ├── WordToPdfScreen.kt
        │   ├── WordMergeScreen.kt
        │   └── WordExtractImageScreen.kt
        └── excel/
            ├── ExcelScreen.kt
            ├── ExcelToPdfScreen.kt
            ├── CsvToExcelScreen.kt
            └── ExcelMergeScreen.kt
```

## 下载安装

### 直接下载

仓库根目录提供了可直接安装的 APK 文件：

[**FileToolbox-release.apk**](FileToolbox-release.apk)

### 安装步骤

1. 在手机浏览器中打开本仓库页面，点击 `FileToolbox-release.apk` 文件，然后点击下载按钮
2. 下载完成后，点击通知栏中的下载完成提示，或在文件管理器的「下载」目录中找到该文件
3. 系统会提示「不允许安装未知来源应用」，点击「设置」进入权限页面
4. 开启「允许此来源安装应用」（不同品牌手机路径略有不同）：
   - **小米/红米**: 设置 → 隐私保护 → 特殊权限 → 安装未知应用
   - **华为/荣耀**: 设置 → 安全 → 更多安全设置 → 安装未知应用
   - **OPPO/realme**: 设置 → 权限隐私 → 安装未知应用
   - **vivo**: 设置 → 安全与隐私 → 安装未知应用
   - **三星**: 设置 → 生物识别与安全 → 安装未知应用
5. 返回安装界面，点击「继续安装」→「安装」
6. 安装完成后点击「打开」即可使用

> **要求**: Android 8.0 (API 26) 及以上系统版本

## 构建与运行

### 环境要求

- Android Studio Hedgehog (2023.1) 或更高版本
- JDK 17
- Gradle 8.x
- Android SDK 34

### 构建步骤

```bash
# 克隆项目
git clone <repo-url>
cd FileToolbox

# 调试构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 测试使用流程

> 以下流程在 Android 8.0+ 真机或模拟器上操作。首次启动时需授予存储/相机权限。

### 1. 照片处理

**图片压缩**
1. 首页点击「照片处理」→「图片压缩」
2. 点击图片区域，从相册选择一张 JPG/PNG 图片
3. 查看「原始大小」显示
4. 拖动滑块设置目标大小（如 200KB）
5. 点击「压缩图片」，等待处理完成
6. 查看「压缩后」大小及节省百分比
7. 点击「保存」，确认提示"已保存: compressed_xxx.jpg"

**扫描成PDF**
1. 首页 →「照片处理」→「扫描成PDF」
2. 点击空白区域添加图片，选择 2\~3 张照片
3. 左右滑动查看缩略图，确认顺序
4. 可点击缩略图上的 × 删除，也可点击右侧「添加」继续追加
5. 点击「生成PDF文档」
6. 确认提示"PDF已保存: scan_xxx.pdf"

**背景替换**
1. 首页 →「照片处理」→「背景替换」
2. 选择一张人像照片
3. 从底部颜色栏选择目标背景色（如蓝色）
4. 点击「应用背景」查看效果
5. 满意后点击「保存图片」

### 2. PDF 工具

**PDF拆分**
1. 首页 →「PDF工具」→「PDF拆分」
2. 点击选择一个多页 PDF 文件
3. 确认文件名和总页数显示正确
4. 拖动滑块设置起始页和结束页（如第 2 页到第 5 页）
5. 点击「拆分PDF」
6. 确认提示"已保存: split_2-5_xxx.pdf (4页)"

**提取页面**
1. 首页 →「PDF工具」→「提取页面」
2. 选择 PDF 文件
3. 在输入框输入页码（如 3）
4. 点击「提取页面」
5. 确认提示"已保存: page_3_xxx.pdf"

**提取图片**
1. 首页 →「PDF工具」→「提取图片」
2. 选择一个含有图片的 PDF
3. 点击「开始提取」
4. 等待处理完成，查看"共找到 N 张图片"
5. 网格中浏览提取的图片，点击单张可保存
6. 或点击右上角「保存全部」批量保存

### 3. Word 文档

**Word转PDF**
1. 首页 →「Word文档」→「Word转PDF」
2. 选择一个 .docx 文件
3. 点击「开始转换」
4. 确认提示"已保存: xxx.pdf"
5. 用文件管理器打开 PDF 确认段落和表格内容

**文档合并**
1. 首页 →「Word文档」→「文档合并」
2. 点击「添加文档」，选择 2 个以上 .docx 文件
3. 列表中确认文件顺序（编号 1、2、3...）
4. 可点击 × 删除不需要的文件
5. 点击「合并文档」
6. 确认提示"已保存: merged_xxx.docx"

**提取图片**
1. 首页 →「Word文档」→「提取图片」
2. 选择含有图片的 .docx 文件
3. 点击「开始提取」
4. 查看提取的图片网格，点击保存或「保存全部」

### 4. Excel 表格

**Excel转PDF**
1. 首页 →「Excel表格」→「Excel转PDF」
2. 选择 .xlsx 或 .xls 文件
3. 确认文件名和工作表数量
4. 点击「开始转换」
5. 确认提示"已保存: xxx.pdf"

**CSV转Excel**
1. 首页 →「Excel表格」→「CSV转Excel」
2. 选择一个 .csv 文件
3. 查看「数据预览」区域，确认前 5 行数据正确
4. 点击「开始转换」
5. 确认提示"已保存: xxx.xlsx (共 N 行)"

**表格合并**
1. 首页 →「Excel表格」→「表格合并」
2. 点击「添加文件」，选择 2 个以上 Excel 文件
3. 确认列表中文件齐全
4. 点击「合并文件」
5. 确认提示"已保存: merged_xxx.xlsx"
6. 打开合并文件，检查各工作表是否按「文件名_表名」命名

## 输出文件位置

所有处理后的文件保存在应用私有目录下：

| 类型 | 路径 |
|------|------|
| 图片 | `Android/data/com.toolbox.filetoolbox/files/Pictures/` |
| 文档/PDF | `Android/data/com.toolbox.filetoolbox/files/Documents/` |

可通过文件管理器的「Android/data」目录找到，或使用 `adb pull` 导出：

```bash
adb shell ls /sdcard/Android/data/com.toolbox.filetoolbox/files/Documents/
adb pull /sdcard/Android/data/com.toolbox.filetoolbox/files/Documents/merged_xxx.docx .
```

## 许可证

私有项目，版权所有。
