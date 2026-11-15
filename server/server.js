'use strict';

/**
 * 「心晴校园」HeartSync 后端服务
 * ---------------------------------------------------------------------------
 * 课程大作业自建服务器：零第三方依赖，只使用 Node.js 内置模块
 * （http / fs / path / url / querystring），学生直接 `node server.js` 即可运行。
 *
 * 技术点对应：网络开发（HTTP 服务、CORS、RESTful 风格接口）、JSON 数据处理、
 * 文件读取（data/tips.json）、大模型 API 代理（OpenAI 兼容协议）。
 *
 * 统一响应封装：
 *   成功：{"code":0,"message":"success","data":{...}}
 *   失败：{"code":500,"message":"错误描述","data":null}
 * 唯一例外是 /api/health，它返回扁平的健康检查结构，便于探活脚本解析。
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

/* ==========================================================================
 * 一、运行配置
 * ========================================================================== */

/**
 * 读取本地 .env 配置文件（可选）。
 * 优先级：真实环境变量 > .env 文件。这样既方便双击 start.bat 一键启动，
 * 又允许用命令行临时覆盖。项目根目录下的 .env 已加入 .gitignore，
 * 避免把 API Key 误提交到代码托管平台。
 */
function loadEnvFile() {
    const envPath = path.join(__dirname, '.env');
    if (!fs.existsSync(envPath)) {
        return;
    }
    const content = fs.readFileSync(envPath, 'utf8');
    content.split(/\r?\n/).forEach((line) => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) {
            return;
        }
        const idx = trimmed.indexOf('=');
        if (idx <= 0) {
            return;
        }
        const name = trimmed.slice(0, idx).trim();
        let value = trimmed.slice(idx + 1).trim();
        // 去掉可能包裹的引号
        if ((value.startsWith('"') && value.endsWith('"'))
            || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.slice(1, -1);
        }
        if (name && !process.env[name]) {
            process.env[name] = value;
        }
    });
}

loadEnvFile();

const HOST = '0.0.0.0';                 // 监听所有网卡，模拟器通过 10.0.2.2 访问
const PORT = Number(process.env.PORT) || 8080;
const SERVER_NAME = 'HeartSync Server';
const SERVER_VERSION = '1.0';

// 大模型配置：全部来自环境变量，未配置 AI_API_KEY 时自动进入离线兜底模式。
// 设置 AI_FORCE_OFFLINE=true 可强制进入离线兜底（忽略 .env 与环境变量中的 Key），
// 便于在没有 Key 的机器上演示完整功能，或在自动化测试中确定性地验证兜底链路。
const AI_FORCE_OFFLINE = String(process.env.AI_FORCE_OFFLINE || '').trim().toLowerCase() === 'true';
const AI_API_KEY = AI_FORCE_OFFLINE ? '' : (process.env.AI_API_KEY || '').trim();
const AI_API_BASE = (process.env.AI_API_BASE
    || 'https://dashscope.aliyuncs.com/compatible-mode/v1').replace(/\/+$/, '');
const AI_MODEL = (process.env.AI_MODEL || 'qwen-plus').trim();
const AI_TIMEOUT_MS = 60000;           // 上游大模型调用超时时间（60 秒）

const START_TIME = Date.now();
const MAX_BODY_BYTES = 1024 * 1024;     // 请求体大小上限 1MB，防止内存被撑爆

/* ==========================================================================
 * 二、内置数据
 * ========================================================================== */

/**
 * 健康小贴士兜底数据。
 * 当 data/tips.json 缺失或解析失败时使用，保证接口始终可用。
 * 顺序与 data/tips.json 保持一致。
 */
const FALLBACK_TIPS = [
  ['每天固定一个起床时间（包括周末），比固定入睡时间更能稳定生物钟。', '睡眠'],
  ['睡前 1 小时把手机放到床外充电，蓝光和刷消息都会推迟入睡。', '睡眠'],
  ['连续熬了 3 天以上，先补一觉再谈效率：睡眠不足时记忆力最多可下降约 30%。', '睡眠'],
  ['咖啡因半衰期约 5 小时，下午 2 点后就别再喝咖啡和浓茶了。', '睡眠'],
  ['午睡控制在 20~30 分钟，超过 40 分钟反而更困，还影响夜间入睡。', '睡眠'],
  ['躺下 20 分钟睡不着就起来做点安静的事，躺着焦虑更容易失眠。', '睡眠'],
  ['睡前别刷短视频，改成泡脚或听轻音乐，入睡速度会明显变快。', '睡眠'],
  ['宿舍噪音多就准备两副耳塞，安静比任何助眠方法都管用。', '睡眠'],
  ['睡前一小时关掉大灯、留一盏暖色小夜灯，身体更容易分泌褪黑素。', '睡眠'],
  ['课间 10 分钟站起来做 12 个深蹲，久坐一节课身体就开始僵。', '运动'],
  ['每周累计 150 分钟中等强度运动（快走、骑行都算）即可达到 WHO 推荐量。', '运动'],
  ['跑步机上跑步不如操场快走 30 分钟，边走边聊天的强度刚刚好。', '运动'],
  ['运动后 5 分钟拉伸，能明显减少第二天的肌肉酸痛。', '运动'],
  ['没有器械也能练：徒手深蹲、俯卧撑、平板支撑、原地开合跳循环 3 组。', '运动'],
  ['从每天 10 分钟快走开始，一周后加到 15 分钟，比一次猛练 1 小时更容易坚持。', '运动'],
  ['运动前先热身 5 分钟，尤其是天冷时直接开跑很容易拉伤。', '运动'],
  ['爬楼梯代替电梯，每天上下 3 层，一周就能明显感觉到腿力变化。', '运动'],
  ['和室友约着打球或散步，运动搭伴的坚持率比一个人高 3 倍。', '运动'],
  ['运动后及时补水，出汗多时补充一点淡盐水，疲劳恢复更快。', '运动'],
  ['情绪来了先做 4-7-8 呼吸 4 轮，生理上先把交感神经降下来。', '情绪'],
  ['把「我很难受」写成三件具体的小事，情绪就有了出口，也更容易被解决。', '情绪'],
  ['每天写 3 件当天的小确幸，情绪记录对低落有实际改善作用。', '情绪'],
  ['感到撑不住时，试着把求助说出口——找同学、辅导员或心理中心，都算有效求助。', '情绪'],
  ['连续一周情绪低落、兴趣减退、影响睡眠，尽早预约学校心理咨询，越早越有效。', '情绪'],
  ['在人群中紧张时做「5-4-3-2-1 落地练习」：说出看到的 5 样东西、听到的 4 种声音。', '情绪'],
  ['每天留 10 分钟什么都不做，比刷手机更能修复情绪。', '情绪'],
  ['给别人一个小小的帮助（递杯水、夸一句），情绪提升往往比自我说服更有效。', '情绪'],
  ['期末季最容易崩溃，提前把任务拆成小份，每完成一项就打一个勾。', '情绪'],
  ['流鼻血时身体前倾、捏住鼻翼 10~15 分钟，仰头会让血倒流进喉咙。', '急救'],
  ['烫伤先用流动冷水冲 15~20 分钟，抹牙膏、酱油都是错误操作。', '急救'],
  ['扭伤 24 小时内冷敷、48 小时后热敷，顺序反了会加重肿胀。', '急救'],
  ['身边有人晕倒，先判断呼吸和呼叫反应，不要急着喂水或喂药。', '急救'],
  ['夏天运动出现头晕恶心、皮肤发烫，是中暑前兆，立即到阴凉处降温。', '急救'],
  ['胸闷心悸伴出汗、超过 5 分钟不缓解，立即拨打 120，不要自己开车。', '急救'],
  ['异物卡喉无法发声时用海姆立克法，从背后冲击上腹部；孕妇改胸部冲击。', '急救'],
  ['腹泻时少量多次补水和口服补液盐，比只喝白水更有效。', '急救'],
  ['记住 120 和 24 小时心理援助热线，遇到真正紧急情况不要犹豫。', '急救']
];

