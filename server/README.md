# HeartSync 后端服务

> 「心晴校园」App 的配套业务服务器。
> **零第三方依赖** —— 只用 Node.js 内置模块（`http` / `fs` / `path` / `url`），无需 `npm install`。

> 运行方式、模拟器 / 真机连接、AI Key 配置等通用步骤见根目录 [开发调试指南](../docs/开发调试指南.md)，本文只讲服务端自身。

---

## 一、它提供什么

| 能力 | 说明 |
|---|---|
| 每日健康贴士 | 按「一年中的第几天」确定性轮换，同一天内结果稳定不变 |
| 急救知识库 | 10 条校园常见意外的处置流程，含 120 / 就医升级建议 |
| 运动类型库 | 12 种运动类型及其 MET 代谢当量值，用于估算运动消耗 |
| 情绪分析 | 服务端规则引擎，依据分数 + 关键词做确定性情绪研判，输出风险等级与建议 |
| AI 对话代理 | 代理 OpenAI 兼容接口（通义千问 / DeepSeek / 智谱 / Kimi）；**未配置密钥时自动降级为本地知识库回复**，App 依然可完整演示 |
| 健康报告 | 睡眠 / 运动 / 情绪三维评分，规则与 Android 端 `HealthScore.calculate()` **完全一致** |
| 健康检查 | `/api/health` 返回服务状态、版本、ISO8601 时间与运行时长 |

### 统一响应格式

除 `/api/health` 外，所有接口均返回统一封装：

```jsonc
// 成功
{ "code": 0, "message": "success", "data": { /* ... */ } }

// 失败
{ "code": 500, "message": "错误描述", "data": null }
```

---

## 二、目录结构

```
server/
├── server.js                 # 主服务入口（全部接口 + 启动横幅）
├── test_api.js               # 接口自动化测试（44 个用例）
├── 自检.bat                   # Windows 一键自检（Node / .env / 探活）
├── start.bat / start.sh      # 一键启动
├── .env.example              # 配置模板（复制为 .env 后填入自己的 Key）
├── data/tips.json            # 健康贴士数据（缺失时自动用 server.js 内置兜底）
└── README.md                 # 本文档
```

---

## 三、接口清单

| 方法 | 路径 | 请求参数 | 响应说明 |
| --- | --- | --- | --- |
| GET | `/api/health` | 无 | `{"status":"ok","service":"HeartSync Server","version":"1.0","time":"<ISO8601>","uptime":<秒>}` |
| GET | `/api/tip` | 无 | `data`：`{"tip":"中文贴士","category":"睡眠\|运动\|情绪\|急救","date":"yyyy-MM-dd"}` |
| GET | `/api/firstaid` | 无 | `data`：`{"total":10,"items":[...]}`，每条含 `id/category/title/symptom/steps/caution/severity` |
| GET | `/api/exercise` | 无 | `data`：`{"types":[{"name":"跑步","met":8.3,"icon":"🏃"}, ...]}`，共 12 种 |
| POST | `/api/mood` | `{"score":1-5,"tags":"关键词","diary":"日记"}` | `data`：`{"summary","cause","suggestions":[3条],"risk":"低\|中\|高","quote"}` |
| POST | `/api/ai/chat` | `{"prompt":"...","system":"...","history":[{"role","content"}]}` | 有密钥：`{"reply","model"}`；无密钥：`message` 为 `offline-fallback` |
| POST | `/api/report` | `{"sleepAvgMinutes":420,"sleepAvgQuality":4,"exerciseTotal":180,"moodAvg":4}` | `data`：`{"score":0-100,"level","comment","dimensions":[3],"advice":[3]}` |
| * | 其他路径 | — | `{"code":404,"message":"Not Found","data":null}` |

---

## 四、请求 / 响应示例

**情绪分析**

```bash
curl -s -X POST http://127.0.0.1:8080/api/mood \
  -H "Content-Type: application/json" \
  -d '{"score":1,"tags":"焦虑,紧张","diary":"最近考试压力很大，晚上总是睡不着"}'
```

