// ─── 设置管理 ───
const DEFAULT_PROMPT = `你是一位专业的电影评论分析师。请分析以下电影评论数据，输出纯JSON格式的分析结果（不要markdown，不要\`\`\`json标记）。

分析要求：
1. 优点提炼：从评论中找出观众反复提及的亮点。不要泛泛说"演技好、画面美"，要具体到哪个场景、哪个细节被称赞，引用评论者的原话或观点。例如"多位观众提到雨夜逃亡段落的镜头调度"，而非"摄影出色"。
2. 缺点与争议：诚实指出评论中暴露的问题。哪些情节被质疑逻辑不通？哪些角色被批评单薄？哪些手法被认为刻意？如果评论间存在分歧（同一元素有人赞有人弹），要呈现这种张力，不要回避。
3. 过人之处：将本片与同类型、同题材或同导演的其他作品横向比较。评论者自己是否提到了参照系？如果没有，你根据评论反映的特征主动比较。比较要落到具体手法上，不要空泛地说"更深刻"。
4. 观众情绪图谱：评论者看完电影后的情绪状态是什么，是被震撼、被治愈、被冒犯，还是觉得过誉了？这种情绪集中体现在哪些评论里？
5. 总评：约400字综合评析。先说这部电影在评论中呈现的核心面貌，再分述优缺点，优缺点比例按评论实际反映的比例来，最后给出有判断力的结论，说明适合什么样的观众以及独特价值。不要说"值得一看"这种废话。
6. 关键词必须来自评论中的经典总结词语或高频短语，避免"剧情、演技、画面"这类过于通用的词，除非评论反复围绕具体搭配出现。
7. 十维雷达图必须拉开差距。不要所有维度都集中在8分附近，评论明显批评的维度应低到4-6分，明显突出的维度可到8.5-9.6分。
8. 情感散点必须基于每条评论文本和星级判断精确坐标：x为-1到1，y为0到1，强烈愤怒/震撼/狂喜/压抑应给更高y值。

输出JSON格式（纯JSON，不要markdown标记）：
{
  "keywords": [["关键词", 次数], ...],
  "ratingDistribution": [["5星", 百分比], ["4星", 百分比], ["3星", 百分比], ["2星", 百分比], ["1星", 百分比]],
  "comparison": [["本片评分", 值], ["同类型热度", 值], ["评价人数", 值], ["正向情绪", 值]],
  "radar": [["剧本", 分数], ["导演", 分数], ["表演", 分数], ["摄影", 分数], ["剪辑", 分数], ["声音", 分数], ["美术", 分数], ["特效", 分数], ["主题", 分数], ["完成度", 分数]],
  "scatter": [{"x": 坐标, "y": 坐标, "sentiment": "情感", "label": "象限", "index": 序号}, ...],
  "sentimentDistribution": {"positive": 百分比, "negative": 百分比, "neutral": 百分比},
  "summary": {"positiveRate": 值, "negativeRate": 值, "neutralRate": 值, "keywordsSummary": "关键词摘要", "mainControversy": "争议点", "totalComments": 数量},
  "review": "400字综合评析，包含优点、缺点、横向比较、总评，每句结论有评论依据"
}`;

// ─── 设置管理（数据库后端） ───
let appSettings = {
  apiBase: "/api",
  commentCount: 100,
  requestTimeout: 9,
  aiModel: "moonshot-v1-8k",
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
          aiModel: s.aiModel || "moonshot-v1-8k",
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
    sentimentDistribution: analysis.sentimentDistribution || empty.sentimentDistribution,
    summary: analysis.summary || empty.summary
  };
}