/**
 * 急救知识库（10 条），内容与 Android 端 assets/first_aid.json 保持一致，
 * 保证联网与离线两种模式下的急救条目完全相同。
 */
const FIRST_AID_ITEMS = [
  {
    id: 'aid_001',
    category: '意外伤害',
    title: '扭伤（踝关节/手腕）',
    symptom: '受伤部位疼痛、肿胀、淤青，无法正常活动或承重。',
    steps: [
      '立即停止运动，就地坐下或躺下，不要勉强继续活动',
      '用冰袋冷敷受伤部位 15~20 分钟，隔 1 小时可重复一次',
      '用弹性绷带适度加压包扎，松紧以能插入一根手指为宜',
      '将患肢抬高至高于心脏位置，促进消肿',
      '24 小时内冷敷为主，48 小时后改为热敷并配合轻柔按摩',
      '疼痛剧烈、明显畸形或无法负重时，立即前往医院骨科就诊'
    ],
    caution: '不要在 24 小时内热敷或使用活血类药物；不要自行强行复位关节；若伤及颈椎或腰部，切勿随意搬动伤者。',
    severity: 2
  },
  {
    id: 'aid_002',
    category: '意外伤害',
    title: '流鼻血',
    symptom: '一侧或双侧鼻腔出血，出血量可从少量渗血到大量涌出。',
    steps: [
      '保持坐姿，身体和头部略微前倾，绝对不要仰头',
      '用拇指和食指捏紧鼻翼柔软处，持续按压 10~15 分钟不松手',
      '用另一只手持冷毛巾敷在鼻根部，帮助血管收缩',
      '按压期间用嘴呼吸，尽量不要吞咽流下的血液',
      '15 分钟后松开，若仍未止血，再重复按压 10 分钟',
      '出血量超过 10 分钟仍未停止，或反复大量出血，立即就医'
    ],
    caution: '严禁仰头，防止血液流入咽喉引起呛咳或误吸；不要往鼻腔内塞纸巾、棉花等异物；高血压患者应格外注意血压情况。',
    severity: 1
  },
  {
    id: 'aid_003',
    category: '意外伤害',
    title: '轻度烫伤（开水/热油）',
    symptom: '皮肤发红、发热、疼痛，可能出现小水泡。',
    steps: [
      '第一时间远离热源，用流动的冷水冲洗烫伤部位 15~20 分钟',
      '冲洗时不要用力搓揉伤处，也不要撕扯粘连的衣物',
      '在水中小心去除未粘连的衣物，粘连部分保留原样',
      '用干净的纱布或棉质毛巾轻轻覆盖伤口',
      '不要自行涂抹牙膏、酱油、面粉等物品',
      '若出现大水泡、皮肤发白发黑或烫伤面积较大，立即送医'
    ],
    caution: '严禁使用冰块直接敷于伤处，可能造成冻伤加重组织损伤；不要挑破水泡；面部、关节部位的烫伤建议直接就医。',
    severity: 2
  },
  {
    id: 'aid_004',
    category: '急症处置',
    title: '中暑（热射病先兆）',
    symptom: '头晕、乏力、恶心、大量出汗、皮肤灼热，体温升高，严重时意识模糊。',
    steps: [
      '迅速将患者转移到阴凉通风处，解开衣物',
      '用湿毛巾擦拭颈部、腋窝、腹股沟等大血管处',
      '准备凉水轻拍全身，或用冰袋冷敷头部、颈部、腋窝',
      '清醒患者可少量多次饮用含盐分的凉水或运动饮料',
      '若患者意识清醒，让其平躺并抬高下肢',
      '出现高热、意识丧失、抽搐等重症表现，立即拨打 120'
    ],
    caution: '意识不清者不可强行喂水，以免呛咳误吸；不要使用酒精擦浴降温；重症中暑属急症，切勿拖延就医。',
    severity: 3
  },
  {
    id: 'aid_005',
    category: '急症处置',
    title: '晕厥（低血糖或体位性低血压）',
    symptom: '突然眼前发黑、站立不稳、意识短暂丧失、面色苍白、出冷汗。',
    steps: [
      '迅速将患者平放在地面，避免跌倒造成二次伤害',
      '抬高下肢约 30 厘米，促进血液回流至脑部',
      '松解领口与腰带，保持呼吸道通畅',
      '若为低血糖（糖尿病患者、长时间未进食），立即口服糖水或含糖食物',
      '轻拍呼叫患者，观察呼吸与意识恢复情况',
      '5 分钟内未恢复意识，立即拨打 120 并持续监测生命体征'
    ],
    caution: '昏迷者不可喂食喂水；注意观察是否发生抽搐、呕吐，防止误吸；如反复发作需尽快就医查明病因。',
    severity: 3
  },
  {
    id: 'aid_006',
    category: '急症处置',
    title: '心悸 / 胸闷不适',
    symptom: '突发心跳加快、心慌、胸闷、气短，伴出汗、恶心或左上肢不适。',
    steps: [
      '立即停止一切活动，就地坐下或平躺，保持安静',
      '解开衣领，保持室内通风',
      '测量心率与血压并记录下来',
      '有心脏病史者按医嘱服用随身携带的急救药物（硝酸甘油等）',
      '症状持续超过 5 分钟不缓解，或伴剧烈胸痛，立即拨打 120',
      '等待救护车时保持平卧，避免任何活动'
    ],
    caution: '不要自行驾车去医院；不要用力憋气或做「Valsalva 动作」；硝酸甘油类药物需按医嘱使用，切勿超量。',
    severity: 3
  },
  {
    id: 'aid_007',
    category: '常见不适',
    title: '急性胃肠炎 / 拉肚子',
    symptom: '腹痛、腹泻、恶心呕吐，可伴发热。',
    steps: [
      '暂停进食约 4~6 小时，让肠胃得到休息，但可少量多次饮用温水',
      '症状缓解后从流质饮食开始，如米汤、稀粥，逐步过渡到软食',
      '注意补充电解质，可服用口服补液盐',
      '记录排便次数，观察是否有血便、持续高热',
      '症状超过 2 天不缓解或加重，及时到校医院或医院就诊',
      '同期饮食宜清淡，避免生冷、油腻、辛辣及乳制品'
    ],
    caution: '出现脱水表现（口干、尿少、皮肤弹性差、精神萎靡）须立即就医；剧烈腹痛或腹部板状压痛警惕急腹症，不要自行服止痛药掩盖症状。',
    severity: 2
  },
  {
    id: 'aid_008',
    category: '常见不适',
    title: '熬夜后头痛 / 颈肩痛',
    symptom: '头部胀痛、后颈僵硬酸痛、注意力不集中、眼睛干涩。',
    steps: [
      '停止学习，闭目休息 15~20 分钟',
      '用温热毛巾热敷颈后与肩部 15 分钟，促进血液循环',
      '轻轻做颈部与肩部的舒展动作，不要猛烈转动头部',
      '按揉太阳穴、风池穴各 1~2 分钟，力度以酸胀可忍受为度',
      '适量补充温水，避免咖啡因与浓茶',
      '连续头痛超过 2 天或伴随视物模糊、肢体麻木，立即就医'
    ],
    caution: '高血压患者突发剧烈头痛需警惕脑血管意外，不要自行服用止痛药掩盖症状，应尽快就医；热敷温度控制在 45℃ 左右防止烫伤。',
    severity: 1
  },
  {
    id: 'aid_009',
    category: '意外伤害',
    title: '异物卡喉 / 呛咳窒息',
    symptom: '突然剧烈呛咳、无法发声、双手抓喉、面色发紫。',
    steps: [
      '询问「你能说话吗」，能说话说明气道尚通畅，先鼓励用力咳嗽',
      '若不能说话、无法咳嗽，立即实施海姆立克急救法',
      '站于患者身后，一脚在前稳住重心，一手握拳置于脐上两指处',
      '另一手包住拳头，快速向内、向上冲击腹部，直到异物排出',
      '孕妇与极度肥胖者改为胸部冲击法，位置在胸骨中下段',
      '失去意识时先平放硬质地面开始心肺复苏，并立即拨打 120'
    ],
    caution: '海姆立克法可能造成内脏损伤，已自行排出异物者仍建议就医检查；切勿用手盲目掏挖异物；婴儿（1 岁以下）不可使用腹部冲击法，需采用背部拍击法。',
    severity: 3
  },
  {
    id: 'aid_010',
    category: '心理急救',
    title: '情绪崩溃 / 焦虑发作',
    symptom: '呼吸急促、心跳过快、手抖、强烈恐惧感或无助感，持续不缓解。',
    steps: [
      '陪在身边，用平静的语气回应：「我在这里，慢慢来」',
      '引导做「4-7-8 呼吸」：吸气 4 秒、屏息 7 秒、呼气 8 秒，重复 4 轮',
      '提供温水和安静独处的空间，避免围观与言语刺激',
      '引导把注意力转移到具体感官上，如说出眼前看到的 5 样东西',
      '情况缓解后陪同联系校心理中心或辅导员，做后续跟进',
      '出现自伤念头或绝望感时，立即联系辅导员并拨打 24 小时心理援助热线'
    ],
    caution: '不要急于讲道理或否定对方感受（「这有什么大不了的」），避免说教与强行安慰；不要承诺你无法做到的事情；涉及人身安全时优先联系专业机构。',
    severity: 3
  }
];