```jsonc
{
  "code": 0,
  "message": "success",
  "data": {
    "summary": "本次记录心情 1 分，整体状态很差、情绪明显低落。从你的描述里读到「焦虑紧张、睡眠不足」等信号。",
    "cause": "近期任务或人际压力较高，身心长期处于警戒状态，休息不足又进一步放大了焦虑感。",
    "suggestions": [
      "试试 4-7-8 呼吸法（吸气 4 秒、屏息 7 秒、呼气 8 秒）重复 4 轮，再配合 5 分钟散步或拉伸。",
      "今晚提前 30 分钟上床，睡前 1 小时远离手机和强光；固定起床时间比固定入睡时间更重要。",
      "建议尽快联系学校心理健康教育中心或辅导员做一次面谈；如果出现自伤念头，请立即拨打 12356 心理援助热线或 120 急救电话。"
    ],
    "risk": "高",
    "quote": "现在的难受是真实的，但它不是终点，先照顾好身体。"
  }
}
```

> **规则命中说明**：`焦虑/紧张/压力` → 放松类建议；`疲惫/困/睡不好` → 睡眠类建议；`低落/孤独/难过` → 情绪支持类建议；`开心/充实/有动力` → 状态维持类建议。风险等级由分数唯一决定（`≤1` 高、`=2` 中、其余低）。

**AI 对话（离线兜底）**

```bash
curl -s -X POST http://127.0.0.1:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt":"流鼻血怎么办","system":"你是急救助手"}'
```

未设置 `AI_API_KEY` 时返回：

```jsonc
{
  "code": 0,
  "message": "offline-fallback",
  "data": { "reply": "【急救知识库】流鼻血（意外伤害）\n常见症状：...\n处理步骤：\n1. ...\n注意事项：...\n..." }
}
```

**健康报告**

```bash
curl -s -X POST http://127.0.0.1:8080/api/report \
  -H "Content-Type: application/json" \
  -d '{"sleepAvgMinutes":420,"sleepAvgQuality":4,"exerciseTotal":180,"moodAvg":4}'
```

该示例结果为 `score = 36 + 30 + 24 = 90`，`level` 为「优秀」，三个维度的 `score` 之和恰好等于总分。

---

## 五、大模型配置

### 方式一：`.env` 文件（推荐）

```bash
cp .env.example .env    # Windows: copy .env.example .env
```

然后填入自己的 Key：

```properties
AI_API_KEY=sk-你的Key
AI_API_BASE=https://api.deepseek.com/v1
AI_MODEL=deepseek-chat
PORT=8080
```

> `.env` 已被 `.gitignore` 排除，**切勿提交到任何公开仓库**。

### 方式二：环境变量（临时覆盖）

```bash
# macOS / Linux
AI_API_KEY=sk-xxx node server.js

# Windows PowerShell
$env:AI_API_KEY="sk-xxx"; node server.js
```

### 强制离线模式

设置 `AI_FORCE_OFFLINE=true` 会**忽略 `.env` 与环境变量中的所有 Key**，强制走本地知识库兜底。用于无 Key 的机器演示，或让自动化测试结果确定化。

---

## 六、自动化测试

```bash
node test_api.js
```

44 个用例覆盖全部接口。测试会自行 spawn 一个服务端子进程：

> ⚠️ **跑测试前请确认 8080 端口空闲**。若端口已被占用，新进程启动失败，测试会打到旧实例上导致结果失真。

---

## 七、常见问题

**端口被占用**

```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# macOS / Linux
lsof -i :8080
kill -9 <PID>

# 或换端口启动
PORT=8081 node server.js
```

**Windows 防火墙拦截**

首次运行 Node.js 会弹出「Windows 安全中心警报」，需勾选 **「专用网络」** 并允许访问。临时放行：

```cmd
netsh advfirewall firewall add rule name="HeartSync 8080" dir=in action=allow protocol=TCP localport=8080
```

**App 连不上服务器**

按顺序排查：服务是否启动（浏览器打开 `/api/health`）→ 地址是否写对 → 端口是否一致 → 真机是否与电脑同 Wi-Fi → 改用 `adb reverse` → 看服务端控制台有无请求日志。
