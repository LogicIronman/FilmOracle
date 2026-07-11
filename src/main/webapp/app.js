// ─── 设置管理 ───
const DEFAULT_PROMPT = `你是一位专业的电影评论分析师。请分析以下电影评论数据，输出纯 JSON 格式的分析结果（不要 markdown，不要 \`\`\`json 标记）。

分析要求：

1. 优点提炼：
从评论中找出观众反复提及的亮点，具体到场景、段落、人物关系、视听细节、叙事设计或主题表达，引用评论者的原话或观点。不要泛泛说"演技好、画面美、剧情好"。

2. 缺点与争议：
诚实指出评论中暴露的问题，例如节奏拖沓、人物动机不足、反转刻意、逻辑不通、表达用力、角色单薄、情绪操控明显等。如果评论间存在分歧，要呈现这种张力，不要回避。

3. 横向比较：
将本片与同类型、同题材、同导演或评论中提到的参照作品进行比较。比较必须落到具体手法上，例如叙事策略、视听表达、主题处理、人物塑造、类型完成度，而不是空泛地说"更深刻"或"更好"。

4. AI 影评生成：
请根据全部评论生成一段结构清晰、有判断力的 AI 影评。影评必须先概括观众对这部电影的整体评价，包括好评率、差评率、中性评价比例，以及评论整体是偏向一致好评、两极分化，还是褒贬不一。

影评需要明确说明：
- 观众总体怎么看这部电影，例如"多数观众认为本片完成度较高""评价呈现明显两极分化""好评集中但争议也很突出"。
- 好评率是多少，必须引用 sentimentDistribution 或 summary 中的 positiveRate，不要模糊说"很多人喜欢"。
- 出彩的地方在哪里：从评论中提炼观众反复提到的亮点，必须具体到场景、段落、人物关系、视听细节、叙事设计或主题表达。
- 不出彩的地方在哪里：指出评论中暴露的问题，例如节奏、逻辑、人物、表达方式、视听手法、主题表达等方面的问题。
- 如果评论存在分歧，要说明分歧点，例如同一个结尾有人认为高级，有人认为故弄玄虚。
- 最后做整体评价：说明这部电影适合什么样的观众，它的主要价值在哪里，局限在哪里。不要使用"值得一看""还不错"这类空泛结论。

影评语气要求：
- 像专业影评人，不像营销文案。
- 每个判断都要能从评论中找到依据。
- 可以引用短句或概括评论者观点，但不要编造评论中没有的信息。
- 优缺点比例要符合评论真实分布：好评率高就多写优点，差评率高就更充分写问题，两极分化就突出争议。
- 字数约 400 字。

5. 关键词：
关键词必须来自评论中的经典总结词语或高频短语，至少输出25个。避免"剧情、演技、画面"等通用词。关键词应来自评论中具体提到的场景、人物、情节、情绪体验，不要泛泛说"剧情好""演技好"。关键词应尽量体现这部电影独有的讨论点，例如具体场景名、人物关系、叙事结构、情绪体验、争议桥段、视听特征等。

6. 十维雷达图：
十维雷达图必须拉开差距，不要所有维度都集中在 8 分附近。评论明显批评的维度应低到 4-6 分，评论明显认可的突出维度可到 8.5-9.6 分。分数必须根据评论证据判断，而不是平均分配。

十维包括：剧本、导演、表演、摄影、剪辑、声音、美术、特效、主题、完成度。

7. 情绪散点 scatter（方面级情感分析）：
对评论做方面级情感分析，将情感相似的评论聚合为散点。每条评论涉及的每个方面都要单独生成一个scatter点——不要遗漏，一条评论涉及3个方面就生成3个点。然后将相同方面且情感相近（polarity差≤1.0，intensity差≤1.0）的评论聚合为一个点。
- aspect：评价方面，枚举值为 剧情|演技|视听|节奏|主题|配乐|美术|结尾
- polarity：-5.0 到 5.0 的浮点数，负值=差评，正值=好评，0=中性，精度到0.1
- intensity：0.0 到 5.0 的浮点数，0=平淡客观，5=强烈情绪（封神/崩坏/震撼/败笔），精度到0.1
- votes：该点聚合的相似评论数量（有多少条评论对该方面表达了相近的情感，不是点赞数）。votes必须反映该方面情感相似评论的实际数量，不能全填0或1。votes越大代表越多观众持相同看法。
- text：代表性评论原文摘录，选点赞最多或最具代表性的评论，最多60字

分析规则：
- 一条评论若同时涉及多个方面，拆成多条记录，每条只对应一个方面。
- 将相同方面且情感相近的评论聚合为一个点，votes为聚合的评论条数。
- 只对评论中明确提及或强烈暗示的方面打分，未提及的不要臆造。
- polarity 是对该方面的好恶，不是对整片的评价。
- intensity 是评论语言的情绪浓度，而非好恶方向。
- 反讽处理：字面正面但实际负面的评论按真实情感打负分。
- 至少输出30条scatter，评论数量多时应输出更多（50条以上为佳）。每个方面都应有至少1个点。确保散点覆盖4个象限。如果评论中提到某个方面超过3次，该方面的scatter点votes应大于1。

8. 观众情绪分布图 emotionMap：
emotionMap 需要基于 scatter 统计四象限分布，包含：
- quadrants：四象限统计，每个象限包含 name、position、count、percent、description。
- centroid：情绪重心，包含 x(polarity均值)、y(intensity均值)、label。
- summary：包含 dominantAspect(讨论最多的方面)、distributionSummary、interpretation。

四象限定义：
- rightTop：狂热好评（polarity>=0 且 intensity>=2.5，高强度正面评价）
- leftTop：强烈差评（polarity<0 且 intensity>=2.5，高强度负面评价）
- leftBottom：差强人意（polarity<0 且 intensity<2.5，低强度负面评价）
- rightBottom：比较推荐（polarity>=0 且 intensity<2.5，低强度正面评价）

9. 情感分布：
根据全部评论计算 positive、negative、neutral 的百分比，三者总和必须为 100。

10. 星级分布：
根据评论星级计算 5 星、4 星、3 星、2 星、1 星占比，百分比总和必须为 100。

11. 输出要求：
只输出一个合法 JSON 对象。不要输出解释。不要输出 markdown。不要输出代码块标记。不要在 JSON 前后添加任何多余文字。所有百分比使用整数。所有分数保留 1 位小数。所有坐标保留 2 位小数。

输出 JSON 格式：

{
  "keywords": [["关键词", 次数]],
  "ratingDistribution": [["5星", 百分比], ["4星", 百分比], ["3星", 百分比], ["2星", 百分比], ["1星", 百分比]],
  "comparison": [["本片评分", 值], ["同类型热度", 值], ["评价人数", 值], ["正向情绪", 值]],
  "radar": [["剧本", 分数], ["导演", 分数], ["表演", 分数], ["摄影", 分数], ["剪辑", 分数], ["声音", 分数], ["美术", 分数], ["特效", 分数], ["主题", 分数], ["完成度", 分数]],
  "scatter": [{"aspect": "剧情|演技|视听|节奏|主题|配乐|美术|结尾", "polarity": -5.0到5.0, "intensity": 0.0到5.0, "votes": 聚合评论数, "text": "评论原文摘录最多60字"}],
  "emotionMap": {
    "quadrants": [
      {"name": "狂热好评", "position": "rightTop", "count": 数量, "percent": 百分比, "description": "高强度正面评价"},
      {"name": "强烈差评", "position": "leftTop", "count": 数量, "percent": 百分比, "description": "高强度负面评价"},
      {"name": "差强人意", "position": "leftBottom", "count": 数量, "percent": 百分比, "description": "低强度负面评价"},
      {"name": "比较推荐", "position": "rightBottom", "count": 数量, "percent": 百分比, "description": "低强度正面评价"}
    ],
    "centroid": {"x": 值, "y": 值, "label": "情绪重心说明"},
    "summary": {"dominantAspect": "主导方面", "distributionSummary": "分布总结", "interpretation": "观影体验解读"}
  },
  "sentimentDistribution": {"positive": 百分比, "negative": 百分比, "neutral": 百分比},
  "summary": {"positiveRate": 值, "negativeRate": 值, "neutralRate": 值, "keywordsSummary": "关键词摘要", "mainControversy": "主要争议点", "totalComments": 数量},
  "review": {
    "overallReception": "观众整体评价概括，必须包含好评率、差评率、中性比例，以及整体口碑倾向",
    "highlightPoints": ["具体出彩点1，带评论依据", "具体出彩点2，带评论依据"],
    "weaknesses": ["不出彩或争议点1，带评论依据", "不出彩或争议点2，带评论依据"],
    "finalJudgement": "整体评价：适合什么观众、主要价值、局限在哪里",
    "fullText": "约400字完整AI影评"
  }
}`;

// ─── 设置管理（数据库后端） ───
let appSettings = {
  apiBase: "/api",
  commentCount: 100,
  requestTimeout: 9,
  aiModel: "deepseek-chat",
  aiApiUrl: "https://api.deepseek.com/v1/chat/completions",
  apiKey: "",
  aiPrompt: DEFAULT_PROMPT,
  fallbackEnabled: true
};

async function loadSettingsFromApi() {
  try {
    const resp = await fetch("/api/settings");
    if (resp.ok) {
      const data = await resp.json();
      if (data.ok && data.settings) {
        const s = data.settings;
        appSettings = {
          apiBase: "/api",
          commentCount: s.commentCount || 100,
          requestTimeout: s.requestTimeout || 9,
          aiModel: s.aiModel || "deepseek-chat",
          aiApiUrl: s.aiApiUrl || "https://api.deepseek.com/v1/chat/completions",
          apiKey: s.apiKey || "",
          aiPrompt: s.aiPrompt || DEFAULT_PROMPT,
          fallbackEnabled: s.fallbackEnabled !== false
        };
      }
    }
  } catch {}
}

function getSettings() {
  return appSettings;
}

async function saveSettings() {
  const settings = {
    aiModel: $("#setting-model").value,
    aiApiUrl: $("#setting-api-url").value,
    apiKey: $("#setting-api-key").value,
    aiPrompt: $("#setting-prompt").value,
    commentCount: parseInt($("#setting-comment-count").value) || 100,
    requestTimeout: parseInt($("#setting-timeout").value) || 9,
    fallbackEnabled: $("#setting-fallback").checked
  };
  try {
    const resp = await fetch("/api/settings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(settings)
    });
    const data = await resp.json();
    const hint = $("#settings-saved-hint");
    if (data.ok) {
      appSettings = { ...appSettings, ...settings };
      hint.textContent = "设置已保存到数据库";
      hint.classList.add("show");
      setTimeout(() => hint.classList.remove("show"), 2000);
    } else {
      hint.textContent = "保存失败: " + (data.error || "");
      hint.classList.add("show");
      setTimeout(() => hint.classList.remove("show"), 3000);
    }
  } catch (e) {
    const hint = $("#settings-saved-hint");
    hint.textContent = "保存失败: " + e.message;
    hint.classList.add("show");
    setTimeout(() => hint.classList.remove("show"), 3000);
  }
}

function loadSettingsToForm() {
  const s = getSettings();
  $("#setting-api-base").value = s.apiBase;
  $("#setting-comment-count").value = s.commentCount;
  $("#setting-timeout").value = s.requestTimeout;
  $("#setting-model").value = s.aiModel;
  $("#setting-api-url").value = s.aiApiUrl || "https://api.deepseek.com/v1/chat/completions";
  $("#setting-api-key").value = s.apiKey;
  $("#setting-prompt").value = s.aiPrompt;
  $("#setting-fallback").checked = s.fallbackEnabled;
}