/** 运动类型库：MET 值为「代谢当量」，数值越大代表强度越高 */
const EXERCISE_TYPES = [
  { name: '跑步', met: 8.3, icon: '🏃' },
  { name: '快走', met: 4.3, icon: '🚶' },
  { name: '骑行', met: 7.5, icon: '🚴' },
  { name: '游泳', met: 8.3, icon: '🏊' },
  { name: '篮球', met: 6.5, icon: '🏀' },
  { name: '羽毛球', met: 5.5, icon: '🏸' },
  { name: '足球', met: 7.0, icon: '⚽' },
  { name: '瑜伽', met: 2.5, icon: '🧘' },
  { name: '健身训练', met: 5.0, icon: '🏋️' },
  { name: '跳绳', met: 11.0, icon: '🪢' },
  { name: '拉伸放松', met: 2.3, icon: '🤸' },
  { name: '其他', met: 3.5, icon: '✨' }
];

/**
 * 情绪分析规则表。
 * 命中关键词 → 给出成因分析与一条可执行的建议，规则按顺序匹配。
 */
const MOOD_RULES = [
  {
    id: 'anxiety',
    label: '焦虑紧张',
    keywords: ['焦虑', '紧张', '压力', '担心', '害怕', '不安', '心慌', '烦躁', '压力大', '怯场', 'panic'],
    cause: '近期任务或人际压力较高，身心长期处于警戒状态，休息不足又进一步放大了焦虑感。',
    suggestion: '试试 4-7-8 呼吸法（吸气 4 秒、屏息 7 秒、呼气 8 秒）重复 4 轮，再配合 5 分钟散步或拉伸。'
  },
  {
    id: 'sleep',
    label: '睡眠不足',
    keywords: ['疲惫', '疲乏', '困', '睡不好', '睡不着', '失眠', '熬夜', '没睡', '不睡', '晚睡', '作息', '疲劳', '睡不够', '起不来', '多梦'],
    cause: '近期睡眠时间不足或作息不规律，身体尚未完成恢复就被迫进入高负荷状态。',
    suggestion: '今晚提前 30 分钟上床，睡前 1 小时远离手机和强光；固定起床时间比固定入睡时间更重要。'
  },
  {
    id: 'low',
    label: '情绪低落',
    keywords: ['低落', '孤独', '难过', '失落', 'emo', '委屈', '想哭', '没意思', '抑郁', '丧', '不开心', '难受'],
    cause: '情绪低落多与近期压力事件、缺少支持性表达或自我评价偏低有关，属于常见但需要被看见的情绪。',
    suggestion: '找一位信任的同学或老师聊一聊，把难受写进日记；情绪持续两周以上建议预约校心理咨询。'
  },
  {
    id: 'positive',
    label: '积极向上',
    keywords: ['开心', '充实', '有动力', '兴奋', '满足', '顺利', '搞定', '放松', 'happy', '满足感', '有成就感'],
    cause: '近期节奏掌控感较好，目标推进顺利，稳定正反馈正在积累。',
    suggestion: '把今天让你有成就感的三件事记录下来，保持当前的作息与运动节奏。'
  },
  {
    id: 'social',
    label: '人际困扰',
    keywords: ['吵架', '误会', '被拒绝', '尴尬', '沟通', '矛盾', '不合', '孤单', '朋友', '室友', '社交'],
    cause: '人际关系中的摩擦会持续占用心理能量，情绪往往是关系压力的直接投射。',
    suggestion: '先分开「事实」与「我的解读」，再选一个对方情绪平稳的时段，用「我感到…因为…」表达一次。'
  },
  {
    id: 'physical',
    label: '身体不适',
    keywords: ['头疼', '头痛', '胃疼', '肚子疼', '胃痛', '身体不舒服', '不舒服', '生病', '感冒', '发烧', '胃不舒服'],
    cause: '身体不适会直接消耗精力并影响情绪，大脑对疼痛的感知也会放大负面想法。',
    suggestion: '先处理身体：休息、补水、必要时到校医院就诊；症状缓解后再回头看情绪。'
  }
];

