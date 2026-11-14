# Fork 开发说明

本仓库是 [AIXINJUELUOAI/NativeComplements](https://github.com/AIXINJUELUOAI/NativeComplements) 的个人开发 fork，保留上游作者署名、Apache-2.0 许可证及第三方许可说明。第三方主题素材不随源码或 APK 提供。

当前在 main 继续维护，上游基线为 `342023a`。首批改动包含：素材库匹配应用批量选择、通知图标50%～200%大小、独立通知图标库和透明留白内实心方块修复。上游 test5 原本已有基础批量模式选择，不能将其算作本 fork 新增。

## 远端

- `origin`：个人 fork `leyodr9000/NativeComplements`。
- `upstream`：原项目 `AIXINJUELUOAI/NativeComplements`。
- 保留完整历史，通过普通合并/冲突评审引入上游更新，不重置用户工作或强制推送。

## 自动检查

GitHub Actions 的 **Fork icon checks** 在 main 代码变更时运行，也可从 Actions 页面手动触发。使用 Ubuntu x86_64 和 JDK21，安装项目指定 SDK 后：

1. 核对原始基线和归档宿主接口。
2. 执行图标包、策略、原生像素、双库导入和批量 Compose 测试。
3. 测试通过后构建 R8 release、运行 lint 和关键反射名称校验。
4. 保存测试报告/截图；全部成功才上传未签名 APK 与 R8 mapping。

检查仅需仓库读取权限，没有仓库写入令牌、部署或自动发布步骤。没有上传主题包、用户设备数据或签名私钥。

图形测试在手机 Linux ARM64 上不能正常初始化；GitHub x86_64 运行结果需要单独核对，不能把本地仅编译通过说成图形测试通过。CI 报错须修复或明确说明，不跳过失败测试来生成成功产物。

## 构建和签名

正式 Gradle 签名配置仍引用本地原签名材料，未把密钥提交仓库。CI 用独立 init 脚本临时关闭 release 签名，产物名称明确带 UNSIGNED：**该 APK 不可直接安装**。

fork 代码和 GitHub 登录不会赋予原作者签名私钥。签名有两种不同的使用结果：

- 原签名持有者签名：可测试覆盖升级原版，保留已有数据。
- 自己长期持有的独立签名：可用于自己的后续版本升级，但不能直接覆盖原签名安装；首次安装及数据迁移、是否与原版共存需另行设计。

不要每次生成临时签名来发布，否则自己的连续版本也不能覆盖升级。若以后启用签名 CI，使用长期密钥和仓库受保护 Secrets，不能将私钥或口令写入源码/日志。当前没有创建、上传私钥，也未改动已安装模块。

## 本地检查

```bash
python3 tests/verify_runtime.py
python3 tests/verify_target.py
python3 tests/verify_test4_target.py
bash ./gradlew --no-daemon :app:testDebugUnitTest \
  --tests 'dev.local.sunwindowdemo.features.iconlayers.*' \
  --tests 'dev.local.sunwindowdemo.IconBatchUiTest'
bash ./gradlew --no-daemon -I tests/unsigned-ci.init.gradle :app:assembleRelease
```

测试图标库使用自有或获得许可的素材，CI 只包含合成测试图片，不包含用户自用的苍绿主题。
