package com.filmoracle.util;

import com.filmoracle.model.Movie;
import com.filmoracle.model.Comment;
import java.util.List;
import java.util.ArrayList;

/**
 * 演示兜底数据——豆瓣 API 不可用时使用
 */
public class FallbackData {

    public static List<Movie> getFallbackMovies() {
        List<Movie> movies = new ArrayList<>();
        movies.add(createMovie("27010768", "寄生虫", "2019", "剧情 / 悬疑 / 犯罪", "8.8", "1800000",
                "奉俊昊", "宋康昊 / 李善均 / 赵汝贞", "韩国", "韩语", "2019-05-30", "132分钟",
                "金基泽全家蜗居在半地下室，靠着折披萨盒勉强度日。基宇进入朴社长家做家教，随后全家相继以不同身份混入。然而在一个雨夜，一个不速之客的到访揭开了深藏的秘密。", "寄生虫海报.jpg"));
        movies.add(createMovie("2131459", "机器人总动员", "2008", "科幻 / 动画 / 冒险", "9.3", "1300000",
                "安德鲁·斯坦顿", "本·贝尔特 / 艾丽莎·奈特", "美国", "英语", "2008-06-27", "98分钟",
                "公元2805年，人类因污染离开地球。只剩最后一个机器人WALL-E在清理垃圾。直到探测机器人EVE到来，WALL-E跟随她飞向太空，开启改变人类命运的旅程。", "机器人总动员海报.jpg"));
        movies.add(createMovie("1292001", "海上钢琴师", "1998", "剧情 / 音乐", "9.3", "1700000",
                "朱塞佩·托纳多雷", "蒂姆·罗斯 / 普路特·泰勒·文斯", "意大利", "英语 / 法语", "1998-10-28", "165分钟",
                "1900年元旦，游轮上发现弃婴取名1900。他在船上长大，展现惊人钢琴天赋，却从未踏上陆地。面对陆地上无穷无尽的选择，他做出了惊人决定。", "海上钢琴师海报.jpg"));
        movies.add(createMovie("1292052", "肖申克的救赎", "1994", "剧情 / 犯罪", "9.7", "2900000",
                "弗兰克·德拉邦特", "蒂姆·罗宾斯 / 摩根·弗里曼", "美国", "英语", "1994-09-10", "142分钟",
                "银行家安迪被诬陷谋杀妻子，判无期徒刑。在肖申克监狱中结识瑞德，两人建立深厚友谊。安迪利用金融知识为狱长洗钱，同时秘密挖掘逃生通道，十九年后重获自由。", "肖申克的救赎海报.jpg"));
        movies.add(createMovie("1291564", "千与千寻", "2001", "剧情 / 动画 / 奇幻", "9.4", "2100000",
                "宫崎骏", "柊瑠美 / 入野自由", "日本", "日语", "2001-07-20", "125分钟",
                "十岁少女千寻误入神灵世界，父母因贪吃变成猪。千寻在浴场打工，寻找拯救父母的方法，学会了勇敢和独立。", "千与千寻海报.jpg"));
        movies.add(createMovie("1292722", "泰坦尼克号", "1997", "剧情 / 爱情 / 灾难", "9.5", "1800000",
                "詹姆斯·卡梅隆", "莱昂纳多·迪卡普里奥 / 凯特·温丝莱特", "美国", "英语", "1998-04-03", "194分钟",
                "穷画家杰克在泰坦尼克号上邂逅贵族少女露丝。两人跨越阶级坠入爱河，然而巨轮撞上冰山即将沉没。", "泰坦尼克号海报.jpg"));
        movies.add(createMovie("1292064", "这个杀手不太冷", "1994", "剧情 / 动作 / 犯罪", "9.4", "2200000",
                "吕克·贝松", "让·雷诺 / 娜塔莉·波特曼", "法国", "英语 / 意大利语", "1994-09-14", "110分钟",
                "职业杀手里昂收留了邻家少女玛蒂达。她的家人被腐败警长杀害，两个孤独灵魂在血腥世界中找到了彼此的温暖。", "这个杀手不太冷海报.jpg"));
        movies.add(createMovie("1292000", "星际穿越", "2014", "科幻 / 冒险 / 剧情", "9.4", "1900000",
                "克里斯托弗·诺兰", "马修·麦康纳 / 安妮·海瑟薇", "美国 / 英国", "英语", "2014-11-12", "169分钟",
                "在地球资源枯竭的未来，前宇航员库珀穿越虫洞寻找新家园。他经历时间膨胀带来的生离死别，最终在五维空间中将关键数据传递给女儿。", "星际穿越海报.jpg"));
        return movies;
    }