/** 兜底建议：规则未命中时按分数段补足，保证 suggestions 始终有 3 条 */
const GENERIC_SUGGESTIONS = [
  '每天给自己留 20 分钟完全不被打断的时间，散步、发呆或洗个热水澡都可以。',
  '规律饮食，三餐定时、多喝水，稳定的生理节律是情绪稳定的底座。',
  '把今天的困扰写下来并标出「能控制」和「不能控制」的部分，只处理前者。',
  '每周安排一次和朋友的线下见面，良好的人际连接是重要的抗压资源。'
];

/** 分数段 → 心情基调描述 */
const MOOD_BANDS = {
  5: { title: '状态很好', tone: '积极饱满', cause: '各项节律与心态都在良好区间，保持当前节奏即可。' },
  4: { title: '状态不错', tone: '平稳向好', cause: '整体状态稳定，只是仍有少量精力消耗需要留意。' },
  3: { title: '状态平稳', tone: '平常', cause: '情绪没有明显波动，处于日常基线水平。' },
  2: { title: '状态偏低', tone: '有些低落', cause: '身心能量偏低，可能存在未被处理的压力源。' },
  1: { title: '状态很差', tone: '明显低落', cause: '身心负荷已经超载，需要优先获得休息与外部支持。' }
};

/** 分数段 → 鼓励语句池 */
const QUOTE_POOL = {
  5: ['状态正佳，把今天的好心情记下来，以后低谷时翻出来看。', '你最近做得很好，这种节奏值得继续保持。'],
  4: ['挺好的状态，允许自己放松一会儿。', '小步向前也值得被肯定，你已经在路上了。'],
  3: ['平稳的日子同样珍贵，安心过好当下这一天。', '不用每天都有所突破，稳定也是一种能力。'],
  2: ['低落的情绪会过去，能觉察到它就已经迈出第一步。', '今天辛苦了，允许自己只做到 60 分。'],
  1: ['现在的难受是真实的，但它不是终点，先照顾好身体。', '能坚持到现在已经很不容易，向任何信任的人求助都不丢人。']
};

/**
 * 常见问答：离线兜底回复的第二知识来源。
 * 命中关键词后返回一段可直接阅读的答案。
 */
const FAQ_ITEMS = [
  {
    id: 'faq_sleep',
    keywords: ['睡不好', '失眠', '睡不着', '熬夜', '作息', '睡多久', '睡眠时间'],
    answer: '关于睡眠，三件事最有效：① 固定起床时间，周末也不要相差超过 1 小时；② 睡前 1 小时不看手机、不喝咖啡因；③ 躺下 20 分钟睡不着就离开床做安静的事。连续两周入睡困难或白天严重嗜睡，建议到校医院睡眠门诊就诊，不要自行长期服用安眠药。'
  },
  {
    id: 'faq_exercise',
    keywords: ['运动', '锻炼', '健身', '减肥', '跑步', '怎么练', '运动量', '增肌'],
    answer: '关于运动，建议按「少量多次」来做：每周累计 150 分钟中等强度（快走、骑行都算），拆成每天 20~30 分钟比周末突击一次更有效。零器械方案：徒手深蹲 15 个 + 平板支撑 30 秒 + 开合跳 20 个，循环 3 组；运动前热身 5 分钟、运动后拉伸 5 分钟，运动量循序渐进增加。'
  },
  {
    id: 'faq_mood',
    keywords: ['情绪', '焦虑', '压力', '崩溃', 'emo', '难过', '低落', '心情不好', '紧张'],
    answer: '关于情绪，可以按三步走：① 觉察并命名（「我现在很焦虑」比「我快不行了」更能降温）；② 身体先行（4-7-8 呼吸 4 轮，或快走 10 分钟）；③ 寻求支持（找同学、辅导员或校心理中心）。若低落情绪持续两周以上，并伴随兴趣减退、睡眠食欲改变，请尽早预约专业心理咨询，这是可以求助的健康问题。'
  },
  {
    id: 'faq_study',
    keywords: ['考试', '复习', '论文', '作业', '拖延', '学习', '效率', '期末', '备考'],
    answer: '关于学习效率：把大任务拆成 25 分钟一个番茄钟，先做最难的那一项（精力最好的时段留给最费脑的活）；复习用「主动回忆」而非重复阅读——合上书自问自答，回忆失败的知识点才是真正要背的部分；每学 50 分钟离开座位活动 5 分钟，效率反而更高。'
  },
  {
    id: 'faq_diet',
    keywords: ['吃', '饮食', '食堂', '外卖', '外卖', '胃', '营养', '外卖', '零食', '减肥饮食'],
    answer: '关于饮食：优先保证三件事——每餐有蛋白质（鸡蛋、牛奶、豆制品、鱼肉）、每天一拳头蔬菜、每天喝够 1500~1700 毫升水。食堂点餐优先选清炒或炖煮，少选糖醋、红烧、油炸；外卖注意少油少盐，加一份绿叶菜。吃辣胃疼时可少量多次温水加口服补液盐，症状超过两天未缓解请到校医院就诊。'
  },
  {
    id: 'faq_eyes',
    keywords: ['眼睛', '护眼', '视力', '近视', '眼干', '屏幕', '用眼'],
    answer: '关于用眼：执行「20-20-20」原则——每用眼 20 分钟，看 6 米外的物体 20 秒；把手机字号调大、系统开启深色模式、亮度与屏幕亮度匹配；每天做一次眼保健操或远眺 5 分钟。已经出现视物模糊、飞蚊或眼痛时，请到校医院眼科检查，不要只靠休息硬扛。'
  },
  {
    id: 'faq_water',
    keywords: ['喝水', '喝水少', '脱水', '口干', '水喝'],
    answer: '关于喝水：把 500 毫升的矿泉水作为单位，每天大约 3 瓶；上课久坐时每节课间喝 200 毫升。口渴才喝往往已经轻度脱水，缺水会直接带来疲劳、注意力下降和假性头痛。运动后按出汗量额外补充，夏季可加一点淡盐水。'
  },
  {
    id: 'faq_help',
    keywords: ['心理中心', '心理咨询', '热线', '120', '医院', '就医', '挂号', '求助'],
    answer: '关于求助：校内资源——心理健康教育中心（通常在学生工作处/校医院同一楼层）、各学院辅导员、校医院门诊；校外资源——急救 120、心理援助热线 12356（24 小时）。紧急情况先打 120 再通知辅导员和同学。如果出现自伤念头，请立即联系身边人陪伴并前往急诊，这是医疗问题而非软弱。'
  }
];