// ─── 海报数据（pic/ 目录中全部海报文件）───
const fallbackPosters = [
  ["寄生虫", "寄生虫海报.jpg"], ["机器人总动员", "机器人总动员海报.jpg"], ["海上钢琴师", "海上钢琴师海报.jpg"],
  ["让子弹飞", "让子弹飞海报.jpg"], ["活着", "活着海报.jpg"], ["美丽人生", "美丽人生海报.jpg"],
  ["辛德勒的名单", "辛德勒的名单海报.jpg"], ["怦然心动", "怦然心动海报.jpg"], ["忠犬八公的故事", "忠犬八公的故事海报.jpg"],
  ["龙猫", "龙猫海报.jpg"], ["飞屋环游记", "飞屋环游记海报.jpg"], ["黑客帝国", "黑客帝国海报.jpg"],
  ["搏击俱乐部", "搏击俱乐部海报.jpg"], ["奥本海默", "奥本海默海报.jpg"], ["你的名字。", "你的名字。海报.jpg"],
  ["哈尔的移动城堡", "哈尔的移动城堡海报.jpg"], ["釜山行", "釜山行海报.jpg"], ["长安三万里", "长安三万里海报.jpg"],
  ["七宗罪", "七宗罪海报.jpg"], ["F1：狂飙飞车", "F1：狂飙飞车海报.jpg"], ["一战再战", "一战再战海报.jpg"],
  ["三傻大闹宝莱坞", "三傻大闹宝莱坞海报.jpg"], ["世界的主人", "世界的主人海报.jpg"], ["何以为家", "何以为家海报.jpg"],
  ["傲慢与偏见", "傲慢与偏见海报.jpg"], ["冰雪奇缘", "冰雪奇缘海报.jpg"], ["出走的决心", "出走的决心海报.jpg"],
  ["剪刀手爱德华", "剪刀手爱德华海报.jpg"], ["功夫", "功夫海报.jpg"], ["加勒比海盗", "加勒比海盗海报.jpg"],
  ["南京照相馆", "南京照相馆海报.jpg"], ["周处除三害", "周处除三害海报.jpg"], ["哈利·波特与魔法石", "哈利·波特与魔法石海报.jpg"],
  ["哈利·波特与密室", "哈利·波特与密室海报.jpg"], ["哈利·波特与阿兹卡班的囚徒", "哈利·波特与阿兹卡班的囚徒海报.jpg"], ["哈利·波特与火焰杯", "哈利·波特与火焰杯海报.jpg"],
  ["哈利·波特与凤凰社", "哈利·波特与凤凰社海报.jpg"], ["哈利·波特与混血王子", "哈利·波特与混血王子海报.jpg"], ["哈利·波特与死亡圣器(上)", "哈利·波特与死亡圣器(上)海报.jpg"],
  ["哈利·波特与死亡圣器(下)", "哈利·波特与死亡圣器(下)海报.jpg"], ["唐伯虎点秋香", "唐伯虎点秋香海报.jpg"], ["喜剧之王", "喜剧之王海报.jpg"],
  ["因果报应", "因果报应海报.jpg"], ["复仇者联盟4", "复仇者联盟4：终局之战海报.jpg"], ["大话西游之大圣娶亲", "大话西游之大圣娶亲海报.jpg"],
  ["大话西游之月光宝盒", "大话西游之月光宝盒海报.jpg"], ["天空之城", "天空之城海报.jpg"], ["头号玩家", "头号玩家海报.jpg"],
  ["头脑特工队", "头脑特工队海报.jpg"], ["姥姥的外孙", "姥姥的外孙海报.jpg"], ["小丑", "小丑海报.jpg"],
  ["少年派的奇幻漂流", "少年派的奇幻漂流海报.jpg"], ["少年的你", "少年的你海报.jpg"], ["布达佩斯大饭店", "布达佩斯大饭店海报.jpg"],
  ["年会不能停！", "年会不能停！海报.jpg"], ["当幸福来敲门", "当幸福来敲门海报.jpg"], ["心灵奇旅", "心灵奇旅海报.jpg"],
  ["恐怖游轮", "恐怖游轮海报.jpg"], ["情书", "情书海报.jpg"], ["指环王1", "指环王1：护戒使者海报.jpg"],
  ["控方证人", "控方证人海报.jpg"], ["摔跤吧！爸爸", "摔跤吧！爸爸海报.jpg"], ["放牛班的春天", "放牛班的春天海报.jpg"],
  ["教父", "教父海报.jpg"], ["无间道", "无间道海报.jpg"], ["末代皇帝", "末代皇帝海报.jpg"],
  ["本杰明·巴顿奇事", "本杰明·巴顿奇事海报.jpg"], ["机器人之梦", "机器人之梦海报.jpg"], ["死亡诗社", "死亡诗社海报.jpg"],
  ["沉默的羔羊", "沉默的羔羊海报.jpg"], ["流浪地球2", "流浪地球2海报.jpg"], ["海蒂和爷爷", "海蒂和爷爷海报.jpg"],
  ["熔炉", "熔炉海报.jpg"], ["爱乐之城", "爱乐之城海报.jpg"], ["狮子王", "狮子王海报.jpg"],
  ["猫鼠游戏", "猫鼠游戏海报.jpg"], ["玩具总动员3", "玩具总动员3海报.jpg"], ["玩具总动员", "玩具总动员海报.jpg"],
  ["疯狂原始人", "疯狂原始人海报.jpg"], ["白日梦想家", "白日梦想家海报.jpg"], ["看不见的客人", "看不见的客人海报.jpg"],
  ["神偷奶爸", "神偷奶爸海报.jpg"], ["禁闭岛", "禁闭岛海报.jpg"], ["红海行动", "红海行动海报.jpg"],
  ["罗小黑战记2", "罗小黑战记2海报.jpg"], ["罗马假日", "罗马假日海报.jpg"], ["色，戒", "色，戒海报.jpg"],
  ["花束般的恋爱", "花束般的恋爱海报.jpg"], ["蝙蝠侠：黑暗骑士", "蝙蝠侠：黑暗骑士海报.jpg"], ["蝴蝶效应", "蝴蝶效应海报.jpg"],
  ["西西里的美丽传说", "西西里的美丽传说海报.jpg"], ["触不可及", "触不可及海报.jpg"], ["记忆碎片", "记忆碎片海报.jpg"],
  ["诺曼底72小时", "诺曼底72小时海报.jpg"], ["调音师", "调音师海报.jpg"], ["超能陆战队", "超能陆战队海报.jpg"],
  ["还有明天", "还有明天海报.jpg"], ["闻香识女人", "闻香识女人海报.jpg"], ["阿凡达", "阿凡达海报.jpg"],
  ["驯龙高手", "驯龙高手海报.jpg"]
];

function shuffleArray(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

const fallbackMovies = [
  {
    id: "27010768",
    title: "寄生虫 Parasite",
    year: "2019",
    genre: "剧情 / 悬疑 / 犯罪",
    rating: "8.8",
    votes: "1800000",
    director: "奉俊昊",
    cast: "宋康昊 / 李善均 / 赵汝贞",
    region: "韩国",
    language: "韩语",
    date: "2019-05-30",
    duration: "132分钟",
    summary: "黑色幽默、阶层寓言和类型反转共同构成强烈的观影记忆。",
    poster: "寄生虫海报.jpg",
    source: "本地兜底"
  },
  {
    id: "2131459",
    title: "机器人总动员 WALL-E",
    year: "2008",
    genre: "科幻 / 动画 / 冒险",
    rating: "9.3",
    votes: "1300000",
    director: "安德鲁·斯坦顿",
    cast: "本·贝尔特 / 艾丽莎·奈特",
    region: "美国",
    language: "英语",
    date: "2008-06-27",
    duration: "98分钟",
    summary: "用极少对白讲述孤独、环保与爱，视觉表达高度统一。",
    poster: "机器人总动员海报.jpg",
    source: "本地兜底"
  },
  {
    id: "1292001",
    title: "海上钢琴师 The Legend of 1900",
    year: "1998",
    genre: "剧情 / 音乐",
    rating: "9.3",
    votes: "1700000",
    director: "朱塞佩·托纳多雷",
    cast: "蒂姆·罗斯 / 普路特·泰勒·文斯",
    region: "意大利",
    language: "英语 / 法语",
    date: "1998-10-28",
    duration: "165分钟",
    summary: "孤独、音乐与传奇人生交织，情绪余味和配乐记忆点突出。",
    poster: "海上钢琴师海报.jpg",
    source: "本地兜底"
  }
];

const fallbackComments = [
  { text: "剧情推进很快，导演对节奏和反转的控制非常稳，结尾留下的余味很强。", sentiment: "正面", star: "5星", ratingValue: 5, aspect: "剧本与叙事", quadrant: "热血/高燃" },
  { text: "表演和场面调度都很成熟，但中段有些桥段略显刻意。", sentiment: "中性", star: "3星", ratingValue: 3, aspect: "导演", quadrant: "孤独/克制" },
  { text: "画面和配乐非常抓人，几个关键场景的情绪释放很有效。", sentiment: "正面", star: "4星", ratingValue: 4, aspect: "声音与配乐", quadrant: "治愈/轻松" },
  { text: "部分人物动机有点薄弱，像是为了推动剧情而推动。", sentiment: "负面", star: "2星", ratingValue: 2, aspect: "主题与思想", quadrant: "压抑/惊悚" },
  { text: "人物关系是最大看点，观众很容易被羁绊和成长打动。", sentiment: "正面", star: "5星", ratingValue: 5, aspect: "表演", quadrant: "热血/高燃" }
];

const radarLabels = ["剧本", "导演", "表演", "摄影", "剪辑", "声音", "美术", "特效", "主题", "完成度"];

let currentMovie = null;
let currentComments = [];
let currentAnalysis = emptyAnalysis();
let currentSearchResults = [];
let currentView = "home";
let activeSentiment = "all";
const viewHistory = [];
let taskTimerId = null;
let taskTimerStartedAt = 0;
let taskTimerState = "ready";

function $(selector) {
  return document.querySelector(selector);
}

function $all(selector) {
  return Array.from(document.querySelectorAll(selector));
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[char]);
}

function emptyAnalysis() {
  return {
    keywords: [],
    ratingDistribution: [["5星", 0], ["4星", 0], ["3星", 0], ["2星", 0], ["1星", 0]],
    comparison: [],
    scatter: [],
    emotionMap: { quadrants: [], centroid: { x: 0, y: 0, label: "" }, summary: {} },
    radar: radarLabels.map((label) => [label, 0]),
    sentimentDistribution: { positive: 0, negative: 0, neutral: 0 },
    summary: { positiveRate: 0, negativeRate: 0, neutralRate: 0, keywordsSummary: "", mainControversy: "", totalComments: 0 },
    review: null,
    engine: "empty"
  };
}

// 历史记录从后端API加载，不再使用localStorage
let history = [];

function normalizePairList(list, fallback = []) {
  if (!Array.isArray(list)) return fallback;
  return list
    .map((item) => Array.isArray(item) ? [item[0], Number(item[1]) || 0] : null)
    .filter(Boolean);
}

function normalizeAnalysis(analysis = {}) {
  const empty = emptyAnalysis();
  return {
    ...empty,
    ...analysis,
    keywords: normalizePairList(analysis.keywords, empty.keywords),
    ratingDistribution: normalizePairList(analysis.ratingDistribution, empty.ratingDistribution),
    comparison: normalizePairList(analysis.comparison, empty.comparison),
    radar: normalizePairList(analysis.radar, empty.radar),
    scatter: Array.isArray(analysis.scatter) ? analysis.scatter : empty.scatter,
    emotionMap: analysis.emotionMap || empty.emotionMap,
    sentimentDistribution: analysis.sentimentDistribution || empty.sentimentDistribution,
    summary: analysis.summary || empty.summary
  };
}

function applyAnalysisToComments(comments, analysis) {
  const analyzed = Array.isArray(analysis?.analyzedComments) ? analysis.analyzedComments : [];
  return comments.map((comment, index) => {
    const detail = analyzed.find((item) => item.id && item.id === comment.id) || analyzed[index] || {};
    return {
      ...comment,
      sentiment: detail.sentiment || comment.sentiment || sentimentOf(comment),
      aspect: detail.aspect || comment.aspect || "完成度与影响力",
      quadrant: detail.quadrant || comment.quadrant || quadrantFor(comment.text || "", Number(comment.ratingValue || 3), 0)
    };
  });
}

// ─── 海报 URL 处理：外部 URL 走代理绕过防盗链 ───
function proxyPosterUrl(url) {
  if (!url) return "";
  if (url.startsWith("http") || url.startsWith("//")) {
    const fullUrl = url.startsWith("//") ? "https:" + url : url;
    return `/api/poster?url=${encodeURIComponent(fullUrl)}`;
  }
  return url;
}

function posterSrc(movie) {
  if (movie?.posterUrl) return proxyPosterUrl(movie.posterUrl);
  if (movie?.poster) return `pic/${movie.poster}`;
  const local = fallbackPosters.find(([title]) => movie?.title?.includes(title));
  return local ? `pic/${local[1]}` : "";
}

function formatVotes(value) {
  const number = Number(String(value || "0").replace(/\D/g, ""));
  if (!number) return "暂无";
  if (number >= 10000) return `${(number / 10000).toFixed(1)}万`;
  return String(number);
}

async function apiGet(path, fallback) {
  try {
    const response = await fetch(path, { headers: { Accept: "application/json" } });
    if (response.status === 401) {
      currentUser = null;
      updateAuthUI();
      showAuthModal("login");
      return typeof fallback === "function" ? fallback(new Error("请先登录")) : fallback;
    }
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } catch (error) {
    return typeof fallback === "function" ? fallback(error) : fallback;
  }
}

// ─── Toast 通知 ───
function showToast(msg, duration = 2500) {
  const toast = $("#toast");
  toast.textContent = msg;
  toast.classList.add("show");
  setTimeout(() => toast.classList.remove("show"), duration);
}