    public static List<Comment> getFallbackComments() {
        String[][] data = {
                {"导演对节奏的把控太绝了，前半段铺垫看似平淡，后半段反转一个接一个，看得人喘不过气。剧本结构精妙绝伦，每个细节都有回响。", "5", "豆瓣用户A", "2341"},
                {"配乐和画面配合得天衣无缝，几个关键场景的情绪释放非常有效。摄影构图极具美感，光影运用教科书级别。", "5", "豆瓣用户B", "1876"},
                {"演技全员在线，宋康昊的眼神戏太强了。每个角色都有完整的弧光，人物关系刻画细腻。", "5", "豆瓣用户C", "1543"},
                {"主题深刻，用黑色幽默讲阶层固化，每个空间都有隐喻。富人和穷人的世界被一扇门隔开。", "5", "豆瓣用户D", "1209"},
                {"整体完成度很高，但中段有些桥段略显刻意，节奏略有拖沓。不过结尾的冲击力足够强。", "4", "豆瓣用户E", "876"},
                {"剪辑流畅，转场自然。前半段的喜剧氛围和后半段的惊悚感切换得非常丝滑。", "4", "豆瓣用户F", "654"},
                {"美术设计太赞了，半地下室和富人大宅的对比触目惊心。道具和服装的质感都很到位。", "4", "豆瓣用户G", "521"},
                {"故事设定有意思，但部分人物动机有点薄弱。富人角色过于脸谱化了。", "3", "豆瓣用户H", "432"},
                {"节奏前慢后快，第一次看有点不适应。重看之后才发现前面的铺垫都有用意。", "4", "豆瓣用户I", "398"},
                {"没有想象中那么神，可能期望太高了。不过视觉表达确实独特，情绪渲染到位。", "3", "豆瓣用户J", "287"},
                {"结局太压抑了，看完心情很沉重。但这正是导演想要的效果吧。", "4", "豆瓣用户K", "256"},
                {"音效设计很精细，雨声、脚步声都有情绪暗示。声音层级的对比暗示了阶层的对比。", "4", "豆瓣用户L", "198"}
        };
        List<Comment> comments = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            Comment c = new Comment();
            c.setId("fallback-comment-" + i);
            c.setText(data[i][0]);
            c.setRatingValue(Integer.parseInt(data[i][1]));
            c.setStar(data[i][1] + "星");
            c.setUser(data[i][2]);
            c.setVoteCount(Integer.parseInt(data[i][3]));
            c.setCreatedAt("");
            comments.add(c);
        }
        return comments;
    }

    public static Movie findMovieById(String id) {
        return getFallbackMovies().stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElse(getFallbackMovies().get(0));
    }

    private static Movie createMovie(String id, String title, String year, String genre, String rating,
                                     String votes, String director, String cast, String region,
                                     String language, String date, String duration,
                                     String summary, String poster) {
        Movie m = new Movie();
        m.setId(id); m.setTitle(title); m.setYear(year); m.setGenre(genre);
        m.setRating(rating); m.setVotes(votes); m.setDirector(director); m.setCast(cast);
        m.setRegion(region); m.setLanguage(language); m.setDate(date); m.setDuration(duration);
        m.setSummary(summary); m.setPoster(poster); m.setSource("fallback");
        return m;
    }
}