/* ==========================================================================
 * 三、通用工具函数
 * ========================================================================== */

/** 生成统一成功响应 */
function success(data, message) {
  return { code: 0, message: message || 'success', data: data === undefined ? null : data };
}

/** 生成统一失败响应 */
function failure(code, message) {
  return { code: code, message: message, data: null };
}

/** 写入 JSON 响应（UTF-8） */
function sendJson(res, statusCode, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Max-Age': '86400'
  });
  res.end(body);
}

/** 读取请求体（JSON），带大小限制 */
function readJsonBody(req) {
  return new Promise(function (resolve, reject) {
    let size = 0;
    const chunks = [];
    req.on('data', function (chunk) {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error('请求体过大'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', function () {
      const raw = Buffer.concat(chunks).toString('utf8').trim();
      if (!raw) {
        resolve({});
        return;
      }
      try {
        const parsed = JSON.parse(raw);
        resolve(parsed && typeof parsed === 'object' ? parsed : {});
      } catch (e) {
        reject(new Error('请求体不是合法的 JSON'));
      }
    });
    req.on('error', reject);
  });
}

/** 将数值转换为区间内的数字 */
function clamp(value, min, max) {
  if (!isFinite(value)) {
    return min;
  }
  return Math.min(max, Math.max(min, value));
}

/** 数字解析：非法输入回退到默认值 */
function toNumber(value, fallback) {
  const n = Number(value);
  return isFinite(n) ? n : fallback;
}

/** 稳定的字符串哈希（FNV-1a），用于确定性选择 */
function hashString(text) {
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = (hash + ((hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24))) >>> 0;
  }
  return hash >>> 0;
}

/** 格式化 yyyy-MM-dd（本地时区） */
function formatDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return y + '-' + m + '-' + d;
}

/** 计算一年中的第几天（本地时区，1 月 1 日为第 1 天） */
function dayOfYear(date) {
  const start = new Date(date.getFullYear(), 0, 1);
  const diff = Math.floor((date.getTime() - start.getTime()) / 86400000);
  return diff + 1;
}

/** 简易日志 */
function log(message) {
  const now = new Date();
  const time = formatDate(now) + ' ' + String(now.getHours()).padStart(2, '0')
    + ':' + String(now.getMinutes()).padStart(2, '0')
    + ':' + String(now.getSeconds()).padStart(2, '0');
  console.log('[' + time + '] ' + message);
}

/* ==========================================================================
 * 四、数据加载（健康小贴士）
 * ========================================================================== */

/**
 * 启动时读取 data/tips.json；文件缺失或格式错误时回退到内置数组。
 */
function loadTips() {
  const filePath = path.join(__dirname, 'data', 'tips.json');
  try {
    if (!fs.existsSync(filePath)) {
      log('未找到 data/tips.json，使用内置兜底贴士数据');
      return fallbackTips();
    }
    const text = fs.readFileSync(filePath, 'utf8');
    const parsed = JSON.parse(text);
    const list = Array.isArray(parsed) ? parsed : parsed && Array.isArray(parsed.items) ? parsed.items : null;
    if (!list || list.length === 0) {
      log('data/tips.json 内容为空，使用内置兜底贴士数据');
      return fallbackTips();
    }
    const tips = list
      .filter(function (item) { return item && typeof item.text === 'string' && item.text.trim(); })
      .map(function (item) {
        return {
          text: String(item.text).trim(),
          category: ['睡眠', '运动', '情绪', '急救'].indexOf(item.category) >= 0 ? item.category : '睡眠'
        };
      });
    if (tips.length === 0) {
      return fallbackTips();
    }
    log('已加载 data/tips.json，共 ' + tips.length + ' 条健康贴士');
    return tips;
  } catch (err) {
    log('读取 data/tips.json 失败（' + err.message + '），使用内置兜底贴士数据');
    return fallbackTips();
  }
}

/** 内置兜底贴士 */
function fallbackTips() {
  return FALLBACK_TIPS.map(function (row) {
    return { text: row[0], category: row[1] };
  });
}

const TIPS = loadTips();

/* ==========================================================================
 * 五、接口实现
 * ========================================================================== */

/** GET /api/health —— 健康检查 */
function handleHealth(res) {
  sendJson(res, 200, {
    status: 'ok',
    service: SERVER_NAME,
    version: SERVER_VERSION,
    time: new Date().toISOString(),
    uptime: Math.floor((Date.now() - START_TIME) / 1000)
  });
}

/** GET /api/tip —— 每日健康贴士（按一年中的第几天确定性轮换） */
function handleTip(res) {
  const now = new Date();
  const index = (dayOfYear(now) - 1) % TIPS.length;
  const tip = TIPS[index];
  sendJson(res, 200, success({
    tip: tip.text,
    category: tip.category,
    date: formatDate(now)
  }));
}

/** GET /api/firstaid —— 急救知识库 */
function handleFirstAid(res) {
  sendJson(res, 200, success({
    total: FIRST_AID_ITEMS.length,
    items: FIRST_AID_ITEMS
  }));
}

/** GET /api/exercise —— 运动类型库 */
function handleExercise(res) {
  sendJson(res, 200, success({
    types: EXERCISE_TYPES
  }));
}

/**
 * POST /api/mood —— 情绪分析（服务端规则引擎，输出确定）
 *
 * @param {object} body {score, tags, diary}
 */