function showView(name, options = {}) {
  // 管理员限制：只能访问 home 和 settings
  if (currentUser?.role === "admin" && name !== "home" && name !== "settings") {
    showToast("管理员账号无法使用搜索、分析等功能，仅可访问设置页面");
    return;
  }
  // 普通用户不能访问设置页
  if (currentUser && currentUser.role !== "admin" && name === "settings") {
    showToast("仅管理员可访问设置页面");
    return;
  }
  if (name !== currentView && options.skipHistory !== true) {
    viewHistory.push(currentView);
  }
  currentView = name;
  $all(".view").forEach((view) => view.classList.remove("is-active"));
  $(`#view-${name}`).classList.add("is-active");
  document.body.classList.toggle("is-home", name === "home");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function goBack() {
  const previous = viewHistory.pop() || "home";
  showView(previous, { skipHistory: true });
}

// ─── 认证管理 ───
let currentUser = null;

async function checkAuth() {
  try {
    const resp = await fetch("/api/auth/check");
    const data = await resp.json();
    if (data.ok && data.user) currentUser = data.user;
    else currentUser = null;
  } catch { currentUser = null; }
  updateAuthUI();
}

function updateAuthUI() {
  const loginBtn = $("#nav-login");
  const logoutBtn = $("#nav-logout");
  const userInfo = $("#nav-user-info");
  const navSettings = $("#nav-settings");
  const navHome = $("#nav-home");
  const navImport = $("#nav-import");
  const navHistory = $("#nav-history");
  if (currentUser) {
    loginBtn.style.display = "none";
    logoutBtn.style.display = "";
    userInfo.style.display = "";
    userInfo.textContent = `${currentUser.username}（${currentUser.role === "admin" ? "管理员" : "用户"}）`;
    if (currentUser.role === "admin") {
      // 管理员：只显示设置
      navHome.style.display = "";
      navImport.style.display = "none";
      navHistory.style.display = "none";
      navSettings.style.display = "";
      if (currentView !== "home" && currentView !== "settings") showView("settings");
    } else {
      // 普通用户：显示主页/导入/历史，隐藏设置
      navHome.style.display = "";
      navImport.style.display = "";
      navHistory.style.display = "";
      navSettings.style.display = "none";
    }
    // 控制所有设置按钮的可见性
    $all('[data-view="settings"]').forEach(btn => {
      btn.style.display = (currentUser?.role === "admin") ? "" : "none";
    });
  } else {
    loginBtn.style.display = "";
    logoutBtn.style.display = "none";
    userInfo.style.display = "none";
    navHome.style.display = "";
    navImport.style.display = "";
    navHistory.style.display = "";
    navSettings.style.display = "none";
    $all('[data-view="settings"]').forEach(btn => btn.style.display = "none");
  }
}

function showAuthModal(mode) {
  const m = mode || "login";
  const title = $("#auth-title");
  const subtitle = $("#auth-subtitle");
  const submit = $("#auth-submit");
  const switchBtn = $("#auth-switch");
  if (m === "login") {
    title.textContent = "登录";
    subtitle.textContent = "登录后可使用搜索和分析功能";
    submit.textContent = "登录";
    switchBtn.textContent = "注册新账号";
  } else {
    title.textContent = "注册";
    subtitle.textContent = "注册后可使用搜索和分析功能";
    submit.textContent = "注册";
    switchBtn.textContent = "已有账号？去登录";
  }
  $("#auth-overlay").style.display = "flex";
  $("#auth-username").focus();
}

function hideAuthModal() {
  $("#auth-overlay").style.display = "none";
  $("#auth-hint").textContent = "";
  $("#auth-username").value = "";
  $("#auth-password").value = "";
}

async function handleAuthSubmit(e) {
  e.preventDefault();
  const username = $("#auth-username").value.trim();
  const password = $("#auth-password").value;
  const submitBtn = $("#auth-submit");
  const mode = submitBtn.textContent.includes("注册") ? "register" : "login";
  const hint = $("#auth-hint");
  hint.textContent = "处理中...";
  try {
    const resp = await fetch(`/api/auth/${mode}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password, role: "user" })
    });
    const data = await resp.json();
    if (data.ok) {
      if (mode === "register") {
        hint.textContent = "注册成功，自动登录中...";
        const loginResp = await fetch("/api/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password })
        });
        const loginData = await loginResp.json();
        if (loginData.ok) {
          currentUser = loginData.user; updateAuthUI(); hideAuthModal();
          await loadHistoryFromApi();
          renderHistory();
          await loadSettingsFromApi();
          loadSettingsToForm();
        }
        else { hint.textContent = "注册成功，请手动登录"; submitBtn.textContent = "登录"; }
      } else {
        currentUser = data.user; updateAuthUI(); hideAuthModal();
        await loadHistoryFromApi();
        renderHistory();
        await loadSettingsFromApi();
        loadSettingsToForm();
      }
    } else { hint.textContent = data.error || "操作失败"; }
  } catch (err) { hint.textContent = "网络错误: " + err.message; }
}

async function loadHistoryFromApi() {
  try {
    const resp = await fetch("/api/history");
    if (resp.ok) {
      const data = await resp.json();
      if (data.ok) { history = data.history || []; }
    }
  } catch {}
}

function requireAuth() {
  if (!currentUser) { showAuthModal("login"); return false; }
  if (currentUser.role === "admin") { setTask(["> 管理员账号无法使用搜索功能", "> 请用普通用户账号登录"], "blocked"); return false; }
  return true;
}

function posterCard([title, file]) {
  const div = document.createElement("div");
  div.className = "mini-poster";
  const f = String(file || "");
  let src;
  if (f.startsWith("http") || f.startsWith("//")) {
    src = proxyPosterUrl(f);
  } else if (f.startsWith("/api/") || f.startsWith("pic/")) {
    src = f;
  } else {
    src = `pic/${f}`;
  }
  div.innerHTML = `<img src="${escapeHtml(src)}" alt="${escapeHtml(title)}海报" onerror="this.style.display='none'"><span>${escapeHtml(title)}</span>`;
  return div;
}

function posterTrack(items, direction) {
  const track = document.createElement("div");
  track.className = `poster-track ${direction === "right" ? "move-right" : "move-left"}`;
  for (let i = 0; i < 2; i += 1) {
    const set = document.createElement("div");
    set.className = "poster-set";
    items.forEach((poster) => set.appendChild(posterCard(poster)));
    track.appendChild(set);
  }
  return track;
}

function renderPosterRows() {
  const shuffled = shuffleArray(fallbackPosters);
  const mid = Math.ceil(shuffled.length / 2);
  const topHalf = shuffled.slice(0, mid);
  const bottomHalf = shuffled.slice(mid);
  $("#poster-row-top").replaceChildren(posterTrack(topHalf, "left"));
  $("#poster-row-bottom").replaceChildren(posterTrack(bottomHalf, "right"));
}

function setTask(lines, state = "ready") {
  taskTimerState = state;
  $("#task-state").textContent = taskTimerId ? `${state} · ${formatElapsedTime(nowMilliseconds() - taskTimerStartedAt)}` : state;
  $("#task-log").textContent = lines.join("\n");
}

function appendTask(line, state) {
  const log = $("#task-log");
  log.textContent += `${line}\n`;
  log.scrollTop = log.scrollHeight;
  if (state) taskTimerState = state;
  const visibleState = state || taskTimerState;
  $("#task-state").textContent = taskTimerId ? `${visibleState} · ${formatElapsedTime(nowMilliseconds() - taskTimerStartedAt)}` : visibleState;
}

function nowMilliseconds() {
  return typeof performance !== "undefined" && typeof performance.now === "function" ? performance.now() : Date.now();
}

function formatElapsedTime(milliseconds) {
  const totalTenths = Math.max(0, Math.floor(Number(milliseconds || 0) / 100));
  const minutes = Math.floor(totalTenths / 600);
  const seconds = Math.floor((totalTenths % 600) / 10);
  const tenths = totalTenths % 10;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}.${tenths}`;
}

function resetTaskTimer() {
  if (taskTimerId) clearInterval(taskTimerId);
  taskTimerId = null;
  taskTimerStartedAt = 0;
}

function startTaskTimer(label = "running") {
  resetTaskTimer();
  taskTimerState = label;
  taskTimerStartedAt = nowMilliseconds();
  const update = () => {
    const state = $("#task-state");
    if (state) state.textContent = `${taskTimerState} · ${formatElapsedTime(nowMilliseconds() - taskTimerStartedAt)}`;
  };
  update();
  taskTimerId = setInterval(update, 100);
}

function stopTaskTimer(outcome = "done") {
  if (!taskTimerStartedAt) return 0;
  const elapsed = nowMilliseconds() - taskTimerStartedAt;
  resetTaskTimer();
  taskTimerState = outcome;
  appendTask(`> 总耗时: ${(elapsed / 1000).toFixed(1)} 秒`);
  const state = $("#task-state");
  if (state) state.textContent = `${outcome} · ${formatElapsedTime(elapsed)}`;
  return elapsed;
}

function renderResults(query, movies, meta = {}) {
  $("#results-movie-query").value = query || "";
  currentSearchResults = Array.isArray(movies) ? movies : [];
  const list = $("#result-list");
  list.innerHTML = "";
  if (!currentSearchResults.length) {
    list.innerHTML = `<article class="comment-item"><p>没有搜索结果。请更换关键词，或检查豆瓣 API 是否可用。</p></article>`;
    return;
  }
  currentSearchResults.forEach((movie, idx) => {
    const image = posterSrc(movie);
    const item = document.createElement("article");
    item.className = "result-item";
    item.style.animationDelay = (idx * 60) + "ms";
    item.innerHTML = `
      <div class="mini-poster">${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(movie.title)}海报" onerror="this.style.display='none'">` : ""}<span>${escapeHtml(movie.title)}</span></div>
      <div>
        <h3>${escapeHtml(movie.title)} <span class="muted">${movie.year ? `(${escapeHtml(movie.year)})` : ""}</span></h3>
        <p><span class="stars">★★★★★</span> ${escapeHtml(movie.rating || "暂无")} (${formatVotes(movie.votes)}人评价)</p>
        <p class="result-meta">${escapeHtml([movie.region, movie.genre, movie.duration].filter(Boolean).join(" / "))}</p>
        <p class="result-meta">${escapeHtml([movie.director, movie.cast].filter(Boolean).join(" / "))}</p>
        <p class="result-meta">数据来源：${escapeHtml(meta.source || movie.source || "本地")}</p>
      </div>
      <button class="primary-action" type="button" data-movie="${escapeHtml(movie.id)}">分析</button>
    `;
    list.appendChild(item);
  });
}

function ratingRows(comments) {
  const counts = [5, 4, 3, 2, 1].map((star) => [star, comments.filter((comment) => Number(comment.ratingValue) === star).length]);
  const total = counts.reduce((sum, [, count]) => sum + count, 0) || 1;
  return counts.map(([star, count]) => [`${star}星`, Math.round((count / total) * 100)]);
}

function ratingBarsHtml(rows = currentAnalysis.ratingDistribution) {
  return rows.map(([label, value]) => `
    <div class="bar-row">
      <span>${escapeHtml(label)}</span>
      <span class="bar-track"><span class="bar-fill" style="width:${value}%"></span></span>
      <span>${value}%</span>
    </div>
  `).join("");
}

function renderDetail(movie = currentMovie) {
  const imported = movie?.source === "导入评论文件";
  $("#fetch-comments").textContent = imported ? "本地分析" : "获取评论并本地规则分析";
  $("#fetch-analyze").textContent = imported ? "AI 分析" : "获取并 AI 分析";
  if (!movie) {
    $("#detail-heading").textContent = "电影详情分析";
    $("#detail-poster").innerHTML = `<span>等待选择电影</span>`;
    $("#movie-facts").innerHTML = `<p class="muted">请先从首页搜索并选择电影，或从导入评论入口填写来源电影信息。</p>`;
    $("#rating-panel").innerHTML = `<p class="muted">暂无评分数据。</p>`;
    renderCharts();
    renderComments();
    renderReview();
    return;
  }
  currentMovie = movie;
  $("#detail-heading").textContent = movie.title;
  const image = posterSrc(movie);
  const poster = $("#detail-poster");
  poster.innerHTML = `${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(movie.title)}海报" onerror="this.style.display='none'">` : ""}<span>${escapeHtml(movie.title)}</span>`;
  $("#movie-facts").innerHTML = `
    <h2>${escapeHtml(movie.title)} <span class="muted">${movie.year ? `(${escapeHtml(movie.year)})` : ""}</span></h2>
    <p>${escapeHtml(movie.summary || "简介待同步。")}</p>
    <dl>
      <dt>导演</dt><dd>${escapeHtml(movie.director || "待同步")}</dd>
      <dt>主演</dt><dd>${escapeHtml(movie.cast || "待同步")}</dd>
      <dt>类型</dt><dd>${escapeHtml(movie.genre || "待同步")}</dd>
      <dt>地区</dt><dd>${escapeHtml(movie.region || "待同步")}</dd>
      <dt>语言</dt><dd>${escapeHtml(movie.language || "待同步")}</dd>
      <dt>上映</dt><dd>${escapeHtml(movie.date || "待同步")}</dd>
      <dt>片长</dt><dd>${escapeHtml(movie.duration || "待同步")}</dd>
      <dt>来源</dt><dd>${escapeHtml(movie.source || "Douban")}</dd>
    </dl>
  `;
  $("#rating-panel").innerHTML = `
    <p class="eyebrow">综合口碑</p>
    <div class="rating-score"><strong>${escapeHtml(movie.rating || "-")}</strong><span class="stars">★★★★★</span></div>
    <p class="muted">${formatVotes(movie.votes)} 人评价</p>
    ${ratingBarsHtml()}
  `;
  renderCharts();
  renderComments();
  renderReview();
}

function buildAnalysis(comments, movie) {
  if (!comments.length || !movie) return emptyAnalysis();
  const text = comments.map((comment) => comment.text || "").join(" ");
  const keywords = extractDistinctKeywords(comments);
  const positive = comments.filter((comment) => sentimentOf(comment) === "正面").length;
  const negative = comments.filter((comment) => sentimentOf(comment) === "负面").length;
  const neutral = Math.max(0, comments.length - positive - negative);
  const averageRating = comments.reduce((sum, comment) => sum + Number(comment.ratingValue || 3), 0) / comments.length;
  const localScatter = buildLocalScatter(comments);
  return {
    keywords,
    ratingDistribution: ratingRows(comments),
    comparison: [
      ["本片评分", Math.round(Number(movie.rating || averageRating * 2) * 10)],
      ["同类型热度", Math.min(96, 50 + Math.round(Math.log10(Number(movie.votes || 10000)) * 8))],
      ["评价人数", Math.min(98, 42 + comments.length * 2)],
      ["正向情绪", Math.round((positive / comments.length) * 100)]
    ],
    scatter: localScatter,
    emotionMap: buildLocalEmotionMap(localScatter),
    radar: buildRadarScores(comments, movie, text),
    sentimentDistribution: {
      positive: Math.round((positive / comments.length) * 100),
      negative: Math.round((negative / comments.length) * 100),
      neutral: Math.round((neutral / comments.length) * 100)
    },
    summary: {
      positiveRate: Math.round((positive / comments.length) * 100),
      negativeRate: Math.round((negative / comments.length) * 100),
      neutralRate: Math.round((neutral / comments.length) * 100),
      keywordsSummary: keywords.slice(0, 5).map(([word]) => word).join("、"),
      mainControversy: findControversy(comments),
      totalComments: comments.length
    },
    review: buildLocalReview(comments, movie, keywords, positive, negative),
    engine: "local"
  };
}

function sentimentOf(comment) {
  if (comment.sentiment) return comment.sentiment;
  const rating = Number(comment.ratingValue || 3);
  if (rating >= 4) return "正面";
  if (rating <= 2) return "负面";
  const text = comment.text || "";
  const pos = countTerms(text, ["震撼", "惊艳", "喜欢", "经典", "精彩", "细腻", "封神", "感动", "治愈", "好看", "绝了"]);
  const neg = countTerms(text, ["失望", "无聊", "拖沓", "刻意", "尴尬", "生硬", "过誉", "拉胯", "难看", "漏洞"]);
  if (pos > neg) return "正面";
  if (neg > pos) return "负面";
  return "中性";
}

function countTerms(text, terms) {
  return terms.reduce((sum, term) => sum + ((text.match(new RegExp(term, "g")) || []).length), 0);
}

function extractDistinctKeywords(comments) {
  // ── 停用词表：与后端 AnalysisService.GENERIC_KEYWORDS 同步 ──
  const stop = new Set([
    // 影视/片名泛称
    "电影", "影片", "这部", "一片", "此片", "该片", "此剧", "本片", "整个", "整部", "一部",
    // 人称/指示代词
    "一个", "两个", "三个", "他们", "我们", "你们", "自己", "别人", "大家", "所有",
    "每个", "各自", "互相", "彼此", "其他", "其它", "某些", "任何",
    "这个", "那个", "这些", "那些", "这是", "那是",
    // 疑问词
    "什么", "怎么", "为什么", "怎么样", "到底", "究竟", "难道",
    // 数量词
    "一些", "一下", "一点", "一种", "一次", "一遍", "一场", "一番", "一幕",
    "很多", "很少", "半部",
    // 程度副词
    "非常", "比较", "有点", "特别", "确实", "相对", "一般", "普通",
    "真的", "简直", "似乎", "果然", "居然", "竟然", "毕竟",
    // 泛泛评价（过于笼统）
    "好看", "不好", "不错", "还行", "太差", "太好", "说是", "来说",
    // 介词/连词
    "因为", "所以", "但是", "可以", "对于", "关于", "由于", "基于",
    "随着", "等于", "属于", "至于", "如果", "虽然", "不过", "其实",
    "而且", "然后", "或者", "还是", "不仅", "不但", "何况", "以及",
    "或是", "要么", "不论", "不管", "无论", "与其", "不如", "以便",
    "除非", "一旦", "万一", "即便", "哪怕", "即使", "尽管", "并且",
    // 时间词
    "时候", "一直", "已经", "后来", "最后", "开始", "出现", "曾经",
    "以前", "以后", "之前", "之后", "刚刚", "马上", "立刻",
    "忽然", "突然", "逐渐", "终于",
    // 助动词/否定
    "不得", "不到", "不会", "不能", "不要", "不用", "不行", "无关", "无法",
    // 结构助词/语气词
    "的话", "似的", "这么", "看的", "的是", "有的", "一样", "有些", "也是",
    "都是", "不算", "而是", "本来", "原来", "出来", "起来", "下去",
    "过来", "过去", "回来",
    // 认知动词（泛义）
    "觉得", "感觉", "认为", "以为", "知道", "明白", "理解", "发现", "看到",
    // 复合功能短语
    "我觉得", "我感觉", "我认为", "我想说", "说实话", "讲真", "客观说",
    "个人觉", "总体来", "整体来", "综合来", "总结来", "一句话",
    "只能说", "不知道", "不觉得", "不至于", "完全不", "并不",
    "并非", "并非是", "并不是", "不算太", "不算很",
    // 影视评价维度泛称（通用，不作为关键词）
    "剧情", "演技", "画面", "导演", "演员", "故事", "角色", "配乐",
    "镜头", "表演", "主题", "节奏", "音乐", "观众",
    // 方位/处所词
    "地方", "东西", "当中", "其中", "以上", "以下", "以外", "以内",
    // 情态词
    "可能", "应该", "也许", "或许",
    // 看过/看了相关
    "看了", "看完", "看过", "看着",
    "看了一", "看了两", "看了三", "看完这", "看完那",
    // 观影相关短语
    "演技在", "画面在", "剧情在", "节奏在", "配乐在", "主题在",
    "第一遍", "第二次", "第一次", "一开始", "第一眼",
    "整个电", "整个故", "整部片", "整部电",
    "且不", "而且不", "但也不", "可是不", "却不", "却没",
    // 其他高频功能词
    "就是", "没有", "不是", "还有", "这样", "那样",
    // JS端额外停用词
    "豆瓣", "演的", "拍的", "说的", "想的", "做的", "了的", "着的", "过的",
    "好的", "差的", "大的", "小的", "多的", "少的", "高的", "低的",
    "新的", "旧的", "长的", "短的", "深的", "浅的", "厚的", "薄的",
    "远的", "近的", "快的", "慢的", "早的", "晚的", "热的", "冷的",
    "满的", "空的", "假的", "对的", "错的", "难的", "易的",
    "强的", "弱的", "亮的", "暗的", "重的", "轻的", "宽的", "窄的",
    "硬的", "软的", "干的", "湿的", "生的", "熟的", "活的", "死的",
    "美的", "丑的", "善的", "恶的"
  ]);
  const freq = new Map();
  comments.forEach((comment) => {
    const text = (comment.text || "").replace(/[^\u4e00-\u9fa5A-Za-z0-9]/g, " ");
    const chineseParts = text.match(/[\u4e00-\u9fa5]{2,10}/g) || [];
    chineseParts.forEach((part) => {
      for (let size = Math.min(6, part.length); size >= 2; size -= 1) {
        for (let i = 0; i <= part.length - size; i += 1) {
          const phrase = part.slice(i, i + size);
          if (stop.has(phrase)) continue;
          if (/^(这部|一部|很多|观众|豆瓣|看完|看过|时候|因为|所以|但是|如果|可以|什么|怎么|他们|我们|你们|自己|别人|大家|所有|一直|已经|还是|或者|而且|然后|后来|最后|开始|出现|这是|那是|还有|虽然|不过|其实|就是|这样|那样|一些|很少|特别|确实|相对|一般|普通|好看|不好|不错|还行|太差|太好|说是|来说|对于|地方|东西|看到|发现|明白|理解|知道|认为|以为|出来|起来|下去|过去|回来|不了|不到|不会|不能|不要|不用|不行|无法|一样)$/.test(phrase)) continue;
          freq.set(phrase, (freq.get(phrase) || 0) + 1 + Math.min(3, Number(comment.voteCount || 0) / 30));
        }
      }
    });
  });

  // ── 复合词加权：4字短语若由两个有意义的2字词组成，则提升50%权重 ──
  for (const word of [...freq.keys()]) {
    if (word.length === 4) {
      const first2 = word.slice(0, 2);
      const last2 = word.slice(2, 4);
      if (freq.has(first2) && freq.has(last2) && !stop.has(first2) && !stop.has(last2)) {
        freq.set(word, freq.get(word) * 1.5);
      }
    }
  }

  const funcChars = new Set("的了是是在和与或但我你他她它们这那有就还不没也都很太个着过把被让使向从到为以于上下中外前后里间地得着过上下一中可来去");
  const isMeaningless2Char = (phrase) => {
    if (phrase.length !== 2) return false;
    return funcChars.has(phrase[0]) || funcChars.has(phrase[1]);
  };
  const allWords = new Set(freq.keys());

  // ── 过滤+排序（门槛1.5） ──
  let ranked = [...freq.entries()]
    .filter(([word, count]) => count >= 1.5 && !isMeaningless2Char(word) && ![...stop].some((generic) => word.includes(generic) && word.length <= generic.length + 1))
    .filter(([word, count]) => {
      // 仅当词长差≤1且互为子串且频率相近时，移除短词
      for (const other of allWords) {
        if (other !== word && other.length > word.length && other.length - word.length <= 1 && other.includes(word) && freq.get(other) >= count * 0.5) return false;
      }
      return true;
    })
    .sort((a, b) => b[1] - a[1] || b[0].length - a[0].length);

  // ── 自适应门槛：不足30个时降低到1.0 ──
  if (ranked.length < 30) {
    ranked = [...freq.entries()]
      .filter(([word, count]) => count >= 1.0 && !isMeaningless2Char(word) && ![...stop].some((generic) => word.includes(generic) && word.length <= generic.length + 1))
      .filter(([word, count]) => {
        for (const other of allWords) {
          if (other !== word && other.length > word.length && other.length - word.length <= 1 && other.includes(word) && freq.get(other) >= count * 0.5) return false;
        }
        return true;
      })
      .sort((a, b) => b[1] - a[1] || b[0].length - a[0].length);
  }

  // ── 去重：仅当词长差<2且互为子串时才去重，避免长词吞掉短词 ──
  const selected = [];
  ranked.forEach(([word, count]) => {
    if (selected.length >= 30) return;
    if (selected.some(([existing]) => {
      const lenDiff = Math.abs(existing.length - word.length);
      return lenDiff < 2 && (existing.includes(word) || word.includes(existing));
    })) return;
    selected.push([word, Math.max(1, Math.round(count))]);
  });

  // ── 兜底：空结果时从评论中取高频2字词 ──
  if (selected.length === 0) {
    const twoCharFreq = new Map();
    comments.forEach((comment) => {
      const text = (comment.text || "").replace(/[^\u4e00-\u9fa5]/g, " ");
      const matches = text.match(/[\u4e00-\u9fa5]{2}/g) || [];
      matches.forEach((w) => {
        if (!stop.has(w) && !isMeaningless2Char(w)) {
          twoCharFreq.set(w, (twoCharFreq.get(w) || 0) + 1);
        }
      });
    });
    const sorted = [...twoCharFreq.entries()].sort((a, b) => b[1] - a[1]);
    for (const [word, count] of sorted) {
      if (selected.length >= 10) break;
      selected.push([word, Math.max(1, count)]);
    }
    if (selected.length === 0) {
      selected.push(["观影体验", 3], ["情感表达", 2]);
    }
  }

  return selected;
}

function emotionPoint(comment, index) {
  // 保留兼容：生成旧格式x/y坐标
  const text = comment.text || "";
  const sentiment = sentimentOf(comment);
  const rating = Number(comment.ratingValue || 3);
  const pos = countTerms(text, ["震撼", "惊艳", "喜欢", "经典", "精彩", "感动", "治愈", "温暖", "封神", "绝"]);
  const neg = countTerms(text, ["失望", "无聊", "拖沓", "刻意", "尴尬", "生硬", "过誉", "压抑", "窒息", "难受"]);
  let x = (rating - 3) / 2 + (pos - neg) * 0.18;
  if (sentiment === "正面") x += 0.18;
  if (sentiment === "负面") x -= 0.18;
  const intensityWords = countTerms(text, ["震撼", "封神", "绝", "崩溃", "窒息", "压抑", "泪目", "愤怒", "治愈", "破防", "惊艳"]);
  const y = Math.max(0.12, Math.min(1, 0.22 + intensityWords * 0.16 + Math.min(0.28, text.length / 260)));
  return {
    x: Math.round(Math.max(-1, Math.min(1, x)) * 100) / 100,
    y: Math.round(y * 100) / 100,
    sentiment,
    label: quadrantFor(text, rating, x, y),
    index
  };
}

// ── 前端本地散点（方面级聚合）──
const ASPECT_REGEX = {
  "剧情": /故事|情节|叙事|剧本|线索|伏笔|反转|逻辑|设定|桥段|套路|开头|铺垫|前半|后半|世界观|人设|动机|结局走向|梗|虐心|催泪|狗血|俗套|脉络|骨架|人物弧|起承转合|悬念|暗线|明线|支线|主线|剧情线|叙事节奏|叙事结构|叙事方式|故事性|故事内核|故事走向|情节推动|转折点|高潮|波折|跌宕|信息量|叙事密度|剧本扎实|剧本薄弱|剧本硬伤|剧本漏洞|剧本逻辑|剧本结构|剧本完成度|剧情流畅|剧情紧凑|剧情松散|剧情推进|剧情发展|剧情走向|剧情转折|剧情反转|剧情高潮|剧情起伏|弧光/,
  "演技": /演技|表演|演员|角色|饰演|演绎|主角|配角|群像|表演力|刻画|塑造|眼神|微表情|出戏|入戏|台词功|台词功底|演技在线|演得好|演得|代入感|感染力|表演层次|情绪递进|共情|信念感|撑起|扛住|演技炸裂|演技派|实力派|老戏骨|面瘫|油腻|用力过猛|表演痕迹|浮夸|做作|呆板|灵动|传神|到位|精准|拿捏|分寸感|张力|爆发力|控制力|表现力|角色贴合|人戏合一|角色塑造|人物刻画|人物塑造|形象立体|形象饱满|有血有肉|演活了|演技碾压|演技吊打/,
  "视听": /画面|镜头|摄影|视效|特效|色彩|构图|光影|视觉|画面感|大片感|视觉冲击|美学|质感|帧|截图|名场面|视觉盛宴|镜头美学|色彩美学|光影美学|每一帧|壁纸级|绝美画面|视觉体验|视觉享受|视觉奇观|画面精致|画面考究|画面细腻|画面质感|画面唯美|画面大气|调色|滤镜|色调|景深|慢镜头|长镜头|特写|远景|全景|构图精妙|运镜|机位|取景|布光|打光|画面构图|镜头调度|镜头语言|画面语言|视觉语言|影像语言|电影感|大片质感|画面冲击力|视觉表现力|画面表现力|画面张力|画面感染力|画面沉浸感|画面代入感/,
  "节奏": /节奏|拖沓|紧凑|缓慢|快|慢|剪辑|转折|推进|松散|冗长|注水|拖戏|拖拉|太长|太慢|太快|高能|停不下来|一气呵成|节奏感|节奏控制|张弛有度|行云流水|丝滑|顺畅|流畅|割裂|突兀|拖泥带水|磨叽|注水剧|水剧|水分|凑数|拖时长|精简|利落|一口气|追剧|熬夜|欲罢不能|沉浸|节奏明快|节奏流畅|节奏紧凑|节奏松散|节奏拖沓|节奏混乱|节奏失调|收放自如|松紧有度|干脆|凝练|节奏拉满|全程高能|全程无尿点|紧凑感|松弛感|节奏断裂|节奏跳跃/,
  "主题": /主题|思想|深度|内涵|隐喻|象征|哲学|意义|表达|探讨|女性|男权|社会|现实|阶层|批判|反思|启示|价值观|立场|议题|社会议题|现实议题|人文关怀|人性|人伦|伦理|道德|救赎|善恶|正义|公平|自由|平等|抗争|反抗|觉醒|女性觉醒|女性意识|父权|阶层固化|阶级|贫富|城乡|文化冲突|代际|移民|弱势群体|边缘|歧视|偏见|刻板印象|身份认同|家国情怀|民族|时代缩影|寓言|讽刺|荒诞|黑色幽默|悲悯|终极关怀|存在主义|异化|体制|规则|丛林法则|文明|野蛮|思想性|思想深度|思想内核|主题表达|主题深度|主题内核|社会批判|现实批判|社会反思|现实反思|人文思考|哲学思考/,
  "配乐": /配乐|音乐|音效|BGM|主题曲|背景音|原声|OST|配乐感|音乐性|旋律|曲调|编曲|作曲|交响|弦乐|钢琴|鼓点|和声|片尾曲|插曲|片头曲|背景音乐|声音设计|声效|环境音|混音|声场|环绕|立体声|杜比|听觉体验|音乐品味|音乐张力|音乐情绪|音乐感染力|音乐烘托|音乐渲染|音乐铺垫|音乐高潮|音乐留白|静默|配乐大师|配乐绝|配乐封神|配乐拉胯|配乐出戏|配乐违和|配乐加分|配乐减分|音乐响起|BGM响起|配乐到位|配乐精准|配乐出色/,
  "美术": /美术|布景|服装|道具|场景|美术设计|造型|服化道|审美|服化|妆造|美术风格|置景|年代感|服化精美|美术指导|场景设计|场景搭建|场景布置|场景氛围|场景质感|场景细节|场景还原|场景沉浸|场景真实|场景考究|场景精致|服装设计|服装造型|服装质感|道具设计|道具制作|道具质感|化妆|妆容|特效化妆|造型设计|实景|棚拍|外景|内景|年代还原|年代质感|服化道精美|服化道考究|美术质感|美术审美|美术水准|美术完成度|视觉风格|美术品位/,
  "结尾": /结尾|结局|最后|收尾|尾声|终章|收场|落幕|结局走向|结尾好|结尾烂|HE|BE|开放式|开放式结局|圆满|意难平|烂尾|神结尾|反转结局|意料之外|情理之中|首尾呼应|闭环|留白|余味|回味|意犹未尽|戛然而止|仓促|草率|虎头蛇尾|画蛇添足|狗尾续貂|画龙点睛|升华|收束|终结|余韵|耐人寻味|引人深思|发人深省|结尾反转|结局反转|结局圆满|结局遗憾|结局仓促|结尾升华|结尾点睛|结尾留白|结尾余味|结尾回味|结尾意犹未尽/
};

function buildLocalScatter(comments) {
  const clusters = new Map();
  for (const comment of comments) {
    const text = comment.text || "";
    if (!text) continue;
    const rating = Number(comment.ratingValue || 3);
    const pos = countTerms(text, ["震撼", "惊艳", "精彩", "感动", "封神", "绝", "经典", "完美", "治愈", "温暖", "好看", "出色", "到位", "细腻", "惊艳", "亮眼", "加分", "舒服", "流畅", "自然", "真诚", "用心"]);
    const neg = countTerms(text, ["失望", "无聊", "拖沓", "刻意", "尴尬", "生硬", "过誉", "烂", "差", "平庸", "俗套", "老套", "空洞", "乏味", "不行", "不足", "薄弱", "违和", "出戏", "可惜", "遗憾", "拉胯", "离谱", "恶心"]);
    const strongPos = /封神|绝了|震撼|神作|标杆|教科书|天花板|无可挑剔|完美|杰作|大师/.test(text);
    const strongNeg = /崩坏|败笔|烂尾|灾难|一塌糊涂|惨不忍睹|垃圾|烂片|拉胯|稀烂|离谱|恶心/.test(text);
    let anyAspect = false;

    for (const [aspect, regex] of Object.entries(ASPECT_REGEX)) {
      if (!regex.test(text)) continue;
      anyAspect = true;
      let polarity = (rating - 3) * 1.2;
      if (strongPos) polarity += 2.0;
      if (strongNeg) polarity -= 2.0;
      polarity += (pos - neg) * 0.3;
      // 评论长度的影响：长评论polarity更极端
      const textLen = text.length;
      if (textLen > 50) polarity += polarity > 0 ? 0.3 : (polarity < 0 ? -0.3 : 0);
      if (textLen > 100) polarity += polarity > 0 ? 0.2 : (polarity < 0 ? -0.2 : 0);
      // 否定词的处理："不好"vs"好"应区分
      const negCount = (text.match(/[不没未别勿莫无非]/g) || []).length;
      if (negCount > 0 && pos > neg) polarity -= negCount * 0.15;
      polarity = Math.max(-5, Math.min(5, Math.round(polarity * 10) / 10));

      // 具体情绪词权重区分（"封神"权重高于"好看"）
      const strongEmotionCount = countTerms(text, ["封神", "绝了", "神作", "天花板", "教科书", "无可挑剔", "崩坏", "败笔", "烂尾", "灾难", "一塌糊涂", "惨不忍睹", "垃圾", "拉胯", "稀烂"]) * 2;
      const normalEmotionCount = countTerms(text, ["震撼", "绝", "崩", "烂", "败", "哭", "泪", "破防", "窒息", "压抑", "愤怒", "爽", "燃", "惊艳", "泪目"]);
      const emotionCount = strongEmotionCount + normalEmotionCount;
      // 评论长度加权
      const lenBonus = Math.min(1.5, textLen / 100);
      // 标点符号（感叹号、问号多=情绪强烈）
      const punctBonus = Math.min(1.5, (text.match(/[！!？?]/g) || []).length * 0.4);
      let intensity = 1.0 + emotionCount * 0.6 + lenBonus + punctBonus;
      if (strongPos || strongNeg) intensity += 1.0;
      intensity = Math.max(0.5, Math.min(5, Math.round(intensity * 10) / 10));

      const key = aspect + "_" + polarity + "_" + intensity;
      if (!clusters.has(key)) {
        clusters.set(key, { aspect, polarity, intensity, votes: 0, maxVotes: -1, text: "" });
      }
      const cl = clusters.get(key);
      cl.votes++;
      if ((comment.voteCount || 0) > cl.maxVotes) {
        cl.maxVotes = comment.voteCount || 0;
        cl.text = text.length > 60 ? text.substring(0, 60) + "..." : text;
      }
    }

    if (!anyAspect) {
      let polarity = (rating - 3) * 1.2;
      if (strongPos) polarity += 2.0;
      if (strongNeg) polarity -= 2.0;
      polarity += (pos - neg) * 0.3;
      const textLen = text.length;
      if (textLen > 50) polarity += polarity > 0 ? 0.3 : (polarity < 0 ? -0.3 : 0);
      if (textLen > 100) polarity += polarity > 0 ? 0.2 : (polarity < 0 ? -0.2 : 0);
      const negCount = (text.match(/[不没未别勿莫无非]/g) || []).length;
      if (negCount > 0 && pos > neg) polarity -= negCount * 0.15;
      polarity = Math.max(-5, Math.min(5, Math.round(polarity * 10) / 10));
      const strongEmotionCount = countTerms(text, ["封神", "绝了", "神作", "天花板", "教科书", "无可挑剔", "崩坏", "败笔", "烂尾", "灾难", "一塌糊涂", "惨不忍睹", "垃圾", "拉胯", "稀烂"]) * 2;
      const normalEmotionCount = countTerms(text, ["震撼", "绝", "崩", "烂", "败", "哭", "泪", "破防", "窒息", "压抑", "愤怒", "爽", "燃", "惊艳", "泪目"]);
      const emotionCount = strongEmotionCount + normalEmotionCount;
      const lenBonus = Math.min(1.5, textLen / 100);
      const punctBonus = Math.min(1.5, (text.match(/[！!？?]/g) || []).length * 0.4);
      let intensity = 1.0 + emotionCount * 0.6 + lenBonus + punctBonus;
      intensity = Math.max(0.5, Math.min(5, Math.round(intensity * 10) / 10));
      const key = "剧情_" + polarity + "_" + intensity;
      if (!clusters.has(key)) {
        clusters.set(key, { aspect: "剧情", polarity, intensity, votes: 0, maxVotes: -1, text: "" });
      }
      const cl = clusters.get(key);
      cl.votes++;
      if ((comment.voteCount || 0) > cl.maxVotes) {
        cl.maxVotes = comment.voteCount || 0;
        cl.text = text.length > 60 ? text.substring(0, 60) + "..." : text;
      }
    }
  }

  const scatter = [];
  let jitterSeed = 42;
  for (const cl of clusters.values()) {
    const jx = ((jitterSeed = (jitterSeed * 9301 + 49297) % 233280) / 233280 - 0.5) * 0.3;
    const jy = ((jitterSeed = (jitterSeed * 9301 + 49297) % 233280) / 233280 - 0.5) * 0.2;
    scatter.push({
      aspect: cl.aspect,
      polarity: Math.round(Math.max(-5, Math.min(5, cl.polarity + jx)) * 10) / 10,
      intensity: Math.round(Math.max(0, Math.min(5, cl.intensity + jy)) * 10) / 10,
      votes: cl.votes,
      text: cl.text
    });
  }
  if (scatter.length === 0) {
    scatter.push({ aspect: "剧情", polarity: 0, intensity: 1, votes: 1, text: "" });
  }
  return scatter;
}

function buildLocalEmotionMap(scatter) {
  let totalWeight = 0, rt = 0, lt = 0, lb = 0, rb = 0, sumX = 0, sumY = 0;
  const aspectWeight = {};
  for (const pt of scatter) {
    const pol = Number(pt.polarity || 0);
    const inten = Number(pt.intensity || 0);
    const w = Number(pt.votes || 1);
    totalWeight += w;
    sumX += pol * w;
    sumY += inten * w;
    aspectWeight[pt.aspect] = (aspectWeight[pt.aspect] || 0) + w;
    if (pol >= 0 && inten >= 2.5) rt += w;
    else if (pol < 0 && inten >= 2.5) lt += w;
    else if (pol < 0 && inten < 2.5) lb += w;
    else rb += w;
  }
  if (totalWeight === 0) totalWeight = 1;
  const cx = sumX / totalWeight;
  const cy = sumY / totalWeight;
  let dominantAspect = "剧情", maxC = 0;
  for (const [a, c] of Object.entries(aspectWeight)) {
    if (c > maxC) { maxC = c; dominantAspect = a; }
  }
  let centroidLabel;
  if (cx >= 0 && cy >= 2.5) centroidLabel = "整体偏正面且情绪强烈，观众热情高涨";
  else if (cx >= 0 && cy < 2.5) centroidLabel = "整体偏正面但情绪温和，观众认可度平稳";
  else if (cx < 0 && cy >= 2.5) centroidLabel = "整体偏负面且情绪强烈，争议较大";
  else centroidLabel = "整体偏负面但情绪克制，观众有保留意见";
  return {
    axis: { x: "差评 ← polarity → 好评", y: "平淡 → intensity → 强烈" },
    quadrants: [
      { name: "狂热好评", position: "rightTop", count: rt, percent: Math.round(rt * 100 / totalWeight), description: "高强度正面评价，观众对特定方面高度认可" },
      { name: "强烈差评", position: "leftTop", count: lt, percent: Math.round(lt * 100 / totalWeight), description: "高强度负面评价，观众对特定方面强烈不满" },
      { name: "差强人意", position: "leftBottom", count: lb, percent: Math.round(lb * 100 / totalWeight), description: "低强度负面评价，观众有保留意见但情绪不激烈" },
      { name: "比较推荐", position: "rightBottom", count: rb, percent: Math.round(rb * 100 / totalWeight), description: "低到中强度正面评价，观众温和认可" }
    ],
    centroid: { x: Math.round(cx * 100) / 100, y: Math.round(cy * 100) / 100, label: centroidLabel },
    summary: {
      dominantAspect,
      distributionSummary: `讨论最多的是「${dominantAspect}」，狂热好评${Math.round(rt * 100 / totalWeight)}%，强烈差评${Math.round(lt * 100 / totalWeight)}%`,
      interpretation: `情绪重心(${cx.toFixed(1)}, ${cy.toFixed(1)})，${centroidLabel}`
    }
  };
}

function quadrantFor(text, rating, x) {
  if (/压抑|惊悚|恐怖|窒息|黑暗|绝望|崩溃/.test(text)) return "压抑/惊悚";
  if (/燃|热血|激动|震撼|爽|炸裂|高能/.test(text)) return "热血/高燃";
  if (/孤独|克制|冷静|疏离|沉默|内敛/.test(text)) return "孤独/克制";
  if (/治愈|轻松|温暖|舒服|可爱|温馨/.test(text)) return "治愈/轻松";
  if (x < -0.2) return "压抑/惊悚";
  if (rating >= 4) return "热血/高燃";
  return "孤独/克制";
}

function buildRadarScores(comments, movie) {
  const rules = [
    ["剧本", /剧情|故事|叙事|反转|逻辑|情节|剧本|对白|铺垫|伏笔|结尾/g],
    ["导演", /导演|调度|镜头语言|风格|场面调度|执导/g],
    ["表演", /演技|表演|演员|角色|主角|配角|眼神/g],
    ["摄影", /画面|摄影|构图|光影|色彩|视觉|镜头/g],
    ["剪辑", /剪辑|转场|蒙太奇|节奏|拖沓/g],
    ["声音", /音乐|配乐|声音|音效|OST|BGM/g],
    ["美术", /美术|场景|服装|道具|布景|年代感|质感/g],
    ["特效", /特效|CG|视觉特效|后期/g],
    ["主题", /主题|阶层|社会|隐喻|思想|深度|内核|寓言/g],
    ["完成度", /完成度|经典|标杆|杰作|神作|过誉|败笔/g]
  ];
  const rating = Number(movie?.rating || 7.2);
  return rules.map(([label, regex], index) => {
    let hits = 0;
    let pos = 0;
    let neg = 0;
    comments.forEach((comment) => {
      const text = comment.text || "";
      const matches = text.match(regex);
      if (!matches) return;
      hits += matches.length;
      if (sentimentOf(comment) === "正面") pos += 1 + matches.length * 0.2;
      if (sentimentOf(comment) === "负面") neg += 1 + matches.length * 0.35;
    });
    let score = 5.2 + (rating - 7) * 0.45 + pos * 0.58 - neg * 1.0 + Math.min(1.5, hits * 0.20);
    if (hits === 0) score -= 1.2 + (index % 3) * 0.45;
    score = Math.max(2.8, Math.min(9.7, score));
    return [label, Math.round(score * 10) / 10];
  });
}

function findControversy(comments) {
  const negativeTexts = comments.filter((comment) => sentimentOf(comment) === "负面").map((comment) => comment.text || "").join(" ");
  if (/逻辑|情节|动机|漏洞/.test(negativeTexts)) return "部分评论质疑情节逻辑和人物动机";
  if (/拖沓|节奏|无聊/.test(negativeTexts)) return "争议集中在节奏拖沓和叙事密度";
  if (/刻意|说教|过誉/.test(negativeTexts)) return "部分观众认为表达刻意或存在过誉";
  return negativeTexts ? "评价存在分歧，但负面意见较分散" : "整体评价较一致，明显争议较少";
}

function buildLocalReview(comments, movie, keywords, positive, negative) {
  const total = comments.length || 1;
  const posRate = Math.round((positive / total) * 100);
  const negRate = Math.round((negative / total) * 100);
  const top = keywords.slice(0, 4).map(([word]) => word).join("、") || "核心段落";
  const samplePositive = comments.find((comment) => sentimentOf(comment) === "正面")?.text || "";
  const sampleNegative = comments.find((comment) => sentimentOf(comment) === "负面")?.text || "";
  return `《${movie.title}》在评论中呈现出的核心面貌，是一部被观众围绕“${top}”反复讨论的作品。正面评论约占${posRate}%，其中有观众提到“${samplePositive.slice(0, 38)}”，说明影片的有效记忆点不是抽象的好看，而是落在具体段落、情绪释放或叙事设计上。负面评论约占${negRate}%，${sampleNegative ? `也有人指出“${sampleNegative.slice(0, 36)}”，暴露出部分观众对逻辑、节奏或表达方式的保留。` : "目前明显批评较少，争议主要来自不同观众对题材和表达强度的接受度。"}横向看，本片更适合和同题材高口碑作品比较其叙事密度、情绪落点和主题表达，而不是只看评分高低。综合而言，它适合愿意从评论细节中捕捉主题和形式关系的观众；独特价值在于让观众讨论具体场景和手法，而不只是停留在泛泛的类型评价。`;
}

function renderKeywordCloud() {
  const cloud = $("#keyword-cloud");
  const keywords = [...(currentAnalysis.keywords || [])]
    .sort((a, b) => (Number(b[1]) || 0) - (Number(a[1]) || 0));
  if (!keywords.length) {
    cloud.innerHTML = `<span style="left:50%;top:50%;font-size:15px">等待评论分析</span>`;
    return;
  }

  const maxKeyword = Math.max(1, ...keywords.map(([, count]) => Number(count) || 1));
  const coreSlots = [
    [50, 47, -3], [26, 37, 6], [74, 37, -7], [31, 67, -5], [69, 67, 5]
  ];
  const outerSlots = [
    [12, 14, -6], [30, 15, 5], [50, 13, -2], [70, 15, 7], [88, 14, -5],
    [12, 30, 4], [88, 30, -7], [12, 47, -4], [88, 47, 6],
    [12, 64, 7], [88, 64, -5], [12, 81, -6], [30, 84, 4], [50, 87, -3],
    [70, 84, 6], [88, 81, -4], [22, 23, 3], [78, 23, -5],
    [22, 76, -4], [78, 76, 5], [42, 27, 6], [58, 27, -6],
    [42, 77, -5], [58, 77, 4], [38, 58, 7], [62, 58, -7],
    [38, 39, -5], [62, 39, 5], [24, 54, 4], [76, 54, -4]
  ];
  const placed = [];
  cloud.innerHTML = "";

  keywords.forEach(([word, count], index) => {
    const item = document.createElement("span");
    const size = 14 + Math.round((Number(count) || 1) / maxKeyword * 22);
    const opacity = 0.74 + Math.min(0.26, (Number(count) || 1) / maxKeyword * 0.26);
    item.textContent = word;
    item.style.cssText = `font-size:${size}px;opacity:${opacity};visibility:hidden;z-index:${index < 5 ? 105 - index : 20};--kw-opacity:${opacity};animation-delay:${index * 45}ms`;
    cloud.appendChild(item);

    const width = item.offsetWidth;
    const height = item.offsetHeight;
    const orderedOuterSlots = outerSlots.map((slot, offset) => outerSlots[(index + offset) % outerSlots.length]);
    const candidates = index < coreSlots.length ? [coreSlots[index], ...orderedOuterSlots] : orderedOuterSlots;
    const position = candidates.find(([left, top]) => keywordRectFits(left, top, width, height, cloud, placed, 8));
    if (!position) {
      item.remove();
      return;
    }

    const [left, top, rotate] = position;
    placed.push(keywordRect(left, top, width, height, cloud));
    item.style.left = `${left}%`;
    item.style.top = `${top}%`;
    item.style.setProperty("--rotate", `${rotate}deg`);
    item.style.visibility = "visible";
  });
}

function keywordRect(leftPercent, topPercent, width, height, container) {
  const edge = 8;
  const centerX = Math.max(width / 2 + edge, Math.min(container.clientWidth - width / 2 - edge, container.clientWidth * leftPercent / 100));
  const centerY = Math.max(height / 2 + edge, Math.min(container.clientHeight - height / 2 - edge, container.clientHeight * topPercent / 100));
  return { left: centerX - width / 2, top: centerY - height / 2, right: centerX + width / 2, bottom: centerY + height / 2 };
}

function keywordRectFits(leftPercent, topPercent, width, height, container, placed, gap) {
  const rect = keywordRect(leftPercent, topPercent, width, height, container);
  return !placed.some((other) => rect.left < other.right + gap && rect.right + gap > other.left && rect.top < other.bottom + gap && rect.bottom + gap > other.top);
}

function renderCharts() {
  // Trigger panel refresh animation
  document.querySelectorAll(".analysis-panel").forEach(panel => {
    panel.classList.remove("refresh");
    void panel.offsetWidth;
    panel.classList.add("refresh");
  });
  renderKeywordCloud();

  $("#rating-bars").innerHTML = renderRatingColumnChart(currentAnalysis.ratingDistribution);
  $("#comparison-bars").innerHTML = currentAnalysis.comparison.map(([label, value], idx) => `
    <div class="bar-row" style="animation-delay:${idx * 50}ms">
      <span>${escapeHtml(label)}</span>
      <span class="bar-track"><span class="bar-fill" style="width:${value}%"></span></span>
      <span>${value}</span>
    </div>
  `).join("");

  // ── 观众情绪分布图（方面级散点）──
  // 如果散点太少（AI返回不足或无AI），用本地聚合引擎补充
  let scatterData = Array.isArray(currentAnalysis.scatter) ? currentAnalysis.scatter : [];
  let emotionMapData = currentAnalysis.emotionMap || {};
  if (scatterData.length < 15 && currentComments.length > 0) {
    const localScatter = buildLocalScatter(currentComments);
    if (localScatter.length > scatterData.length) {
      scatterData = localScatter;
      emotionMapData = buildLocalEmotionMap(localScatter);
    }
  }
  const em = emotionMapData;
  const quads = em.quadrants || [];
  const centroid = em.centroid || { x: 0, y: 0 };
  const emSummary = em.summary || {};

  // 方面颜色映射
  const aspectColors = {
    "剧情": "#e74c3c", "演技": "#e67e22", "视听": "#00bcd4", "节奏": "#2ecc71",
    "主题": "#9b59b6", "配乐": "#3498db", "美术": "#e91e63", "结尾": "#f1c40f"
  };

  // 坐标映射: polarity(-5~5)→x(34~326), intensity(0~5)→y(188~24)
  const polToX = (p) => 180 + p * 29.2;
  const intToY = (i) => 188 - i * 32.8;

  // 象限背景色块
  const quadRects = [
    `<rect x="34" y="24" width="146" height="82" fill="rgba(220,105,102,0.12)"/>`,   // leftTop=强烈差评
    `<rect x="180" y="24" width="146" height="82" fill="rgba(220,170,55,0.12)"/>`,    // rightTop=狂热好评
    `<rect x="34" y="106" width="146" height="82" fill="rgba(144,143,139,0.08)"/>`,  // leftBottom=差强人意
    `<rect x="180" y="106" width="146" height="82" fill="rgba(82,196,26,0.08)"/>`,   // rightBottom=比较推荐
  ].join("");

  // 象限标签
  const quadLabels = quads.map(q => {
    const positions = { leftTop: [38, 38], rightTop: [250, 38], leftBottom: [38, 184], rightBottom: [250, 184] };
    const colors = { leftTop: "#dc6966", rightTop: "#dcaa37", leftBottom: "#908f8b", rightBottom: "#52c41a" };
    const [x, y] = positions[q.position] || [38, 38];
    const color = colors[q.position] || "#908f8b";
    return `<text x="${x}" y="${y}" fill="${color}" font-size="11" font-weight="700">${escapeHtml(q.name)} ${q.count}(${q.percent}%)</text>`;
  }).join("");

  // 方面级散点（点大小用sqrt缩放，votes=该聚类的真实评论数）
  const points = scatterData.map((point, idx) => {
    const cx = polToX(point.polarity || 0);
    const cy = intToY(point.intensity || 0);
    const aspect = point.aspect || "剧情";
    const fill = aspectColors[aspect] || "#908f8b";
    const votes = Math.max(1, Number(point.votes || 1));
    // sqrt缩放：votes=1→r≈11, votes=5→r≈17, votes=10→r≈22, votes=20→r≈28
    const r = Math.max(8, Math.min(28, 6 + Math.sqrt(votes) * 5));
    const opacity = Math.max(0.55, Math.min(0.9, 0.55 + votes * 0.025));
    const text = point.text ? escapeHtml(point.text.substring(0, 40)) : "";
    const strokeW = votes >= 5 ? 2 : 1;
    const showLabel = votes >= 3 && r >= 12;
    return `<circle cx="${cx.toFixed(1)}" cy="${cy.toFixed(1)}" r="${r.toFixed(1)}" fill="${fill}" fill-opacity="${opacity}" stroke="${fill}" stroke-opacity="0.4" stroke-width="${strokeW}" style="animation-delay:${idx * 12}ms"><title>${escapeHtml(aspect)}｜polarity=${point.polarity}｜intensity=${point.intensity}｜${votes}条相似评论｜${text}</title></circle>${showLabel ? `<text x="${cx.toFixed(1)}" y="${(cy + 3).toFixed(1)}" text-anchor="middle" fill="#fff" font-size="9" font-weight="700" pointer-events="none">${votes}</text>` : ""}`;
  }).join("");

  // 情绪重心
  const ccx = polToX(centroid.x || 0);
  const ccy = intToY(centroid.y || 0);
  const centroidMarker = `
    <line x1="${(ccx - 10).toFixed(1)}" y1="${ccy.toFixed(1)}" x2="${(ccx + 10).toFixed(1)}" y2="${ccy.toFixed(1)}" stroke="#fffaf4" stroke-width="2"/>
    <line x1="${ccx.toFixed(1)}" y1="${(ccy - 10).toFixed(1)}" x2="${ccx.toFixed(1)}" y2="${(ccy + 10).toFixed(1)}" stroke="#fffaf4" stroke-width="2"/>
    <circle cx="${ccx.toFixed(1)}" cy="${ccy.toFixed(1)}" r="5" fill="none" stroke="#fffaf4" stroke-width="2"/>
  `;

  $("#scatter-chart").innerHTML = `
    <div class="emotion-map">
      <svg viewBox="0 0 360 220" role="img" aria-label="观众情绪分布图">
        <rect x="0" y="0" width="360" height="220" rx="8" fill="#30302e"/>
        ${quadRects}
        <line x1="180" y1="24" x2="180" y2="188" stroke="#5e5d59" stroke-dasharray="4,3"/>
        <line x1="34" y1="106" x2="326" y2="106" stroke="#5e5d59" stroke-dasharray="4,3"/>
        ${quadLabels}
        <text x="180" y="18" text-anchor="middle" fill="#5e5d59" font-size="9">差评 polarity 好评</text>
        <text x="10" y="106" fill="#5e5d59" font-size="9" transform="rotate(-90 10 106)">强烈 intensity 平淡</text>
        ${points}
        ${centroidMarker}
        <text x="${(ccx + 8).toFixed(1)}" y="${(ccy + 4).toFixed(1)}" fill="#fffaf4" font-size="9" font-weight="700">重心</text>
      </svg>
      <div class="emotion-legend">
        ${Object.entries(aspectColors).map(([a, c]) => `<span><span class="dot" style="background:${c}"></span>${a}</span>`).join("")}
        <span>点大小=评论数</span>
        <span>✛=情绪重心</span>
      </div>
      ${emSummary.distributionSummary ? `<div class="emotion-summary"><strong>主导方面：</strong>${escapeHtml(emSummary.dominantAspect || "")} — ${escapeHtml(emSummary.distributionSummary)}。${escapeHtml(emSummary.interpretation || "")}</div>` : ""}
    </div>
  `;

  const center = 110;
  const radius = 78;
  const radarPairs = currentAnalysis.radar.map((item, index) => Array.isArray(item) ? item : [radarLabels[index], item]);
  const radarScores = radarPairs.map(item => Number(item[1]) || 0);
  const polygon = radarScores.map((score, index) => {
    const angle = (Math.PI * 2 * index) / radarLabels.length - Math.PI / 2;
    const distance = radius * (score / 10);
    return `${center + Math.cos(angle) * distance},${center + Math.sin(angle) * distance}`;
  }).join(" ");
  const axes = radarLabels.map((label, index) => {
    const angle = (Math.PI * 2 * index) / radarLabels.length - Math.PI / 2;
    const x = center + Math.cos(angle) * (radius + 18);
    const y = center + Math.sin(angle) * (radius + 18);
    return `<line x1="${center}" y1="${center}" x2="${center + Math.cos(angle) * radius}" y2="${center + Math.sin(angle) * radius}" stroke="#e8e6dc"/><text x="${x}" y="${y}" font-size="10" text-anchor="middle" fill="#5e5d59">${label}</text>`;
  }).join("");
  $("#radar-chart").innerHTML = `
    <div class="radar-layout">
      <svg viewBox="0 0 220 220" role="img" aria-label="十维雷达图">
        <circle cx="${center}" cy="${center}" r="78" fill="none" stroke="#e8e6dc"/>
        <circle cx="${center}" cy="${center}" r="52" fill="none" stroke="#e8e6dc"/>
        <circle cx="${center}" cy="${center}" r="26" fill="none" stroke="#e8e6dc"/>
        ${axes}
        <polygon points="${polygon}" fill="rgba(217,119,87,.28)" stroke="#d97757" stroke-width="2"/>
      </svg>
      <div class="radar-score-list">
        ${radarPairs.map(([label, score], idx) => `
          <div class="radar-score-row" style="animation-delay:${idx * 50}ms">
            <span>${escapeHtml(label)}</span>
            <span class="bar-track"><span class="bar-fill" style="width:${Math.max(0, Math.min(100, Number(score) * 10))}%"></span></span>
            <strong>${Number(score).toFixed(1)}</strong>
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderRatingColumnChart(rows = []) {
  const normalized = ["1星", "2星", "3星", "4星", "5星"].map((label) => {
    const found = rows.find(([name]) => name === label);
    return [label, found ? Number(found[1]) || 0 : 0];
  });
  return `
    <div class="rating-column-chart" role="img" aria-label="星级分布柱状图">
      ${normalized.map(([label, value], idx) => `
        <div class="rating-column" style="animation-delay:${idx * 80}ms">
          <span class="rating-column-track"><span class="rating-column-fill" style="height:${Math.max(2, value)}%"></span></span>
          <span class="rating-column-value">${value}%</span>
          <span class="rating-column-label">${label}</span>
        </div>
      `).join("")}
    </div>
  `;
}

function renderReview() {
  const reviewEl = $("#ai-review");
  const tagEl = $("#review-tag");
  const review = currentAnalysis.review;
  if (review) {
    const isAi = currentAnalysis.engine && currentAnalysis.engine.startsWith("ai");
    tagEl.textContent = isAi ? "AI评析" : "本地评析";
    tagEl.className = isAi ? "status-tag running" : "status-tag neutral";

    reviewEl.innerHTML = formatReviewHtml(review);
  } else {
    reviewEl.innerHTML = `<p class="muted">点击「获取并 AI 分析」后，此处将显示基于评论数据的客观评析。</p>`;
    tagEl.textContent = "待生成";
    tagEl.className = "status-tag neutral";
  }
}

function renderReviewObject(review, el) {
  el.innerHTML = formatReviewHtml(review);
}

function renderComments() {
  const keyword = $("#comment-keyword").value.trim();
  const filtered = currentComments.filter((comment) => {
    const bySentiment = activeSentiment === "all" || comment.sentiment === activeSentiment;
    const byKeyword = !keyword || [comment.text, comment.aspect, comment.quadrant, comment.user].some((value) => String(value || "").includes(keyword));
    return bySentiment && byKeyword;
  });
  $("#comment-list").innerHTML = filtered.map((comment, idx) => `
    <article class="comment-item" style="animation-delay:${idx * 40}ms">
      <p>${escapeHtml(comment.text)}</p>
      <div class="comment-meta">
        <span>${escapeHtml(comment.sentiment)}</span>
        <span>${escapeHtml(comment.star)}</span>
        <span>${escapeHtml(comment.aspect)}</span>
        <span>${escapeHtml(comment.quadrant)}</span>
        <span>${escapeHtml(comment.user || "")}</span>
      </div>
    </article>
  `).join("") || `<article class="comment-item"><p>没有匹配评论。</p></article>`;
}

function renderHistory() {
  const grid = $("#history-grid");
  const items = history;
  if (!items.length) {
    grid.innerHTML = `<article class="comment-item"><p>还没有历史记录。完成一次搜索分析后，可以点击“保存到历史”。</p></article>`;
    return;
  }
  grid.innerHTML = items.map((movie, idx) => {
    const image = posterSrc(movie);
    return `
      <article class="history-item" style="animation-delay:${idx * 60}ms">
        <div class="mini-poster">${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(movie.title)}海报" onerror="this.style.display='none'">` : ""}<span>${escapeHtml(movie.title)}</span></div>
        <h3>${escapeHtml(movie.title)}</h3>
        <p class="muted">已保存搜索、详情、评论与分析结果，二次进入优先复用缓存。</p>
        <button class="secondary-action" type="button" data-history-id="${escapeHtml(movie.id)}">打开分析</button>
      </article>
    `;
  }).join("");
}

function openHistory(id) {
  const item = history.find((movie) => movie.id === id);
  if (!item) return;
  currentMovie = item;
  currentComments = Array.isArray(item.comments) ? item.comments : [];
  currentAnalysis = normalizeAnalysis(item.analysis || buildAnalysis(currentComments, currentMovie));
  renderDetail(currentMovie);
  showView("detail");
  setTask([
    "> history restored",
    `> movie: ${currentMovie.title}`,
    `> comments: ${currentComments.length}`,
    `> analysis: ${currentAnalysis.engine || "local"}`
  ], "history ready");
}

function exportComments() {
  if (!currentMovie || !currentComments.length) {
    appendTask("> export skipped: 当前没有可导出的评论", "export empty");
    return;
  }
  const header = ["movie", "commentId", "user", "ratingValue", "star", "sentiment", "aspect", "quadrant", "voteCount", "createdAt", "text"];
  const rows = currentComments.map((comment) => [
    currentMovie.title,
    comment.id || "",
    comment.user || "",
    comment.ratingValue || "",
    comment.star || "",
    comment.sentiment || "",
    comment.aspect || "",
    comment.quadrant || "",
    comment.voteCount || "",
    comment.createdAt || "",
    (comment.text || "").replace(/\n/g, " ")
  ]);
  const csv = [header, ...rows]
    .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(","))
    .join("\n");
  const blob = new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${(currentMovie.title || "FilmOracle").replace(/[\\/:*?"<>|]/g, "_")}_comments.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  appendTask(`> exported comments: ${currentComments.length}`, "exported");
}

function exportPdfReport() {
  if (!currentMovie) {
    showToast("请先选择电影");
    return;
  }
  const report = window.open("", "_blank");
  if (!report) {
    showToast("请允许弹窗以导出报告");
    return;
  }
  const scatterSvg = $("#scatter-chart").innerHTML;
  const radarSvg = $("#radar-chart").querySelector("svg")?.outerHTML || "";
  const keywordHtml = $("#keyword-cloud").innerHTML;
  const ratingHtml = $("#rating-bars").innerHTML;
  const comparisonHtml = $("#comparison-bars").innerHTML;
  const reviewHtml = formatReviewHtml(currentAnalysis.review);
  const topComments = currentComments.slice(0, 10).map((c, i) =>
    `<div class="comment"><span class="star">${c.star || ""}</span><span class="sentiment ${c.sentiment || ""}">${c.sentiment || ""}</span><p>${escapeHtml(c.text || "")}</p><span class="user">— ${escapeHtml(c.user || "豆瓣用户")}</span></div>`
  ).join("");
  const facts = [
    currentMovie.year, currentMovie.director, currentMovie.genre, currentMovie.region
  ].filter(Boolean).map(f => escapeHtml(f)).join(" / ");

  report.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${escapeHtml(currentMovie.title)} — FilmOracle 分析报告</title>
  <style>
    body { font-family: system-ui, "Microsoft YaHei", sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; color: #141413; }
    h1 { font-size: 28px; margin: 0 0 4px; }
    h2 { font-size: 18px; margin: 24px 0 8px; border-bottom: 2px solid #d97757; padding-bottom: 4px; }
    .meta { color: #5e5d59; font-size: 14px; margin-bottom: 16px; }
    .rating { font-size: 36px; font-weight: 800; color: #d97757; }
    .section { margin: 20px 0; }
    .keyword-cloud { position: relative; min-height: 120px; }
    .keyword-cloud span { display: inline-block; margin: 4px; padding: 4px 8px; border-radius: 6px; background: #fff6f0; color: #b3573e; font-weight: 700; }
    svg { max-width: 100%; height: auto; }
    .comment { padding: 10px; border: 1px solid #e8e6dc; border-radius: 8px; margin: 8px 0; }
    .comment .star { color: #f2a52b; font-weight: 700; margin-right: 8px; }
    .comment .sentiment { font-size: 12px; padding: 2px 6px; border-radius: 4px; margin-right: 8px; }
    .comment .sentiment.正面 { background: #f6ffed; color: #235a13; }
    .comment .sentiment.负面 { background: #fff1f0; color: #a8071a; }
    .comment .sentiment.中性 { background: #f0f0f0; color: #5e5d59; }
    .comment p { margin: 6px 0; line-height: 1.6; }
    .comment .user { color: #908f8b; font-size: 12px; }
    .review { line-height: 1.8; text-indent: 2em; font-size: 14px; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    @media print { body { max-width: none; } }
  </style></head><body>
  <h1>${escapeHtml(currentMovie.title)}</h1>
  <div class="meta">${facts}</div>
  ${currentMovie.rating ? `<div class="rating">${escapeHtml(currentMovie.rating)}</div>` : ""}
  ${currentMovie.summary ? `<p style="line-height:1.6;color:#5e5d59;">${escapeHtml(currentMovie.summary)}</p>` : ""}
  <h2>观众情绪分布图</h2>
  <div class="section">${scatterSvg}</div>
  <h2>十维雷达图</h2>
  <div class="section">${radarSvg}</div>
  <div class="grid">
    <div><h2>好评关键词</h2><div class="keyword-cloud">${keywordHtml}</div></div>
    <div><h2>星级分布</h2>${ratingHtml}</div>
  </div>
  <h2>同类型对比</h2>
  <div class="section">${comparisonHtml}</div>
  <h2>综合评析</h2>
  <div class="review">${reviewHtml}</div>
  <h2>代表评论（前10条）</h2>
  ${topComments || "<p>暂无评论数据</p>"}
  <p style="text-align:center;color:#908f8b;margin-top:24px;">Generated by FilmOracle — AI Movie Review Intelligence</p>
  </body></html>`);
  report.document.close();
  setTimeout(() => report.print(), 500);
  appendTask(`> PDF report generated for: ${currentMovie.title}`, "report exported");
}

async function performSearch(query) {
  if (!requireAuth()) return;
  const q = query || "";
  renderResults(q, [], { source: "loading" });
  showView("results");
  $("#result-list").innerHTML = `<div class="loading-indicator">正在搜索「${escapeHtml(q)}」...</div>`;
  const data = await apiGet(`/api/search?q=${encodeURIComponent(q || "寄生虫")}`, {
    ok: false,
    movies: [],
    meta: { source: "fallback", error: "本地代理不可用" }
  });
  renderResults(query, data.movies || [], data.meta || {});
}

async function openMovie(id) {
  if (!requireAuth()) return;
  const local = currentSearchResults.find((movie) => movie.id === id) || fallbackMovies.find((movie) => movie.id === id) || null;
  currentComments = [];
  currentAnalysis = emptyAnalysis();
  renderDetail(local);
  showView("detail");
  setTask(["> 正在同步豆瓣详情...", `> movieId: ${id}`], "detail loading");
  const data = await apiGet(`/api/movie/${encodeURIComponent(id)}`, {
    ok: false,
    movie: local || { id, title: "详情获取失败" },
    meta: { source: "fallback", error: "本地代理不可用" }
  });
  const movie = { ...local, ...(data.movie || {}) };
  if ((!movie.rating || movie.rating === "0" || movie.rating === "-") && local?.rating) movie.rating = local.rating;
  if ((!movie.votes || movie.votes === "0") && local?.votes) movie.votes = local.votes;
  movie.source = data.meta?.source || movie.source || "Douban";
  currentMovie = movie;
  renderDetail(movie);
  setTask([
    "> 详情同步完成",
    `> source: ${data.meta?.source || "unknown"}`,
    data.meta?.error ? `> fallback reason: ${data.meta.error}` : "> status: ok"
  ], "detail ready");
  // 自动保存浏览历史
  if (currentUser) {
    try {
      await fetch("/api/history", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(movie)
      });
    } catch {}
  }
}

async function loadCommentsAndAnalyze(runAnalysis = false, useLocalRules = false) {
  if (!requireAuth()) return;
  if (!currentMovie) {
    setTask(["> 请先搜索并选择一部电影，或从导入评论入口创建来源电影。"], "no movie");
    return;
  }
  const settings = getSettings();
  const commentCount = settings.commentCount || 100;
  startTaskTimer(runAnalysis ? "AI 分析中" : "本地分析中");

  // ─── Step 1: 获取评论 ───
  setTask([
    "> === 任务启动 ===",
    `> 目标电影: ${currentMovie.title}`,
    `> 请求评论数量: ${commentCount} 条`,
    "> 正在连接豆瓣 API..."
  ], "crawling");
  appendTask("> [1/3] 爬取短评中...");

  const data = await apiGet(`/api/movie/${encodeURIComponent(currentMovie.id)}/interests?count=${commentCount}`, {
    ok: false,
    comments: fallbackComments,
    meta: { source: "fallback", error: "本地代理不可用", count: fallbackComments.length }
  });

  currentComments = data.comments?.length ? data.comments : fallbackComments;
  const dataSource = data.meta?.source || "unknown";
  appendTask(`> [1/3] 爬取完成: 获取 ${currentComments.length} 条评论 (来源: ${dataSource})`, "comments ready");
  if (data.meta?.error) appendTask(`> ! 豆瓣 API 不可用: ${data.meta.error}`);

  // ─── Step 2: 分析评论 ───
  if (useLocalRules) {
    appendTask(`> [2/3] 使用本地规则引擎分析全部 ${currentComments.length} 条评论...`, "filtering");
    currentAnalysis = buildAnalysis(currentComments, currentMovie);
    currentComments = applyAnalysisToComments(currentComments, currentAnalysis);
    appendTask(`> [2/3] 本地规则分析完成: ${currentComments.length} / ${currentComments.length} 条评论`, "analysis ready");
  } else if (runAnalysis) {
    const hasApiKey = settings.apiKey && settings.apiKey.trim() && !settings.apiKey.startsWith("sk-***");
    appendTask(`> [2/3] 筛选有价值评论...`, "filtering");

    if (hasApiKey) {
      appendTask(`> [2/3] 使用 AI 分析 (${settings.aiModel})`);
      appendTask(`> > 发送 ${currentComments.length} 条评论到 AI...`);
      appendTask(`> > 提示词长度: ${settings.aiPrompt.length} 字符`);
    } else {
      appendTask(`> [2/3] 未配置 API Key, 使用本地规则引擎分析`);
    }

    try {
      const resp = await fetch("/api/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          movie: currentMovie,
          comments: currentComments,
          apiKey: settings.apiKey || "",
          model: settings.aiModel || "moonshot-v1-8k",
          prompt: settings.aiPrompt || DEFAULT_PROMPT
        })
      });
      const analysisData = await resp.json();

      if (analysisData.ok && analysisData.analysis) {
        currentAnalysis = normalizeAnalysis(analysisData.analysis);
        if (analysisData.comments) currentComments = analysisData.comments;
        currentComments = applyAnalysisToComments(currentComments, currentAnalysis);
        const engine = analysisData.meta?.engine || "unknown";
        const analyzed = analysisData.meta?.analyzedComments || currentComments.length;
        appendTask(`> [2/3] 分析完成! 引擎: ${engine}`, "analysis ready");
        if (analysisData.meta?.cacheHit) {
          appendTask(`> > 已恢复数据库缓存结果，未重复调用 AI${analysisData.meta?.cachedAt ? `（缓存时间: ${analysisData.meta.cachedAt}）` : ""}`);
        }
        appendTask(`> > 分析评论数: ${analyzed} / 总评论数: ${currentComments.length}`);
        if (currentAnalysis.review) {
          appendTask(`> > AI 评析已生成: ${currentAnalysis.review.length} 字`);
        }
      } else {
        throw new Error(analysisData.error || "analysis failed");
      }
    } catch (e) {
      appendTask(`> [2/3] 分析失败: ${e.message}, 使用本地回退`, "fallback");
      currentAnalysis = buildAnalysis(currentComments, currentMovie);
    }
  } else {
    currentComments = currentComments.map(c => ({...c, sentiment: c.sentiment || sentimentOf(c)}));
    currentAnalysis = emptyAnalysis();
    appendTask(`> [2/3] 评论获取完成（未执行分析）`);
    appendTask(`> > 点击「获取并 AI 分析」生成图表和评析`);
  }

  // ─── Step 3: 渲染结果 ───
  appendTask("> [3/3] 渲染图表和评析...", "rendering");
  renderCharts();
  renderComments();
  renderReview();
  renderDetail(currentMovie);
  appendTask("> === 任务完成 ===", "done");
  stopTaskTimer("done");
}

async function importMovieFromForm(form) {
  if (!requireAuth()) return;
  const formData = new FormData(form);
  const title = (formData.get("title") || "").trim();
  const file = formData.get("file");
  if (!title || !(file instanceof File) || file.size === 0) {
    showToast(!title ? "请填写电影名称（必填）" : "请选择 CSV 或 TXT 评论文件");
    return;
  }
  let importedMovie = {
    id: `import-${Date.now()}`,
    title,
    year: formData.get("year") || "",
    director: formData.get("director") || "",
    cast: formData.get("cast") || "",
    genre: formData.get("genre") || "",
    region: formData.get("region") || "",
    language: formData.get("language") || "",
    duration: formData.get("duration") || "",
    summary: formData.get("summary") || "",
    source: formData.get("source") || "导入评论文件"
  };
  setTask(["> === 导入评论分析 ===", `> 电影: ${title}`, `> 文件: ${file.name}`, "> 正在解析导入评论..."], "importing");

  importedMovie = await enrichImportedMovieSummary(importedMovie);
  formData.set("movieId", importedMovie.id);
  formData.set("movieTitle", importedMovie.title);
  formData.set("summary", importedMovie.summary || "");

  try {
    const response = await fetch("/api/comments/import", { method: "POST", body: formData });
    const data = await response.json();
    if (!response.ok || !data.ok || !Array.isArray(data.comments) || data.comments.length === 0) {
      throw new Error(data.error || "文件中没有可解析的评论");
    }
    currentMovie = { ...importedMovie, ...(data.movie || {}), source: "导入评论文件" };
    currentComments = data.comments;
    currentAnalysis = emptyAnalysis();
    appendTask(`> 已解析并保存 ${currentComments.length} 条导入评论`, "import ready");
    renderDetail(currentMovie);
    showView("detail");
    appendTask("> 图表已恢复初始状态，请选择「本地分析」或「AI 分析」", "import ready");
    appendTask("> === 导入完成 ===", "done");
    showToast(`已导入 ${currentComments.length} 条评论`);
  } catch (error) {
    appendTask(`> 导入失败: ${error.message}`, "import failed");
    showToast(`导入失败：${error.message}`);
  }
}

function formatReviewHtml(review) {
  if (!review) return `<p class="muted">暂无评析</p>`;
  let normalized = review;
  if (typeof normalized === "string") {
    try {
      const parsed = JSON.parse(normalized);
      if (parsed && typeof parsed === "object") normalized = parsed;
    } catch {
      return `<p>${escapeHtml(normalized)}</p>`;
    }
  }
  if (!normalized || typeof normalized !== "object") return `<p>${escapeHtml(normalized)}</p>`;
  const highlights = Array.isArray(normalized.highlightPoints) ? normalized.highlightPoints : [];
  const weaknesses = Array.isArray(normalized.weaknesses) ? normalized.weaknesses : [];
  return `
    ${normalized.overallReception ? `<p style="font-weight:700;color:var(--accent-active);margin-bottom:10px;">${escapeHtml(normalized.overallReception)}</p>` : ""}
    ${highlights.length ? `<p style="margin:8px 0;"><strong style="color:var(--positive);">出彩：</strong>${highlights.map((item) => escapeHtml(item)).join("；")}</p>` : ""}
    ${weaknesses.length ? `<p style="margin:8px 0;"><strong style="color:var(--negative);">不足：</strong>${weaknesses.map((item) => escapeHtml(item)).join("；")}</p>` : ""}
    ${normalized.finalJudgement ? `<p style="margin:8px 0;font-style:italic;color:var(--muted);">${escapeHtml(normalized.finalJudgement)}</p>` : ""}
    ${normalized.fullText ? `<p style="margin-top:12px;text-indent:2em;line-height:1.8;">${escapeHtml(normalized.fullText)}</p>` : ""}
  `;
}

async function enrichImportedMovieSummary(movie) {
  if (!movie.title) return movie;
  const search = await apiGet(`/api/search?q=${encodeURIComponent(movie.title)}`, null);
  const candidates = Array.isArray(search?.movies) ? search.movies : [];
  const matched = candidates.find((item) => item.title === movie.title) || candidates[0];
  if (!matched?.id) return movie;
  const detail = await apiGet(`/api/movie/${encodeURIComponent(matched.id)}`, null);
  const remote = detail?.movie;
  if (!remote) return movie;
  return {
    ...movie,
    id: remote.id || matched.id || movie.id,
    year: movie.year || remote.year || "",
    director: movie.director || remote.director || "",
    cast: movie.cast || remote.cast || "",
    genre: movie.genre || remote.genre || "",
    region: movie.region || remote.region || "",
    language: movie.language || remote.language || "",
    duration: movie.duration || remote.duration || "",
    summary: movie.summary || remote.summary || "",
    posterUrl: remote.posterUrl || matched.posterUrl || ""
  };
}

async function analyzeImportedComments(analysisMode) {
  if (!currentComments.length) {
    showToast("当前没有可分析的导入评论");
    return;
  }
  startTaskTimer(analysisMode === "local" ? "本地分析中" : "AI 分析中");
  setTask([
    "> === 导入评论分析 ===",
    `> 电影: ${currentMovie?.title || "未命名电影"}`,
    `> 评论来源: 已导入文件（${currentComments.length} 条）`
  ], analysisMode === "local" ? "local analysis" : "ai analysis");
  if (analysisMode === "local") {
    appendTask(`> 正在对 ${currentComments.length} 条导入评论执行本地规则分析...`, "local analysis");
    currentAnalysis = buildAnalysis(currentComments, currentMovie);
    currentComments = applyAnalysisToComments(currentComments, currentAnalysis);
    appendTask(`> 本地规则分析完成: ${currentComments.length} 条导入评论`, "analysis ready");
    renderDetail(currentMovie);
    appendTask("> === 分析完成 ===", "done");
    stopTaskTimer("done");
    return;
  }

  try {
    appendTask(`> 正在将 ${currentComments.length} 条导入评论发送 AI 分析...`, "ai analysis");
    const response = await fetch("/api/analyze", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ movie: currentMovie, comments: currentComments })
    });
    const data = await response.json();
    if (!response.ok || !data.ok || !data.analysis) throw new Error(data.error || "AI 分析失败");
    currentAnalysis = normalizeAnalysis(data.analysis);
    currentComments = applyAnalysisToComments(data.comments || currentComments, currentAnalysis);
    appendTask(`> AI 分析完成: ${data.meta?.engine || "AI"}`, "analysis ready");
    if (data.meta?.cacheHit) appendTask("> 已恢复数据库缓存结果，未重复调用 AI");
    renderDetail(currentMovie);
    appendTask("> === 分析完成 ===", "done");
    stopTaskTimer("done");
  } catch (error) {
    appendTask(`> AI 分析失败: ${error.message}`, "failed");
    stopTaskTimer("failed");
    showToast(`AI 分析失败: ${error.message}`);
  }
}

