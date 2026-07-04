# Web 报告入口

上级：[[_M2-日报周报月报生成]]
下级：无
依赖：Report API

---

## 场景

用户在浏览器 Console 管理素材并生成报告。

## 触发

打开 `/console`，切到“报告”区域，点击生成。

## 逻辑

- 表单字段：报告类型、开始日期、结束日期、模板、补充要求。
- 点击生成时调用 `/api/actions/generate-report`。
- 成功后显示 report_id、校验结果、下载按钮。

## 状态 / 边界

- Token 缺失：复用现有认证提示。
- 生成中：按钮 disabled，显示 loading。