function handleMood(res, body) {
  const rawScore = toNumber(body.score, 3);
  const score = clamp(Math.round(rawScore), 1, 5);
  const tags = typeof body.tags === 'string' ? body.tags : '';
  const diary = typeof body.diary === 'string' ? body.diary : '';
  const combined = (tags + ' ' + diary);

  // 1) 风险等级：由分数唯一决定
  let risk;
  if (score <= 1) {
    risk = '高';
  } else if (score === 2) {
    risk = '中';
  } else {
    risk = '低';
  }

  // 2) 规则命中（按 MOOD_RULES 顺序，结果确定）
  const matched = MOOD_RULES.filter(function (rule) {
    return rule.keywords.some(function (kw) { return combined.indexOf(kw) >= 0; });
  });

  // 3) 总结：分数基调 + 命中的关键词描述
  const band = MOOD_BANDS[score];
  let summary = '本次记录心情 ' + score + ' 分，整体' + band.title + '、情绪' + band.tone + '。';
  if (matched.length > 0) {
    summary += '从你的描述里读到「' + matched.map(function (r) { return r.label; }).join('、')
      + '」等信号。';
  } else {
    summary += '你的描述里没有明显的情绪关键词，更像是日常平稳状态。';
  }

  // 4) 成因：优先使用首个命中规则的成因
  const cause = matched.length > 0 ? matched[0].cause : band.cause;

  // 5) 建议：按规则顺序收集，补齐到 3 条
  const suggestions = [];
  function pushSuggestion(text) {
    if (text && suggestions.indexOf(text) < 0 && suggestions.length < 3) {
      suggestions.push(text);
    }
  }
  matched.forEach(function (rule) { pushSuggestion(rule.suggestion); });
  // 风险较高时，优先补上专业求助类建议
  if (risk === '高') {
    pushSuggestion('建议尽快联系学校心理健康教育中心或辅导员做一次面谈；如果出现自伤念头，请立即拨打 12356 心理援助热线或 120 急救电话。');
  } else if (risk === '中') {
    pushSuggestion('如果这种状态持续两周以上并影响到上课与饮食，建议预约学校心理咨询做一次评估。');
  }
  for (let i = 0; i < GENERIC_SUGGESTIONS.length && suggestions.length < 3; i++) {
    pushSuggestion(GENERIC_SUGGESTIONS[i]);
  }

  // 6) 鼓励语：按分数段选池，再按内容哈希确定具体一条（同样输入必得同样结果）
  const pool = QUOTE_POOL[score] || QUOTE_POOL[3];
  const quote = pool[hashString(String(score) + '|' + tags + '|' + diary) % pool.length];

  sendJson(res, 200, success({
    summary: summary,
    cause: cause,
    suggestions: suggestions,
    risk: risk,
    quote: quote
  }));
}

/**
 * 离线兜底回复：基于急救知识库 + 常见问答的本地检索。
 * 用于没有配置 AI_API_KEY 的场景，保证 App 依然可以完整演示。
 */
function buildOfflineReply(prompt) {
  const text = String(prompt || '').trim();
  if (!text) {
    return '你好，我是「心晴」的本地知识库助手。你可以问我：流鼻血/扭伤/烫伤/中暑等急救问题、'
      + '睡眠不好怎么调、运动量够不够、情绪低落怎么办、期末复习怎么安排。';
  }

  // 1) 先查急救知识库
  const aidHit = matchFirstAid(text);
  if (aidHit) {
    const item = aidHit.item;
    const lines = [];
    lines.push('【急救知识库】' + item.title + '（' + item.category + '）');
    lines.push('常见症状：' + item.symptom);
    lines.push('处理步骤：');
    item.steps.forEach(function (step, index) {
      lines.push((index + 1) + '. ' + step);
    });
    lines.push('注意事项：' + item.caution);
    lines.push(item.severity === 3
      ? '该情况属于危急级别，若症状加重或出现意识异常，请立即拨打 120 或前往医院急诊。'
      : '如症状持续不缓解或明显加重，请及时到校医院或医院就诊。');
    lines.push('（以上内容来自本地急救知识库，仅供科普参考，不能替代医生诊断。）');
    return lines.join('\n');
  }

  // 2) 再查常见问答
  for (let i = 0; i < FAQ_ITEMS.length; i++) {
    const faq = FAQ_ITEMS[i];
    if (faq.keywords.some(function (kw) { return text.indexOf(kw) >= 0; })) {
      return faq.answer + '\n（当前未配置 AI_API_KEY，以上为本地知识库兜底回复；'
        + '配置后可获得更贴合你个人情况的回答。）';
    }
  }

  // 3) 通用兜底：给出结构化的自助建议
  return '我在当前离线模式下运行（未配置 AI_API_KEY），可以给你这几条通用建议：\n'
    + '1. 睡眠：固定起床时间，睡前 1 小时远离手机和咖啡因；\n'
    + '2. 运动：每天累计 20~30 分钟中等强度活动（快走、爬楼都算），一周约 150 分钟；\n'
    + '3. 情绪：给情绪命名 + 4-7-8 呼吸 4 轮，再找一位信任的人说一说；\n'
    + '4. 急救：鼻出血前倾按压、烫伤冷水冲 15 分钟、身边有人晕倒先判断呼吸。\n'
    + '你可以更具体地描述你的问题（例如「熬夜后头痛」「运动量够不够」「心悸胸闷」），我会给出更有针对性的解答。';
}

/**
 * 在急救知识库中做关键词匹配。
 * 命中词表覆盖常见口语说法，不依赖标点与顺序。
 */
const FIRST_AID_KEYWORDS = {
  aid_001: ['扭伤', '崴脚', '脚踝', '手腕', '拉伤', '关节肿', '韧带'],
  aid_002: ['流鼻血', '鼻血', '鼻子出血', '鼻出血', '血管破裂'],
  aid_003: ['烫伤', '烧伤', '开水', '热油', '被烫', '起水泡', '起泡'],
  aid_004: ['中暑', '热射病', '高温', '晒晕', '头晕恶心', '脱水发热'],
  aid_005: ['晕厥', '晕倒', '晕了过去', '失去意识', '低血糖', '眼前发黑', '冷汗'],
  aid_006: ['心悸', '心慌', '胸闷', '胸痛', '心跳快', '气短', '胸口难受'],
  aid_007: ['拉肚子', '腹泻', '胃肠炎', '肠胃炎', '腹痛', '呕吐', '上吐下泻'],
  aid_008: ['熬夜', '头痛', '头疼', '颈肩', '肩颈', '脖子疼', '眼睛干', '久坐酸痛'],
  aid_009: ['卡喉', '呛咳', '噎住', '窒息', '异物', '海姆立克'],
  aid_010: ['情绪崩溃', '焦虑发作', '崩溃', '自伤', '想不开', '绝望', '恐慌', '喘不上气']
};

/**
 * @returns {{item: object, hit: string}|null}
 */
function matchFirstAid(text) {
  let best = null;
  FIRST_AID_ITEMS.forEach(function (item) {
    const keywords = FIRST_AID_KEYWORDS[item.id] || [];
    for (let i = 0; i < keywords.length; i++) {
      if (text.indexOf(keywords[i]) >= 0) {
        if (!best || keywords[i].length > best.hit.length) {
          best = { item: item, hit: keywords[i] };
        }
        return;
      }
    }
  });
  return best;
}