// 解析评论文件（CSV/TXT）
function parseCommentFile(text, fileName) {
  const lines = text.split(/\r?\n/).filter(l => l.trim());
  if (lines.length === 0) return [];

  const comments = [];
  const isCsv = fileName.toLowerCase().endsWith(".csv");

  // 检测CSV头部
  let startIdx = 0;
  let textCol = 0, ratingCol = -1, userCol = -1;
  if (isCsv && lines[0].includes(",")) {
    const header = lines[0].toLowerCase().split(",");
    for (let i = 0; i < header.length; i++) {
      if (header[i].includes("text") || header[i].includes("comment") || header[i].includes("评")) textCol = i;
      if (header[i].includes("rating") || header[i].includes("star") || header[i].includes("星")) ratingCol = i;
      if (header[i].includes("user") || header[i].includes("name") || header[i].includes("用户")) userCol = i;
    }
    // 如果第一行看起来是数据而非头部
    if (!header[textCol].includes("text") && !header[textCol].includes("comment")) {
      startIdx = 0;
    } else {
      startIdx = 1;
    }
  }

  for (let i = startIdx; i < lines.length; i++) {
    let parts, commentText, rating = 3, user = "导入用户";
    if (isCsv) {
      // 简单CSV解析（不处理引号内逗号）
      parts = lines[i].split(",");
      commentText = (parts[textCol] || "").trim().replace(/^"|"$/g, "");
      if (ratingCol >= 0 && parts[ratingCol]) {
        const r = parseInt(parts[ratingCol].replace(/[^0-5]/g, ""));
        if (r >= 1 && r <= 5) rating = r;
      }
      if (userCol >= 0 && parts[userCol]) user = parts[userCol].trim().replace(/^"|"$/g, "");
    } else {
      // TXT：整行为评论内容
      commentText = lines[i].trim();
      // 尝试从行首提取星级 如 "5星 好看"
      const starMatch = commentText.match(/^([1-5])\s*星\s*(.*)/);
      if (starMatch) { rating = parseInt(starMatch[1]); commentText = starMatch[2]; }
    }
    if (commentText.length >= 2) {
      comments.push({
        id: `import-comment-${i}`,
        text: commentText,
        ratingValue: rating,
        star: rating + "星",
        user,
        voteCount: 0,
        createdAt: null
      });
    }
  }
  return comments;
}

