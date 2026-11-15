'use strict';

/**
 * HeartSync 后端服务自动化自测脚本
 * ---------------------------------------------------------------------------
 * 用法：node test_api.js
 *
 * 脚本会：
 *   1. 在后台启动 server.js（端口可用 PORT 覆盖，默认 8080）
 *   2. 逐个校验全部接口的 HTTP 状态、JSON 封装与字段完整性
 *   3. 逐条打印 PASS / FAIL
 *   4. 测试结束后关闭服务进程并输出汇总
 *
 * 零依赖，只使用 Node.js 内置模块。
 */

const http = require('http');
const path = require('path');
const { spawn } = require('child_process');

const PORT = Number(process.env.PORT) || 8080;
const BASE = 'http://127.0.0.1:' + PORT;

let passed = 0;
let failed = 0;
const failures = [];

function record(name, ok, detail) {
  if (ok) {
    passed++;
    console.log('  PASS  ' + name);
  } else {
    failed++;
    failures.push(name + (detail ? ' -> ' + detail : ''));
    console.log('  FAIL  ' + name + (detail ? '  (' + detail + ')' : ''));
  }
}

/** 发起 HTTP 请求，返回 {status, headers, body, json} */
function request(method, urlPath, body) {
  return new Promise(function (resolve, reject) {
    const data = body === undefined ? null : Buffer.from(JSON.stringify(body), 'utf8');
    const options = {
      host: '127.0.0.1',
      port: PORT,
      path: urlPath,
      method: method,
      headers: {
        'Accept': 'application/json'
      }
    };
    if (data) {
      options.headers['Content-Type'] = 'application/json; charset=utf-8';
      options.headers['Content-Length'] = data.length;
    }

    const req = http.request(options, function (res) {
      const chunks = [];
      res.on('data', function (chunk) { chunks.push(chunk); });
      res.on('end', function () {
        const text = Buffer.concat(chunks).toString('utf8');
        let json = null;
        try {
          json = JSON.parse(text);
        } catch (e) {
          json = null;
        }
        resolve({ status: res.statusCode, headers: res.headers, text: text, json: json });
      });
    });

    req.on('error', reject);
    if (data) {
      req.write(data);
    }
    req.end();
  });
}

/** 等待服务器端口就绪 */
function waitForServer(tries) {
  const max = tries === undefined ? 40 : tries;
  return new Promise(function (resolve, reject) {
    let attempt = 0;
    function probe() {
      attempt++;
      const req = http.get(BASE + '/api/health', function (res) {
        res.resume();
        if (res.statusCode === 200) {
          resolve();
        } else {
          retry();
        }
      });
      req.on('error', retry);
      req.setTimeout(1000, function () {
        req.destroy();
        retry();
      });

      function retry() {
        if (attempt >= max) {
          reject(new Error('服务器启动超时，请检查端口 ' + PORT + ' 是否被占用'));
          return;
        }
        setTimeout(probe, 250);
      }
    }
    probe();
  });
}

/** 校验统一响应封装 */
function checkEnvelope(res, name) {
  const ok = res.status === 200
    && res.json !== null
    && res.json.code === 0
    && res.json.message === 'success'
    && res.json.data !== undefined;
  record(name + ' 响应封装 code=0/message=success/data 非空', ok,
    ok ? '' : ('status=' + res.status + ' body=' + res.text.slice(0, 160)));
  return res.json && res.json.data ? res.json.data : {};
}