/**
 * POST /api/ai/chat —— 大模型代理
 *
 * 配置了 AI_API_KEY 时转发到 OpenAI 兼容接口；
 * 未配置时走本地知识库兜底，保证 App 离线也能演示。
 */
async function handleAiChat(res, body) {
  const prompt = typeof body.prompt === 'string' ? body.prompt.trim() : '';
  const system = typeof body.system === 'string' ? body.system.trim() : '';
  const history = Array.isArray(body.history) ? body.history : [];

  if (!AI_API_KEY) {
    sendJson(res, 200, {
      code: 0,
      message: 'offline-fallback',
      data: { reply: buildOfflineReply(prompt || system) }
    });
    return;
  }

  if (!prompt) {
    sendJson(res, 200, failure(400, 'prompt 不能为空'));
    return;
  }

  // 组装 OpenAI 兼容格式的 messages
  const messages = [];
  if (system) {
    messages.push({ role: 'system', content: system });
  }
  history.slice(-10).forEach(function (msg) {
    if (msg && typeof msg.content === 'string' && (msg.role === 'user' || msg.role === 'assistant')) {
      messages.push({ role: msg.role, content: msg.content });
    }
  });
  messages.push({ role: 'user', content: prompt });

  const controller = new AbortController();
  const timer = setTimeout(function () { controller.abort(); }, AI_TIMEOUT_MS);

  try {
    const upstream = await fetch(AI_API_BASE + '/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + AI_API_KEY
      },
      body: JSON.stringify({
        model: AI_MODEL,
        temperature: 0.7,
        max_tokens: 1200,
        messages: messages
      }),
      signal: controller.signal
    });

    const rawText = await upstream.text();
    let parsed = null;
    try {
      parsed = JSON.parse(rawText);
    } catch (e) {
      parsed = null;
    }

    if (!upstream.ok) {
      const errMsg = parsed && parsed.error && parsed.error.message
        ? parsed.error.message
        : ('上游服务返回 HTTP ' + upstream.status);
      sendJson(res, 200, failure(500, '调用大模型失败：' + errMsg));
      return;
    }

    const choices = parsed && Array.isArray(parsed.choices) ? parsed.choices : [];
    const content = choices.length > 0 && choices[0].message
      ? String(choices[0].message.content || '').trim()
      : '';
    if (!content) {
      sendJson(res, 200, failure(500, '大模型返回内容为空或格式异常'));
      return;
    }

    sendJson(res, 200, success({
      reply: content,
      model: parsed.model || AI_MODEL
    }));
  } catch (err) {
    const reason = err && err.name === 'AbortError' ? '请求超时（60 秒）' : err.message;
    sendJson(res, 200, failure(500, '调用大模型失败：' + reason));
  } finally {
    clearTimeout(timer);
  }
}

/**
 * POST /api/report —— 健康报告
 *
 * 评分规则与 Android 端 HealthScore.calculate() 完全一致。
 */
function handleReport(res, body) {
  const sleepMinutes = clamp(toNumber(
    body.sleepAvgMinutes !== undefined ? body.sleepAvgMinutes : body.sleepAvg, 0), 0, 1440);
  const sleepQuality = clamp(toNumber(
    body.sleepAvgQuality !== undefined ? body.sleepAvgQuality : body.quality, 0), 0, 5);
  const exerciseTotal = clamp(toNumber(
    body.exerciseTotal !== undefined ? body.exerciseTotal : body.exerciseTotalMinutes, 0), 0, 100000);
  const moodAvg = clamp(toNumber(
    body.moodAvg !== undefined ? body.moodAvg : body.moodAvgScore, 0), 0, 5);

  // 1) 睡眠维度（满分 40）：时长 20 分 + 质量 20 分
  let durationScore;
  if (sleepMinutes >= 420 && sleepMinutes <= 480) {
    durationScore = 20;
  } else if (sleepMinutes < 420) {
    durationScore = 20 * Math.max(0, sleepMinutes / 420);
  } else {
    durationScore = 20 * Math.max(0, 1 - (sleepMinutes - 480) / 240);
  }
  const qualityScore = (sleepQuality / 5) * 20;
  const sleepScore = Math.round(clamp(durationScore + qualityScore, 0, 40));

  // 2) 运动维度（满分 30）：累计 150 分钟为满分
  const exerciseScore = Math.round(clamp(exerciseTotal / 150 * 30, 0, 30));

  // 3) 情绪维度（满分 30）：平均心情分 5 分制线性映射
  const moodScore = Math.round(clamp(moodAvg / 5 * 30, 0, 30));

  const score = clamp(sleepScore + exerciseScore + moodScore, 0, 100);

  let level;
  if (score >= 85) {
    level = '优秀';
  } else if (score >= 70) {
    level = '良好';
  } else if (score >= 50) {
    level = '一般';
  } else {
    level = '偏低';
  }

  let comment;
  if (score >= 85) {
    comment = '状态很棒，继续保持！';
  } else if (score >= 70) {
    comment = '状态良好，注意补充睡眠。';
  } else if (score >= 50) {
    comment = '状态一般，建议增加运动或调整作息。';
  } else if (score > 0) {
    comment = '状态偏低，建议先从规律作息开始调整。';
  } else {
    comment = '还没有记录，先完成一次健康记录吧。';
  }

  // 各维度评语
  let sleepComment;
  if (sleepMinutes < 420) {
    sleepComment = '平均睡眠 ' + Math.round(sleepMinutes) + ' 分钟，不足 7 小时，建议每晚提前 30 分钟入睡。';
  } else if (sleepMinutes > 480) {
    sleepComment = '平均睡眠 ' + Math.round(sleepMinutes) + ' 分钟，超过 8 小时，适度规律比睡得久更重要。';
  } else {
    sleepComment = '平均睡眠 ' + Math.round(sleepMinutes) + ' 分钟，时长处于 7~8 小时的理想区间。';
  }
  if (sleepQuality >= 4) {
    sleepComment += '睡眠质量良好。';
  } else if (sleepQuality > 0) {
    sleepComment += '睡眠质量偏低，睡前 1 小时远离手机、保持卧室安静黑暗会明显改善。';
  }

  let exerciseComment;
  if (exerciseTotal >= 150) {
    exerciseComment = '累计运动 ' + Math.round(exerciseTotal) + ' 分钟，已达到每周 150 分钟的推荐量，注意运动后补充水分。';
  } else if (exerciseTotal > 0) {
    exerciseComment = '累计运动 ' + Math.round(exerciseTotal) + ' 分钟，距离每周 150 分钟还差 '
      + Math.round(150 - exerciseTotal) + ' 分钟，可从每天快走 20 分钟开始。';
  } else {
    exerciseComment = '暂无运动记录，从每天 10 分钟快走或一次课间拉伸开始就很好。';
  }

  let moodComment;
  if (moodAvg >= 4) {
    moodComment = '平均心情 ' + moodAvg.toFixed(1) + ' 分，情绪状态良好，继续保持并把你的好状态分享给身边同学。';
  } else if (moodAvg >= 3) {
    moodComment = '平均心情 ' + moodAvg.toFixed(1) + ' 分，情绪整体平稳，可尝试用运动或兴趣活动增加正反馈。';
  } else if (moodAvg > 0) {
    moodComment = '平均心情 ' + moodAvg.toFixed(1) + ' 分，情绪偏低，试着每天记录情绪并找信任的人聊一聊。';
  } else {
    moodComment = '暂无情绪记录，先完成一次心情记录吧。';
  }

  const dimensions = [
    { name: '睡眠', score: sleepScore, max: 40, comment: sleepComment },
    { name: '运动', score: exerciseScore, max: 30, comment: exerciseComment },
    { name: '情绪', score: moodScore, max: 30, comment: moodComment }
  ];

  // 建议：按「得分率」从低到高排序，最薄弱的维度排在最前面
  const advicePool = [
    {
      ratio: sleepScore / 40,
      text: sleepMinutes < 420
        ? '睡眠是当前最需要补的一环：今晚就提前 30 分钟上床，把平均时长先提升到 7 小时，再谈效率。'
        : '保持现有的入睡节奏，睡前 1 小时远离手机与强光，睡眠质量还能再提一档。'
    },
    {
      ratio: exerciseScore / 30,
      text: exerciseTotal < 150
        ? '把运动拆小：课间 10 分钟做 12 个深蹲、晚饭后快走 20 分钟，一周就能补齐大部分运动量。'
        : '运动量已经达标，记得每次运动后拉伸 5 分钟，能有效减少第二天的肌肉酸痛。'
    },
    {
      ratio: moodScore / 30,
      text: moodAvg < 3
        ? '情绪需要被认真对待：每天记 3 件小确幸、找一次朋友或辅导员聊聊；若低落持续两周以上请预约校心理咨询。'
        : '情绪状态不错，试着把这份好状态分享给身边同学，低谷时它会是你最好的支撑。'
    }
  ];
  advicePool.sort(function (a, b) {
    if (a.ratio !== b.ratio) {
      return a.ratio - b.ratio;
    }
    return 0;
  });
  const advice = advicePool.map(function (item) { return item.text; });

  sendJson(res, 200, success({
    score: score,
    level: level,
    comment: comment,
    dimensions: dimensions,
    advice: advice
  }));
}