function bindEvents() {
  document.addEventListener("click", (event) => {
    const historyButton = event.target.closest("[data-history-id]");
    if (historyButton) {
      openHistory(historyButton.dataset.historyId);
      return;
    }
    const viewButton = event.target.closest("[data-view]");
    if (viewButton) {
      showView(viewButton.dataset.view);
      return;
    }
    const movieButton = event.target.closest("[data-movie]");
    if (movieButton) {
      void openMovie(movieButton.dataset.movie);
    }
  });

  $("#search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    void performSearch($("#movie-query").value.trim());
  });

  $("#results-search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    void performSearch($("#results-movie-query").value.trim());
  });

  // 认证事件
  $("#nav-login").addEventListener("click", () => showAuthModal("login"));
  $("#nav-logout").addEventListener("click", async () => {
    await fetch("/api/auth/logout", { method: "POST" });
    currentUser = null;
    updateAuthUI();
    showView("home");
  });
  $("#auth-form").addEventListener("submit", handleAuthSubmit);
  $("#auth-cancel").addEventListener("click", hideAuthModal);
  $("#auth-switch").addEventListener("click", () => {
    const btn = $("#auth-switch");
    showAuthModal(btn.textContent.includes("注册") ? "register" : "login");
  });

  $("#import-form").addEventListener("submit", (event) => {
    event.preventDefault();
    void importMovieFromForm(event.currentTarget);
  });

  $("#fetch-comments").addEventListener("click", () => {
    if (currentMovie?.source === "导入评论文件") void analyzeImportedComments("local");
    else void loadCommentsAndAnalyze(false, true);
  });
  $("#fetch-analyze").addEventListener("click", () => {
    if (currentMovie?.source === "导入评论文件") void analyzeImportedComments("ai");
    else void loadCommentsAndAnalyze(true);
  });
  $("#export-comments").addEventListener("click", exportComments);
  $("#export-pdf").addEventListener("click", exportPdfReport);
  $("#global-back").addEventListener("click", goBack);

  $("#comment-keyword").addEventListener("input", renderComments);
  $("#sentiment-filters").addEventListener("click", (event) => {
    const button = event.target.closest("[data-filter]");
    if (!button) return;
    activeSentiment = button.dataset.filter;
    $all("#sentiment-filters button").forEach((item) => item.classList.remove("is-active"));
    button.classList.add("is-active");
    renderComments();
  });

  // 设置页面保存
  $("#save-settings").addEventListener("click", saveSettings);
}

async function init() {
  renderPosterRows();
  renderResults("", [], { source: "empty" });
  renderDetail(null);
  renderHistory();
  bindEvents();
  document.body.classList.add("is-home");
  await checkAuth();
  await loadSettingsFromApi();
  loadSettingsToForm();
  const hot = await apiGet("/api/hot?limit=20", null);
  if (hot?.movies?.length) {
    const apiPosters = hot.movies
      .filter((movie) => movie.posterUrl)
      .slice(0, 20)
      .map((movie) => [movie.title, movie.posterUrl.startsWith("http") ? proxyPosterUrl(movie.posterUrl) : movie.posterUrl]);
    if (apiPosters.length) {
      const combined = shuffleArray([...apiPosters, ...fallbackPosters]);
      const mid = Math.ceil(combined.length / 2);
      $("#poster-row-top").replaceChildren(posterTrack(combined.slice(0, mid), "left"));
      $("#poster-row-bottom").replaceChildren(posterTrack(combined.slice(mid), "right"));
    }
  }
  if (currentUser) {
    await loadHistoryFromApi();
    renderHistory();
  }
}

void init();