async function run() {
  console.log('');
  console.log('========================================================');
  console.log('  HeartSync 服务器接口自动化测试');
  console.log('  目标地址: ' + BASE);
  console.log('========================================================');
  console.log('');

  /* ---------- 1. GET /api/health ---------- */
  console.log('[1] GET /api/health');
  const health = await request('GET', '/api/health');
  const healthOk = health.status === 200
    && health.json
    && health.json.status === 'ok'
    && health.json.service === 'HeartSync Server'
    && health.json.version === '1.0'
    && typeof health.json.time === 'string'
    && /^\d{4}-\d{2}-\d{2}T/.test(health.json.time)
    && typeof health.json.uptime === 'number';
  record('健康检查返回 status/service/version/time/uptime', healthOk,
    healthOk ? '' : health.text.slice(0, 200));
  record('健康检查 Content-Type 为 application/json; charset=utf-8',
    /application\/json;\s*charset=utf-8/i.test(String(health.headers['content-type'])),
    String(health.headers['content-type']));

  /* ---------- 2. GET /api/tip ---------- */
  console.log('[2] GET /api/tip');
  const tip = await request('GET', '/api/tip');
  const tipData = checkEnvelope(tip, '每日贴士');
  const tipOk = typeof tipData.tip === 'string' && tipData.tip.length > 0
    && ['睡眠', '运动', '情绪', '急救'].indexOf(tipData.category) >= 0
    && /^\d{4}-\d{2}-\d{2}$/.test(String(tipData.date));
  record('贴士字段 tip/category/date 完整且取值合法', tipOk,
    tipOk ? '' : JSON.stringify(tipData));
  const tip2 = await request('GET', '/api/tip');
  record('同一天内贴士结果稳定（确定性轮换）',
    tip2.json.data.tip === tipData.tip, tip2.json.data.tip);

  /* ---------- 3. GET /api/firstaid ---------- */
  console.log('[3] GET /api/firstaid');
  const aid = await request('GET', '/api/firstaid');
  const aidData = checkEnvelope(aid, '急救知识库');
  record('急救条目数量为 10', aidData.items && aidData.items.length === 10,
    'actual=' + (aidData.items ? aidData.items.length : 'null'));
  record('data.total 与 items 长度一致',
    aidData.total === (aidData.items ? aidData.items.length : -1),
    'total=' + aidData.total);

  const requiredKeys = ['id', 'category', 'title', 'symptom', 'steps', 'caution', 'severity'];
  let keyProblem = '';
  let idSet = new Set();
  if (Array.isArray(aidData.items)) {
    aidData.items.forEach(function (item, index) {
      const keys = Object.keys(item);
      requiredKeys.forEach(function (key) {
        if (keys.indexOf(key) < 0) {
          keyProblem = '第 ' + (index + 1) + ' 条缺少字段 ' + key;
        }
      });
      const extra = keys.filter(function (k) { return requiredKeys.indexOf(k) < 0; });
      if (extra.length > 0) {
        keyProblem = '第 ' + (index + 1) + ' 条存在多余字段 ' + extra.join(',');
      }
      if (!Array.isArray(item.steps) || item.steps.length === 0) {
        keyProblem = '第 ' + (index + 1) + ' 条 steps 为空';
      }
      if ([1, 2, 3].indexOf(item.severity) < 0) {
        keyProblem = '第 ' + (index + 1) + ' 条 severity 取值非法: ' + item.severity;
      }
      idSet.add(item.id);
    });
  } else {
    keyProblem = 'items 不是数组';
  }
  record('每条急救含且仅含 7 个必需字段，steps 非空，severity ∈ {1,2,3}',
    keyProblem === '', keyProblem);
  record('急救条目 id 唯一', idSet.size === 10, 'unique=' + idSet.size);

  const categories = new Set((aidData.items || []).map(function (i) { return i.category; }));
  record('覆盖 意外伤害/急症处置/常见不适/心理急救 四大类',
    ['意外伤害', '急症处置', '常见不适', '心理急救'].every(function (c) {
      return categories.has(c);
    }), Array.from(categories).join(','));

  const hasEscalation = (aidData.items || []).some(function (item) {
    return /120|就医|送医|急诊/.test(item.steps.join('') + item.caution);
  });
  record('急救条目包含 120 / 就医升级建议', hasEscalation);

  /* ---------- 4. GET /api/exercise ---------- */
  console.log('[4] GET /api/exercise');
  const ex = await request('GET', '/api/exercise');
  const exData = checkEnvelope(ex, '运动类型库');
  record('运动类型数量为 12', exData.types && exData.types.length === 12,
    'actual=' + (exData.types ? exData.types.length : 'null'));
  const expectedTypes = ['跑步', '快走', '骑行', '游泳', '篮球', '羽毛球',
    '足球', '瑜伽', '健身训练', '跳绳', '拉伸放松', '其他'];
  const typeNames = (exData.types || []).map(function (t) { return t.name; });
  const missing = expectedTypes.filter(function (n) { return typeNames.indexOf(n) < 0; });
  record('12 种运动类型名称与规格一致', missing.length === 0, 'missing=' + missing.join(','));
  const metOk = (exData.types || []).every(function (t) {
    return typeof t.met === 'number' && t.met > 0 && typeof t.icon === 'string' && t.icon.length > 0;
  });
  record('每种运动包含合法的 met 与 icon', metOk);

  /* ---------- 5. POST /api/mood ---------- */
  console.log('[5] POST /api/mood');
  const mood = await request('POST', '/api/mood', {
    score: 1,
    tags: '焦虑,紧张',
    diary: '最近考试压力很大，晚上总是睡不着'
  });
  const moodData = checkEnvelope(mood, '情绪分析');
  record('risk 为「高」（score=1）', moodData.risk === '高', 'risk=' + moodData.risk);
  record('summary/cause/quote 均为非空字符串',
    typeof moodData.summary === 'string' && moodData.summary.length > 0
    && typeof moodData.cause === 'string' && moodData.cause.length > 0
    && typeof moodData.quote === 'string' && moodData.quote.length > 0);
  record('suggestions 为长度 3 的字符串数组',
    Array.isArray(moodData.suggestions) && moodData.suggestions.length === 3
    && moodData.suggestions.every(function (s) { return typeof s === 'string' && s.length > 0; }),
    JSON.stringify(moodData.suggestions));
  record('命中「焦虑/紧张/压力」关键词 → 给出放松类建议',
    /4-7-8|呼吸|放松|散步|拉伸/.test(moodData.suggestions.join('|')),
    moodData.suggestions.join(' | '));
  record('命中「睡不着」关键词 → 给出睡眠类建议',
    /睡眠|上床|入睡|手机|作息/.test(moodData.suggestions.join('|')),
    moodData.suggestions.join(' | '));

  const mood2 = await request('POST', '/api/mood', {
    score: 1,
    tags: '焦虑,紧张',
    diary: '最近考试压力很大，晚上总是睡不着'
  });
  record('相同输入返回相同结果（确定性，非随机）',
    JSON.stringify(mood2.json.data) === JSON.stringify(mood.json.data));

  const moodMid = await request('POST', '/api/mood', { score: 2, tags: '低落', diary: '有点难过' });
  record('risk 为「中」（score=2）', moodMid.json.data.risk === '中', moodMid.json.data.risk);
  const moodLow = await request('POST', '/api/mood', { score: 4, tags: '开心', diary: '今天很充实' });
  record('risk 为「低」（score=4）', moodLow.json.data.risk === '低', moodLow.json.data.risk);
  record('命中「开心/充实」关键词 → 给出状态维持类建议',
    /保持|记录|节奏|分享/.test(moodLow.json.data.suggestions.join('|')),
    moodLow.json.data.suggestions.join(' | '));

  /* ---------- 6. POST /api/ai/chat（离线兜底） ---------- */
  console.log('[6] POST /api/ai/chat');
  const chat = await request('POST', '/api/ai/chat', {
    prompt: '流鼻血怎么办',
    system: '你是急救助手'
  });
  const chatOk = chat.status === 200 && chat.json && chat.json.code === 0
    && chat.json.message === 'offline-fallback'
    && chat.json.data && typeof chat.json.data.reply === 'string'
    && chat.json.data.reply.length > 0;
  record('未配置 AI_API_KEY 时返回离线兜底 envelope', chatOk, chat.text.slice(0, 200));
  record('离线回复命中急救知识库「流鼻血」并给出步骤',
    /流鼻血/.test(chat.json.data.reply) && /前倾|鼻翼/.test(chat.json.data.reply)
    && /注意事项|就医|120/.test(chat.json.data.reply),
    chat.json.data.reply.slice(0, 120));

  const chat2 = await request('POST', '/api/ai/chat', { prompt: '睡不好怎么办' });
  record('离线回复命中常见问答',
    /睡眠|作息|手机/.test(chat2.json.data.reply), chat2.json.data.reply.slice(0, 120));

  /* ---------- 7. POST /api/report ---------- */
  console.log('[7] POST /api/report');
  const report = await request('POST', '/api/report', {
    sleepAvgMinutes: 420,
    sleepAvgQuality: 4,
    exerciseTotal: 180,
    moodAvg: 4
  });
  const repData = checkEnvelope(report, '健康报告');
  record('score 位于 0~100',
    typeof repData.score === 'number' && repData.score >= 0 && repData.score <= 100,
    'score=' + repData.score);
  record('level 取值合法',
    ['优秀', '良好', '一般', '偏低'].indexOf(repData.level) >= 0, 'level=' + repData.level);
  record('comment 为非空字符串',
    typeof repData.comment === 'string' && repData.comment.length > 0);

  const dims = repData.dimensions;
  const dimsOk = Array.isArray(dims) && dims.length === 3
    && dims[0].name === '睡眠' && dims[0].max === 40
    && dims[1].name === '运动' && dims[1].max === 30
    && dims[2].name === '情绪' && dims[2].max === 30
    && dims.every(function (d) {
      return typeof d.score === 'number' && typeof d.comment === 'string' && d.comment.length > 0;
    });
  record('三个维度 睡眠40/运动30/情绪30 结构正确', dimsOk, JSON.stringify(dims));
  const dimSum = dims.reduce(function (sum, d) { return sum + d.score; }, 0);
  record('dimensions 之和等于 score', dimSum === repData.score,
    'sum=' + dimSum + ' score=' + repData.score);
  record('advice 为长度 3 的字符串数组',
    Array.isArray(repData.advice) && repData.advice.length === 3
    && repData.advice.every(function (a) { return typeof a === 'string' && a.length > 0; }),
    JSON.stringify(repData.advice));

  // 评分算法与 Android HealthScore.calculate() 对齐校验
  const rep2 = await request('POST', '/api/report', {
    sleepAvgMinutes: 300, sleepAvgQuality: 3, exerciseTotal: 75, moodAvg: 2
  });
  const d2 = rep2.json.data;
  const expectSleep = Math.round(Math.max(0, Math.min(40, 20 * (300 / 420) + (3 / 5) * 20)));
  const expectExercise = Math.round(Math.min(30, 75 / 150 * 30));
  const expectMood = Math.round(Math.min(30, 2 / 5 * 30));
  const expectTotal = Math.max(0, Math.min(100, expectSleep + expectExercise + expectMood));
  record('评分算法与 HealthScore.calculate() 完全一致',
    d2.dimensions[0].score === expectSleep
    && d2.dimensions[1].score === expectExercise
    && d2.dimensions[2].score === expectMood
    && d2.score === expectTotal,
    'expect=' + expectSleep + '/' + expectExercise + '/' + expectMood
    + '/' + expectTotal + ' actual=' + d2.dimensions.map(function (x) { return x.score; }).join('/')
    + '/' + d2.score);

  /* ---------- 8. OPTIONS 预检 ---------- */
  console.log('[8] OPTIONS 预检（CORS）');
  const options = await request('OPTIONS', '/api/health');
  record('OPTIONS 返回 204', options.status === 204, 'status=' + options.status);
  record('CORS: Access-Control-Allow-Origin: *',
    options.headers['access-control-allow-origin'] === '*',
    String(options.headers['access-control-allow-origin']));
  record('CORS: Allow-Methods 含 GET/POST/OPTIONS',
    /GET/.test(String(options.headers['access-control-allow-methods']))
    && /POST/.test(String(options.headers['access-control-allow-methods']))
    && /OPTIONS/.test(String(options.headers['access-control-allow-methods'])),
    String(options.headers['access-control-allow-methods']));
  record('CORS: Allow-Headers 含 Content-Type',
    /Content-Type/i.test(String(options.headers['access-control-allow-headers'])),
    String(options.headers['access-control-allow-headers']));
  record('GET 响应也带 Access-Control-Allow-Origin: *',
    health.headers['access-control-allow-origin'] === '*',
    String(health.headers['access-control-allow-origin']));

  /* ---------- 9. 404 静态回退 ---------- */
  console.log('[9] 未知路径 404');
  const notFound = await request('GET', '/not/exist');
  const nfOk = notFound.status === 404 && notFound.json
    && notFound.json.code === 404 && notFound.json.message === 'Not Found'
    && notFound.json.data === null;
  record('未知路径返回 JSON 404 封装', nfOk, notFound.text.slice(0, 160));

  /* ---------- 10. 非法请求体 ---------- */
  console.log('[10] 非法请求体容错');
  const badBody = await new Promise(function (resolve, reject) {
    const data = Buffer.from('{not-json', 'utf8');
    const req = http.request({
      host: '127.0.0.1', port: PORT, path: '/api/mood', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': data.length }
    }, function (res) {
      const chunks = [];
      res.on('data', function (c) { chunks.push(c); });
      res.on('end', function () {
        const text = Buffer.concat(chunks).toString('utf8');
        let json = null;
        try { json = JSON.parse(text); } catch (e) { json = null; }
        resolve({ status: res.statusCode, json: json });
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
  record('非法 JSON 返回统一失败封装而非崩溃',
    badBody.json !== null && badBody.json.code !== 0 && badBody.json.data === null,
    JSON.stringify(badBody.json));

  /* ---------- 汇总 ---------- */
  console.log('');
  console.log('========================================================');
  console.log('  测试完成：PASS ' + passed + ' / FAIL ' + failed);
  if (failures.length > 0) {
    console.log('  失败用例：');
    failures.forEach(function (f) { console.log('    - ' + f); });
  }
  console.log('========================================================');
  console.log('');
}

/**
 * 启动服务器子进程。
 *
 * <p>显式清空 AI_API_KEY / AI_API_BASE / AI_MODEL 并置 AI_FORCE_OFFLINE=true，
 * 保证「未配置 Key 时走离线兜底」这组用例在任何环境下都可确定性地通过
 * ——否则宿主机若设置了相关环境变量，或 server 目录下存在 .env，
 * 子进程会继承并真实调用大模型，导致断言失败。</p>
 */
function startServer() {
  const childEnv = Object.assign({}, process.env, { PORT: String(PORT) });
  delete childEnv.AI_API_KEY;
  delete childEnv.AI_API_BASE;
  delete childEnv.AI_MODEL;
  childEnv.AI_FORCE_OFFLINE = 'true';
  const child = spawn(process.execPath, [path.join(__dirname, 'server.js')], {
    cwd: __dirname,
    env: childEnv,
    stdio: ['ignore', 'pipe', 'pipe']
  });
  child.stdout.on('data', function () { /* 静默服务器日志，保持测试输出干净 */ });
  child.stderr.on('data', function (chunk) {
    const text = chunk.toString();
    if (/EADDRINUSE/.test(text)) {
      console.error('\n[错误] 端口 ' + PORT + ' 已被占用，请先关闭占用进程或设置 PORT 环境变量。\n');
    } else {
      process.stderr.write(text);
    }
  });
  return child;
}

let child = null;

child = startServer();
waitForServer()
  .then(run)
  .catch(function (err) {
    failed++;
    failures.push('测试执行异常: ' + err.message);
    console.error('\n[错误] ' + err.message + '\n');
  })
  .then(function () {
    if (child) {
      child.kill();
    }
    const ok = failed === 0;
    console.log(ok ? '  全部用例通过 ✅' : '  存在失败用例 ❌');
    console.log('');
    process.exit(ok ? 0 : 1);
  });