/* ==========================================================================
 * 六、路由分发
 * ========================================================================== */

const ROUTES = {
  'GET /api/health': handleHealth,
  'GET /api/tip': handleTip,
  'GET /api/firstaid': handleFirstAid,
  'GET /api/exercise': handleExercise,
  'POST /api/mood': handleMood,
  'POST /api/ai/chat': handleAiChat,
  'POST /api/report': handleReport
};

const server = http.createServer(function (req, res) {
  const parsed = url.parse(req.url, true);
  const pathname = parsed.pathname.replace(/\/+$/, '') || '/';
  const method = req.method.toUpperCase();

  // CORS 预检
  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Max-Age': '86400'
    });
    res.end();
    log('OPTIONS ' + req.url + ' -> 204');
    return;
  }

  const key = method + ' ' + pathname;
  const handler = ROUTES[key];

  if (!handler) {
    sendJson(res, 404, failure(404, 'Not Found'));
    log(method + ' ' + req.url + ' -> 404');
    return;
  }

  if (method === 'GET') {
    handler(res);
    log(method + ' ' + req.url + ' -> 200');
    return;
  }

  // POST：读取并解析请求体
  readJsonBody(req).then(function (body) {
    return handler(res, body);
  }).catch(function (err) {
    sendJson(res, 200, failure(400, err.message || '请求处理失败'));
  }).then(function () {
    log(method + ' ' + req.url + ' -> 200');
  });
});

/* ==========================================================================
 * 七、启动横幅
 * ========================================================================== */

function printBanner() {
  const line = '='.repeat(64);
  const lines = [
    '',
    line,
    '  ' + SERVER_NAME + '  v' + SERVER_VERSION,
    '  「心晴校园」Android 课程大作业 · 零依赖 Node.js 后端',
    line,
    '  监听地址   : http://' + HOST + ':' + PORT,
    '  本机访问   : http://127.0.0.1:' + PORT,
    '  模拟器访问 : http://10.0.2.2:' + PORT + '   (Android Emulator → 宿主机)',
    '  真机访问   : http://<本机局域网IP>:' + PORT,
    '  启动命令   : node server.js',
    '  运行环境   : Node.js ' + process.version + '（无需 npm install）',
    '',
    '  AI 模式    : ' + (AI_API_KEY
      ? '在线代理（模型 ' + AI_MODEL + ' @ ' + AI_API_BASE + '）'
      : '离线兜底（未设置 AI_API_KEY，使用本地知识库回复）'),
    '',
    '  接口清单：',
    '    GET  /api/health     -> http://127.0.0.1:' + PORT + '/api/health',
    '    GET  /api/tip        -> http://127.0.0.1:' + PORT + '/api/tip',
    '    GET  /api/firstaid   -> http://127.0.0.1:' + PORT + '/api/firstaid',
    '    GET  /api/exercise   -> http://127.0.0.1:' + PORT + '/api/exercise',
    '    POST /api/mood       -> http://127.0.0.1:' + PORT + '/api/mood',
    '    POST /api/ai/chat    -> http://127.0.0.1:' + PORT + '/api/ai/chat',
    '    POST /api/report     -> http://127.0.0.1:' + PORT + '/api/report',
    '',
    '  快速自测：curl -s http://127.0.0.1:' + PORT + '/api/health',
    line,
    ''
  ];
  console.log(lines.join('\n'));
}

server.listen(PORT, HOST, function () {
  printBanner();
});

server.on('error', function (err) {
  if (err && err.code === 'EADDRINUSE') {
    console.error('\n[启动失败] 端口 ' + PORT + ' 已被占用。');
    console.error('请先关闭占用该端口的进程，或换端口启动：PORT=8081 node server.js\n');
  } else {
    console.error('\n[启动失败] ' + (err && err.message ? err.message : err) + '\n');
  }
  process.exit(1);
});

/** 优雅退出，Windows 下 Ctrl+C 不残留监听端口 */
process.on('SIGINT', function () {
  console.log('\n正在关闭 ' + SERVER_NAME + ' ...');
  server.close(function () { process.exit(0); });
  setTimeout(function () { process.exit(0); }, 500).unref();
});

module.exports = { server: server, FIRST_AID_ITEMS: FIRST_AID_ITEMS, TIPS: TIPS };