function applyAnalysisToComments(comments, analysis) {
  const scatter = Array.isArray(analysis?.scatter) ? analysis.scatter : [];
  const analyzed = Array.isArray(analysis?.analyzedComments) ? analysis.analyzedComments : [];
  return comments.map((comment, index) => {
    const point = scatter.find((item) => Number(item.index) === index) || scatter[index] || {};
    const detail = analyzed.find((item) => item.id && item.id === comment.id) || analyzed[index] || {};
    return {
      ...comment,
      sentiment: detail.sentiment || point.sentiment || comment.sentiment || sentimentOf(comment),
      aspect: detail.aspect || comment.aspect || "完成度与影响力",
      quadrant: detail.quadrant || point.label || comment.quadrant || quadrantFor(comment.text || "", Number(comment.ratingValue || 3), Number(point.x || 0))
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

function showView(name, options = {}) {
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
  if (currentUser) {
    loginBtn.style.display = "none";
    logoutBtn.style.display = "";
    userInfo.style.display = "";
    userInfo.textContent = `${currentUser.username}（${currentUser.role === "admin" ? "管理员" : "用户"}）`;
    if (currentUser.role === "admin") {
      $("#nav-home").style.display = "none";
      $("#nav-import").style.display = "none";
      $("#nav-history").style.display = "none";
      showView("settings");
    } else {
      $("#nav-home").style.display = "";
      $("#nav-import").style.display = "";
      $("#nav-history").style.display = "";
    }
  } else {
    loginBtn.style.display = "";
    logoutBtn.style.display = "none";
    userInfo.style.display = "none";
    $("#nav-home").style.display = "";
    $("#nav-import").style.display = "";
    $("#nav-history").style.display = "";
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
        if (loginData.ok) { currentUser = loginData.user; updateAuthUI(); hideAuthModal(); }
        else { hint.textContent = "注册成功，请手动登录"; submitBtn.textContent = "登录"; }
      } else { currentUser = data.user; updateAuthUI(); hideAuthModal(); }
    } else { hint.textContent = data.error || "操作失败"; }
  } catch (err) { hint.textContent = "网络错误: " + err.message; }
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
  $("#task-state").textContent = state;
  $("#task-log").textContent = lines.join("\n");
}

function appendTask(line, state) {
  const log = $("#task-log");
  log.textContent += `${line}\n`;
  log.scrollTop = log.scrollHeight;
  if (state) $("#task-state").textContent = state;
}

function renderResults(query, movies, meta = {}) {
  $("#results-title").textContent = `搜索「${query || "电影"}」`;
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
  return {
    keywords,
    ratingDistribution: ratingRows(comments),
    comparison: [
      ["本片评分", Math.round(Number(movie.rating || averageRating * 2) * 10)],
      ["同类型热度", Math.min(96, 50 + Math.round(Math.log10(Number(movie.votes || 10000)) * 8))],
      ["评价人数", Math.min(98, 42 + comments.length * 2)],
      ["正向情绪", Math.round((positive / comments.length) * 100)]
    ],
    scatter: comments.map((comment, index) => emotionPoint(comment, index)),
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
  const stop = new Set(["电影", "影片", "真的", "感觉", "觉得", "一个", "这个", "就是", "还是", "没有", "不是", "非常", "比较", "有点", "剧情", "演技", "画面", "导演", "演员", "故事", "角色", "配乐", "镜头", "表演", "主题", "节奏", "音乐", "时候", "因为", "所以", "但是", "可以", "观众", "这部", "一片", "看了", "看完", "一种", "一些", "这种", "那种", "什么", "怎么", "他们", "我们", "你们", "自己", "别人", "大家", "所有", "一直", "已经", "或者", "而且", "然后", "后来", "最后", "开始", "出现", "这是", "那是", "还有", "如果", "虽然", "不过", "其实", "就是", "这样", "那样", "一些", "很多", "很少", "特别", "确实", "相对", "一般", "普通", "好看", "不好", "不错", "还行", "太差", "太好", "说是", "来说", "对于", "地方", "东西", "时候", "看到", "发现", "明白", "理解", "知道", "认为", "以为", "认为", "出来", "起来", "下去", "过去", "回来", "回来", "不了", "不到", "不会", "不能", "不要", "不用", "不行", "无法", "一样", "这种", "那种", "这是", "那是", "还有", "演的", "拍的", "看的", "说的", "想的", "做的", "了的", "着的", "过的", "好的", "差的", "大的", "小的", "多的", "少的", "高的", "低的", "新的", "旧的", "长的", "短的", "深的", "浅的", "厚的", "薄的", "远的", "近的", "快的", "慢的", "早的", "晚的", "热的", "冷的", "满的", "空的", "真的", "假的", "对的", "错的", "难的", "易的", "强的", "弱的", "亮的", "暗的", "重的", "轻的", "宽的", "窄的", "硬的", "软的", "干的", "湿的", "生的", "熟的", "活的", "死的", "美的", "丑的", "善的", "恶的"]);
  const freq = new Map();
  comments.forEach((comment) => {
    const text = (comment.text || "").replace(/[^\u4e00-\u9fa5A-Za-z0-9]/g, " ");
    const chineseParts = text.match(/[\u4e00-\u9fa5]{2,10}/g) || [];
    chineseParts.forEach((part) => {
      for (let size = Math.min(6, part.length); size >= 2; size -= 1) {
        for (let i = 0; i <= part.length - size; i += 1) {
          const phrase = part.slice(i, i + size);
          if (stop.has(phrase) || [...stop].some((word) => phrase === word)) continue;
          if (/^(这部|一部|很多|观众|豆瓣|看完|看过|时候|因为|所以|但是|如果|可以|什么|怎么|他们|我们|你们|自己|别人|大家|所有|一直|已经|还是|或者|而且|然后|后来|最后|开始|出现|这是|那是|还有|虽然|不过|其实|就是|这样|那样|一些|很多|很少|特别|确实|相对|一般|普通|好看|不好|不错|还行|太差|太好|说是|来说|对于|地方|东西|看到|发现|明白|理解|知道|认为|以为|出来|起来|下去|过去|回来|不了|不到|不会|不能|不要|不用|不行|无法|一样)$/.test(phrase)) continue;
          freq.set(phrase, (freq.get(phrase) || 0) + 1 + Math.min(3, Number(comment.voteCount || 0) / 30));
        }
      }
    });
  });
  const funcChars = new Set("的了是是在和与或但我你他她它们这那有就还不没也都很太个着过把被让使向从到为以于上下中外前后里间地得着过上下一中可来去");
  const isMeaningless2Char = (phrase) => {
    if (phrase.length !== 2) return false;
    return funcChars.has(phrase[0]) || funcChars.has(phrase[1]);
  };
  const allWords = new Set(freq.keys());
  const ranked = [...freq.entries()]
    .filter(([word, count]) => count >= 1.5 && !isMeaningless2Char(word) && ![...stop].some((generic) => word.includes(generic) && word.length <= generic.length + 1))
    .filter(([word, count]) => {
      // 移除是更长关键词子串且频率相近的短词
      for (const other of allWords) {
        if (other !== word && other.length > word.length && other.includes(word) && freq.get(other) >= count * 0.5) return false;
      }
      return true;
    })
    .sort((a, b) => b[1] - a[1] || b[0].length - a[0].length);
  const selected = [];
  ranked.forEach(([word, count]) => {
    if (selected.length >= 18) return;
    if (selected.some(([existing]) => existing.includes(word) || word.includes(existing))) return;
    selected.push([word, Math.max(1, Math.round(count))]);
  });
  return selected.length ? selected : [["雨夜逃亡", 3], ["阶层反转", 2], ["地下室段落", 2], ["黑色幽默", 2], ["结尾余味", 1]];
}

function emotionPoint(comment, index) {
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

function renderCharts() {
  // Trigger panel refresh animation
  document.querySelectorAll(".analysis-panel").forEach(panel => {
    panel.classList.remove("refresh");
    void panel.offsetWidth;
    panel.classList.add("refresh");
  });
  const maxKeyword = Math.max(1, ...currentAnalysis.keywords.map(([, count]) => Number(count) || 1));
  const cloudPositions = [
    [50, 50, -4], [28, 34, 7], [70, 34, -9], [24, 68, -6], [76, 68, 8],
    [48, 24, 3], [42, 78, -10], [63, 78, 5], [18, 48, 9], [84, 48, -7],
    [34, 18, -4], [64, 17, 6], [16, 82, 3], [86, 82, -5], [52, 88, 8],
    [36, 54, -8], [66, 55, 4], [50, 66, -2]
  ];
  $("#keyword-cloud").innerHTML = currentAnalysis.keywords.length
    ? currentAnalysis.keywords.map(([word, count], index) => {
      const [left, top, rotate] = cloudPositions[index % cloudPositions.length];
      const size = 13 + Math.round((Number(count) || 1) / maxKeyword * 20);
      const opacity = 0.74 + Math.min(0.26, (Number(count) || 1) / maxKeyword * 0.26);
      return `<span style="left:${left}%;top:${top}%;font-size:${size}px;opacity:${opacity};--rotate:${rotate}deg;--kw-opacity:${opacity};animation-delay:${index * 60}ms">${escapeHtml(word)}</span>`;
    }).join("")
    : `<span style="left:50%;top:50%;font-size:15px">等待评论分析</span>`;

  $("#rating-bars").innerHTML = renderRatingColumnChart(currentAnalysis.ratingDistribution);
  $("#comparison-bars").innerHTML = currentAnalysis.comparison.map(([label, value], idx) => `
    <div class="bar-row" style="animation-delay:${idx * 50}ms">
      <span>${escapeHtml(label)}</span>
      <span class="bar-track"><span class="bar-fill" style="width:${value}%"></span></span>
      <span>${value}</span>
    </div>
  `).join("");

  const points = currentAnalysis.scatter.map((point, idx) => {
    const cx = 180 + point.x * 130;
    const cy = 188 - point.y * 156;
    const fill = point.sentiment === "正面" ? "#dcaa37" : point.sentiment === "负面" ? "#dc6966" : "#908f8b";
    return `<circle cx="${cx.toFixed(1)}" cy="${cy.toFixed(1)}" r="6" fill="${fill}" style="animation-delay:${idx * 30}ms"><title>${escapeHtml(point.label)}</title></circle>`;
  }).join("");
  $("#scatter-chart").innerHTML = `
    <svg viewBox="0 0 360 220" role="img" aria-label="情感散点图">
      <rect x="0" y="0" width="360" height="220" rx="8" fill="#30302e"/>
      <line x1="180" y1="24" x2="180" y2="188" stroke="#5e5d59"/>
      <line x1="34" y1="106" x2="326" y2="106" stroke="#5e5d59"/>
      <text x="38" y="38" fill="#dc6966" font-size="12">压抑/惊悚</text>
      <text x="250" y="38" fill="#dcaa37" font-size="12">热血/高燃</text>
      <text x="42" y="184" fill="#908f8b" font-size="12">孤独/克制</text>
      <text x="250" y="184" fill="#52c41a" font-size="12">治愈/轻松</text>
      ${points}
    </svg>
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
  if (currentAnalysis.review) {
    reviewEl.innerHTML = `<p>${escapeHtml(currentAnalysis.review)}</p>`;
    const isAi = currentAnalysis.engine && currentAnalysis.engine.startsWith("ai");
    tagEl.textContent = isAi ? "AI评析" : "本地评析";
    tagEl.className = isAi ? "status-tag running" : "status-tag neutral";
  } else {
    reviewEl.innerHTML = `<p class="muted">点击「获取并 AI 分析」后，此处将显示基于评论数据的客观评析。</p>`;
    tagEl.textContent = "待生成";
    tagEl.className = "status-tag neutral";
  }
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

async function loadCommentsAndAnalyze(runAnalysis = false) {
  if (!requireAuth()) return;
  if (!currentMovie) {
    setTask(["> 请先搜索并选择一部电影，或从导入评论入口创建来源电影。"], "no movie");
    return;
  }
  const settings = getSettings();
  const commentCount = settings.commentCount || 100;

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
  if (runAnalysis) {
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
}

function importMovieFromForm(form) {
  const formData = new FormData(form);
  const importedMovie = {
    ...fallbackMovies[1],
    id: `import-${Date.now()}`,
    title: formData.get("title") || fallbackMovies[1].title,
    year: formData.get("year") || fallbackMovies[1].year,
    director: formData.get("director") || fallbackMovies[1].director,
    cast: formData.get("cast") || fallbackMovies[1].cast,
    genre: formData.get("genre") || fallbackMovies[1].genre,
    region: formData.get("region") || fallbackMovies[1].region,
    language: formData.get("language") || fallbackMovies[1].language,
    duration: formData.get("duration") || fallbackMovies[1].duration,
    summary: formData.get("summary") || fallbackMovies[1].summary,
    source: formData.get("source") || "导入评论文件"
  };
  currentMovie = importedMovie;
  currentComments = [...fallbackComments];
  currentAnalysis = buildAnalysis(currentComments, currentMovie);
  renderDetail(importedMovie);
  showView("detail");
  setTask([
    "> import: local comment file preview",
    `> movie: ${importedMovie.title}`,
    "> comments: demo parsed 5",
    "> ai-analysis: sentiment, keywords, radar, scatter generated"
  ], "import ready");
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
    importMovieFromForm(event.currentTarget);
  });

  $("#fetch-comments").addEventListener("click", () => void loadCommentsAndAnalyze(false));
  $("#fetch-analyze").addEventListener("click", () => void loadCommentsAndAnalyze(true));
  $("#export-comments").addEventListener("click", exportComments);
  $("#global-back").addEventListener("click", goBack);
  $("#save-history").addEventListener("click", async () => {
    if (!currentMovie) {
      appendTask("> save skipped: 当前没有电影详情", "save empty");
      return;
    }
    if (!currentUser) { showAuthModal("login"); return; }
    try {
      const resp = await fetch("/api/history", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(currentMovie)
      });
      const data = await resp.json();
      if (data.ok) {
        const histResp = await fetch("/api/history");
        const histData = await histResp.json();
        if (histData.ok) history = histData.history || [];
        renderHistory();
        appendTask(`> 已保存到浏览历史: ${currentMovie.title}`, "saved");
      } else {
        appendTask(`> 保存失败: ${data.error || ""}`, "save error");
      }
    } catch (e) {
      appendTask(`> 保存失败: ${e.message}`, "save error");
    }
  });

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
    try {
      const resp = await fetch("/api/history");
      if (resp.ok) {
        const data = await resp.json();
        if (data.ok) {
          history = data.history || [];
          renderHistory();
        }
      }
    } catch {}
  }
}

void init();
