"""KET 课程单词种子数据。

由 scripts/build_ket_word_bank.py 生成（pyphen 自动拆音节 + MyMemory 翻译）。
词表来源：Cambridge English 官方 A2 Key vocabulary list（约 1500 词），此处取前 150 高频词。

字段：(spelling, syllables, meaning_cn, phonetic, sample_sentence, sample_sentence_translation, sort_order)
- phonetic: IPA 音标（英式 RP），如 "/ˈæp.əl/"
- sample_sentence: 简短英文例句（≤ 8 词，儿童友好，British English）
- sample_sentence_translation: 例句中文翻译

若词表更新，重新运行脚本即可：cd services/backend && .venv/bin/python scripts/build_ket_word_bank.py

v0.14.0 起:seed 时先 upsert lexicon(全局词表),再插 words 并回填 lexeme_id。
"""

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.course import Course
from app.models.word import Lexicon, Word

# (spelling, syllables, meaning_cn, phonetic, sample_sentence, sample_sentence_translation, sort_order)
KET_WORDS: list[tuple[str, list[str], str, str, str, str, int]] = [
    ("apple", ["ap", "ple"], "苹果", "/ˈæp.əl/", "I eat an apple every day.", "我每天吃一个苹果。", 1),
    ("banana", ["ba", "nan", "a"], "香蕉", "/bəˈnɑː.nə/", "The monkey likes bananas.", "猴子喜欢香蕉。", 2),
    ("orange", ["or", "ange"], "橙子", "/ˈɒr.ɪndʒ/", "The orange is sweet.", "橙子很甜。", 3),
    ("grape", ["grape"], "葡萄", "/ɡreɪp/", "I like purple grapes.", "我喜欢紫葡萄。", 4),
    ("lemon", ["lem", "on"], "柠檬", "/ˈlem.ən/", "The lemon is very sour.", "柠檬很酸。", 5),
    ("peach", ["peach"], "桃子", "/piːtʃ/", "This peach is juicy.", "这个桃子很多汁。", 6),
    ("pear", ["pear"], "梨", "/peər/", "She eats a green pear.", "她吃了一个绿色的梨。", 7),
    ("cherry", ["cher", "ry"], "樱桃", "/ˈtʃer.i/", "The cherry is red.", "樱桃是红色的。", 8),
    ("bread", ["bread"], "面包", "/bred/", "I have bread for breakfast.", "我早餐吃面包。", 9),
    ("butter", ["but", "ter"], "黄油", "/ˈbʌt.ər/", "I like butter on toast.", "我喜欢吐司上抹黄油。", 10),
    ("cheese", ["cheese"], "奶酪", "/tʃiːz/", "The cheese is yellow.", "奶酪是黄色的。", 11),
    ("egg", ["egg"], "鸡蛋", "/eɡ/", "I eat an egg every morning.", "我每天早上吃一个鸡蛋。", 12),
    ("milk", ["milk"], "牛奶", "/mɪlk/", "I drink milk every day.", "我每天喝牛奶。", 13),
    ("rice", ["rice"], "米饭", "/raɪs/", "We eat rice for dinner.", "我们晚饭吃米饭。", 14),
    ("salt", ["salt"], "盐", "/sɒlt/", "The soup needs more salt.", "汤需要再加点盐。", 15),
    ("sugar", ["su", "gar"], "糖", "/ˈʃʊɡ.ər/", "This tea has too much sugar.", "这杯茶糖太多了。", 16),
    ("tea", ["tea"], "茶", "/tiː/", "My mum drinks tea.", "我妈妈喝茶。", 17),
    ("coffee", ["cof", "fee"], "咖啡", "/ˈkɒf.i/", "My dad loves coffee.", "我爸爸很喜欢咖啡。", 18),
    ("juice", ["juice"], "果汁", "/dʒuːs/", "I drink apple juice.", "我喝苹果汁。", 19),
    ("water", ["wa", "ter"], "水", "/ˈwɔː.tər/", "I drink water after running.", "我跑步后喝水。", 20),
    ("wine", ["wine"], "葡萄酒", "/waɪn/", "Adults drink wine at dinner.", "成年人在晚餐时喝葡萄酒。", 21),
    ("beer", ["beer"], "啤酒", "/bɪər/", "Adults drink beer in summer.", "成年人在夏天喝啤酒。", 22),
    ("cat", ["cat"], "猫", "/kæt/", "The cat is sleeping.", "猫在睡觉。", 23),
    ("dog", ["dog"], "狗", "/dɒɡ/", "My dog is big and brave.", "我的狗又大又勇敢。", 24),
    ("fish", ["fish"], "鱼", "/fɪʃ/", "The fish swims in water.", "鱼在水里游。", 25),
    ("bird", ["bird"], "鸟", "/bɜːd/", "The bird is singing loud.", "鸟儿唱得很响。", 26),
    ("horse", ["horse"], "马", "/hɔːs/", "The horse runs very fast.", "马跑得很快。", 27),
    ("cow", ["cow"], "牛", "/kaʊ/", "The cow eats green grass.", "牛吃绿草。", 28),
    ("sheep", ["sheep"], "羊", "/ʃiːp/", "The sheep has soft wool.", "羊有柔软的毛。", 29),
    ("pig", ["pig"], "猪", "/pɪɡ/", "The pig is pink and fat.", "猪是粉色又胖胖的。", 30),
    ("rabbit", ["rab", "bit"], "兔子", "/ˈræb.ɪt/", "The rabbit hops all day.", "兔子整天蹦蹦跳跳。", 31),
    ("mother", ["moth", "er"], "母亲", "/ˈmʌð.ər/", "My mother is very kind.", "我妈妈很和蔼。", 32),
    ("father", ["fa", "ther"], "父亲", "/ˈfɑː.ðər/", "My father is tall and strong.", "我爸爸又高又壮。", 33),
    ("sister", ["sis", "ter"], "姐妹", "/ˈsɪs.tər/", "My sister is younger than me.", "我妹妹比我小。", 34),
    ("brother", ["broth", "er"], "兄弟", "/ˈbrʌð.ər/", "My brother is very funny.", "我哥哥很幽默。", 35),
    ("son", ["son"], "儿子", "/sʌn/", "Their son is clever.", "他们的儿子很聪明。", 36),
    ("daughter", ["daugh", "ter"], "女儿", "/ˈdɔː.tər/", "Her daughter is lovely.", "她女儿很可爱。", 37),
    ("uncle", ["un", "cle"], "叔叔", "/ˈʌŋ.kəl/", "My uncle is a doctor.", "我叔叔是医生。", 38),
    ("aunt", ["aunt"], "阿姨", "/ɑːnt/", "My aunt lives in London.", "我阿姨住在伦敦。", 39),
    ("cousin", ["cou", "sin"], "表亲", "/ˈkʌz.ən/", "My cousin plays football well.", "我表哥足球踢得很好。", 40),
    ("family", ["fam", "i", "ly"], "家庭", "/ˈfæm.əl.i/", "I love my family very much.", "我非常爱我的家人。", 41),
    ("parent", ["par", "ent"], "父母", "/ˈpeə.rənt/", "My parents are kind to me.", "我父母对我很好。", 42),
    ("child", ["child"], "孩子", "/tʃaɪld/", "The child is happy today.", "孩子今天很开心。", 43),
    ("baby", ["ba", "by"], "婴儿", "/ˈbeɪ.bi/", "The baby is sleeping now.", "宝宝现在在睡觉。", 44),
    ("grandfather", ["grand", "fa", "ther"], "祖父", "/ˈɡræn.fɑː.ðər/", "My grandfather is very wise.", "我祖父很有智慧。", 45),
    ("grandmother", ["grand", "moth", "er"], "祖母", "/ˈɡræn.mʌð.ər/", "My grandmother bakes cakes.", "我祖母会烤蛋糕。", 46),
    ("red", ["red"], "红色", "/red/", "The apple is red and sweet.", "苹果又红又甜。", 47),
    ("blue", ["blue"], "蓝色", "/bluː/", "The sky is blue today.", "今天天空是蓝色的。", 48),
    ("green", ["green"], "绿色", "/ɡriːn/", "The grass is green in spring.", "春天草是绿色的。", 49),
    ("yellow", ["yel", "low"], "黄色", "/ˈjel.əʊ/", "The sun is yellow and bright.", "太阳又黄又亮。", 50),
    ("black", ["black"], "黑色", "/blæk/", "My shoes are black.", "我的鞋子是黑色的。", 51),
    ("white", ["white"], "白色", "/waɪt/", "The cloud is white and soft.", "云朵又白又软。", 52),
    ("purple", ["pur", "ple"], "紫色", "/ˈpɜː.pəl/", "The flower is purple.", "花是紫色的。", 53),
    ("brown", ["brown"], "棕色", "/braʊn/", "The bear is brown and big.", "熊是棕色又大。", 54),
    ("small", ["small"], "小的", "/smɔːl/", "The cat is small and cute.", "猫又小又可爱。", 55),
    ("big", ["big"], "大的", "/bɪɡ/", "The elephant is very big.", "大象非常大。", 56),
    ("tall", ["tall"], "高的", "/tɔːl/", "The tree is tall and green.", "树又高又绿。", 57),
    ("short", ["short"], "短的", "/ʃɔːt/", "The pencil is short.", "铅笔很短。", 58),
    ("long", ["long"], "长的", "/lɒŋ/", "Her hair is long.", "她的头发很长。", 59),
    ("wide", ["wide"], "宽的", "/waɪd/", "The river is wide.", "河很宽。", 60),
    ("narrow", ["nar", "row"], "窄的", "/ˈnær.əʊ/", "The path is narrow.", "小路很窄。", 61),
    ("thick", ["thick"], "厚的", "/θɪk/", "The book is very thick.", "这本书很厚。", 62),
    ("thin", ["thin"], "薄的", "/θɪn/", "The paper is thin.", "纸很薄。", 63),
    ("hot", ["hot"], "热的", "/hɒt/", "The tea is too hot.", "茶太热了。", 64),
    ("cold", ["cold"], "冷的", "/kəʊld/", "The water is cold.", "水很冷。", 65),
    ("warm", ["warm"], "温暖的", "/wɔːm/", "The day is warm and sunny.", "天气温暖又晴朗。", 66),
    ("cool", ["cool"], "凉爽的", "/kuːl/", "The wind is cool tonight.", "今晚风很凉爽。", 67),
    ("wet", ["wet"], "湿的", "/wet/", "The grass is wet this morning.", "今天早上草是湿的。", 68),
    ("dry", ["dry"], "干的", "/draɪ/", "The bread is dry.", "面包是干的。", 69),
    ("clean", ["clean"], "干净的", "/kliːn/", "My hands are clean now.", "我的手现在干净了。", 70),
    ("dirty", ["dir", "ty"], "脏的", "/ˈdɜː.ti/", "My shoes are dirty.", "我的鞋子脏了。", 71),
    ("new", ["new"], "新的", "/njuː/", "This book is new.", "这本书是新的。", 72),
    ("old", ["old"], "旧的", "/əʊld/", "That house is old.", "那栋房子很旧。", 73),
    ("good", ["good"], "好的", "/ɡʊd/", "The food is very good.", "食物非常好。", 74),
    ("bad", ["bad"], "坏的", "/bæd/", "The weather is bad today.", "今天天气不好。", 75),
    ("happy", ["hap", "py"], "开心的", "/ˈhæp.i/", "I am happy today.", "我今天很开心。", 76),
    ("sad", ["sad"], "难过的", "/sæd/", "She looks sad this morning.", "她今天早上看起来很难过。", 77),
    ("angry", ["an", "gry"], "生气的", "/ˈæŋ.ɡri/", "He is angry at me.", "他在生我的气。", 78),
    ("tired", ["tired"], "疲惫的", "/ˈtaɪəd/", "I am tired after running.", "跑步后我很累。", 79),
    ("hungry", ["hun", "gry"], "饥饿的", "/ˈhʌŋ.ɡri/", "I am hungry before lunch.", "午餐前我很饿。", 80),
    ("thirsty", ["thirst", "y"], "口渴的", "/ˈθɜː.sti/", "I am thirsty after sport.", "运动后我很渴。", 81),
    ("school", ["school"], "学校", "/skuːl/", "I go to school by bus.", "我坐公交车上学。", 82),
    ("teacher", ["teach", "er"], "老师", "/ˈtiː.tʃər/", "My teacher is very kind.", "我的老师很和蔼。", 83),
    ("student", ["stu", "dent"], "学生", "/ˈstjuː.dənt/", "She is a good student.", "她是个好学生。", 84),
    ("class", ["class"], "班级", "/klɑːs/", "Our class is small and friendly.", "我们班小又友好。", 85),
    ("book", ["book"], "书", "/bʊk/", "I read a book every night.", "我每晚读一本书。", 86),
    ("pen", ["pen"], "钢笔", "/pen/", "I write with a pen.", "我用钢笔写字。", 87),
    ("pencil", ["pen", "cil"], "铅笔", "/ˈpen.səl/", "The pencil is sharp.", "铅笔很尖。", 88),
    ("paper", ["pa", "per"], "纸", "/ˈpeɪ.pər/", "I draw on paper.", "我在纸上画画。", 89),
    ("table", ["ta", "ble"], "桌子", "/ˈteɪ.bəl/", "The book is on the table.", "书在桌子上。", 90),
    ("chair", ["chair"], "椅子", "/tʃeər/", "I sit on a chair.", "我坐在椅子上。", 91),
    ("door", ["door"], "门", "/dɔːr/", "Please close the door.", "请关门。", 92),
    ("window", ["win", "dow"], "窗户", "/ˈwɪn.dəʊ/", "Open the window, please.", "请打开窗户。", 93),
    ("wall", ["wall"], "墙", "/wɔːl/", "The picture is on the wall.", "画在墙上。", 94),
    ("floor", ["floor"], "地板", "/flɔːr/", "The cat is on the floor.", "猫在地板上。", 95),
    ("room", ["room"], "房间", "/ruːm/", "My room is big and bright.", "我的房间又大又亮。", 96),
    ("house", ["house"], "房子", "/haʊs/", "Our house has a garden.", "我们家有个花园。", 97),
    ("home", ["home"], "家", "/həʊm/", "I stay at home tonight.", "我今晚待在家。", 98),
    ("kitchen", ["kitch", "en"], "厨房", "/ˈkɪtʃ.ɪn/", "Mum cooks in the kitchen.", "妈妈在厨房做饭。", 99),
    ("bedroom", ["bed", "room"], "卧室", "/ˈbed.ruːm/", "I sleep in my bedroom.", "我在卧室睡觉。", 100),
    ("bathroom", ["bath", "room"], "浴室", "/ˈbɑːθ.ruːm/", "I wash in the bathroom.", "我在浴室洗澡。", 101),
    ("garden", ["gar", "den"], "花园", "/ˈɡɑː.dən/", "We play in the garden.", "我们在花园里玩。", 102),
    ("street", ["street"], "街道", "/striːt/", "The street is busy today.", "今天街道很热闹。", 103),
    ("road", ["road"], "路", "/rəʊd/", "The road is long and straight.", "路又长又直。", 104),
    ("city", ["cit", "y"], "城市", "/ˈsɪt.i/", "London is a big city.", "伦敦是个大城市。", 105),
    ("town", ["town"], "城镇", "/taʊn/", "I live in a small town.", "我住在小镇上。", 106),
    ("car", ["car"], "汽车", "/kɑːr/", "My dad drives a red car.", "我爸爸开红色汽车。", 107),
    ("bus", ["bus"], "公交车", "/bʌs/", "I take the bus to school.", "我坐公交车上学。", 108),
    ("train", ["train"], "火车", "/treɪn/", "The train is very fast.", "火车非常快。", 109),
    ("plane", ["plane"], "飞机", "/pleɪn/", "The plane flies high.", "飞机飞得很高。", 110),
    ("bike", ["bike"], "自行车", "/baɪk/", "I ride my bike every day.", "我每天骑自行车。", 111),
    ("boat", ["boat"], "小船", "/bəʊt/", "We row the boat together.", "我们一起划船。", 112),
    ("ship", ["ship"], "大船", "/ʃɪp/", "The ship is huge.", "轮船很大。", 113),
    ("taxi", ["tax", "i"], "出租车", "/ˈtæk.si/", "We take a taxi home.", "我们打车回家。", 114),
    ("run", ["run"], "跑", "/rʌn/", "I run fast in the park.", "我在公园里跑得很快。", 115),
    ("walk", ["walk"], "走", "/wɔːk/", "We walk to school together.", "我们一起走路上学。", 116),
    ("jump", ["jump"], "跳", "/dʒʌmp/", "The frog jumps high.", "青蛙跳得很高。", 117),
    ("swim", ["swim"], "游泳", "/swɪm/", "I can swim very well.", "我游泳游得很好。", 118),
    ("climb", ["climb"], "攀爬", "/klaɪm/", "He climbs the tall tree.", "他爬上了高树。", 119),
    ("fly", ["fly"], "飞", "/flaɪ/", "Birds fly in the sky.", "鸟儿在天空飞翔。", 120),
    ("dance", ["dance"], "跳舞", "/dɑːns/", "She dances beautifully.", "她跳舞很美。", 121),
    ("sing", ["sing"], "唱歌", "/sɪŋ/", "I sing a song for you.", "我为你唱一首歌。", 122),
    ("play", ["play"], "玩", "/pleɪ/", "We play in the garden.", "我们在花园里玩。", 123),
    ("read", ["read"], "读", "/riːd/", "I read books every night.", "我每晚读书。", 124),
    ("write", ["write"], "写", "/raɪt/", "I write a letter to Grandma.", "我给奶奶写一封信。", 125),
    ("speak", ["speak"], "说", "/spiːk/", "She speaks English well.", "她英语说得很好。", 126),
    ("listen", ["lis", "ten"], "听", "/ˈlɪs.ən/", "Please listen to the teacher.", "请听老师讲。", 127),
    ("look", ["look"], "看", "/lʊk/", "Look at the beautiful bird.", "看那只漂亮的鸟。", 128),
    ("see", ["see"], "看见", "/siː/", "I can see a bright star.", "我能看到一颗明亮的星星。", 129),
    ("hear", ["hear"], "听见", "/hɪər/", "I hear music from next door.", "我听到隔壁的音乐声。", 130),
    ("feel", ["feel"], "感觉", "/fiːl/", "I feel happy and warm.", "我感到开心又温暖。", 131),
    ("touch", ["touch"], "触摸", "/tʌtʃ/", "Please do not touch the hot pan.", "请不要碰热锅。", 132),
    ("smell", ["smell"], "闻", "/smel/", "The flower smells sweet.", "花闻起来很香。", 133),
    ("taste", ["taste"], "尝", "/teɪst/", "The cake tastes very good.", "蛋糕尝起来很好吃。", 134),
    ("eat", ["eat"], "吃", "/iːt/", "I eat lunch at noon.", "我中午吃午饭。", 135),
    ("drink", ["drink"], "喝", "/drɪŋk/", "She drinks water after running.", "她跑步后喝水。", 136),
    ("cook", ["cook"], "烹饪", "/kʊk/", "Dad cooks dinner for us.", "爸爸为我们做晚饭。", 137),
    ("wash", ["wash"], "洗", "/wɒʃ/", "I wash my hands before dinner.", "我在晚饭前洗手。", 138),
    ("sleep", ["sleep"], "睡觉", "/sliːp/", "I sleep eight hours every night.", "我每晚睡八小时。", 139),
    ("wake", ["wake"], "醒来", "/weɪk/", "I wake up early every day.", "我每天醒得很早。", 140),
    ("dream", ["dream"], "做梦", "/driːm/", "I dream of bright stars.", "我梦见明亮的星星。", 141),
    ("think", ["think"], "思考", "/θɪŋk/", "I think you are right.", "我觉得你是对的。", 142),
    ("know", ["know"], "知道", "/nəʊ/", "I know the answer now.", "我现在知道答案了。", 143),
    ("understand", ["un", "der", "stand"], "理解", "/ˌʌn.dəˈstænd/", "I understand the lesson today.", "我听懂了今天的课。", 144),
    ("monday", ["mon", "day"], "星期一", "/ˈmʌn.deɪ/", "School starts on Monday.", "星期一开学。", 145),
    ("tuesday", ["tues", "day"], "星期二", "/ˈtjuːz.deɪ/", "I have music on Tuesday.", "我星期二有音乐课。", 146),
    ("wednesday", ["wednes", "day"], "星期三", "/ˈwenz.deɪ/", "We swim on Wednesday.", "我们星期三游泳。", 147),
    ("thursday", ["thurs", "day"], "星期四", "/ˈθɜːz.deɪ/", "I visit Grandma on Thursday.", "我星期四看望奶奶。", 148),
    ("friday", ["fri", "day"], "星期五", "/ˈfraɪ.deɪ/", "Friday is my favourite day.", "星期五是我最喜欢的一天。", 149),
    ("saturday", ["sat", "ur", "day"], "星期六", "/ˈsæt.ə.deɪ/", "I play with friends on Saturday.", "我星期六和朋友玩。", 150),
]


async def seed_words_if_empty(db: AsyncSession) -> int:
    """为「英语 · KET」各 active 课程补齐词库（按课程幂等）。

    每门 KET 课程（朗读/学习/测评/...）独立判断：该课程还没有 words 时
    从 KET_WORDS 插入 150 行。lexicon 全局 upsert（spelling 唯一），
    各课 words 行共享同一 lexeme_id —— 能力跨课程互通。

    后端 lifespan 在 seed_courses 后调用。返回新写入的 words 条数。
    """
    # 查所有「英语 · KET」active 课程
    courses = (
        await db.execute(
            select(Course).where(
                Course.subject == "英语",
                Course.textbook == "KET",
                Course.is_active.is_(True),
            )
        )
    ).scalars().all()
    if not courses:
        return 0

    # 找出还没有词的课程
    empty_courses: list[Course] = []
    for course in courses:
        has = await db.execute(
            select(Word.id).where(Word.course_id == course.id).limit(1)
        )
        if has.scalar_one_or_none() is None:
            empty_courses.append(course)
    if not empty_courses:
        return 0

    # Step 1: upsert lexicon(按 spelling 全局唯一)
    for spelling, _, meaning_cn, phonetic, _, _, _ in KET_WORDS:
        spelling_lower = spelling.lower()
        stmt = (
            insert(Lexicon)
            .values(
                spelling=spelling_lower,
                phonetic=phonetic,
                meaning_cn=meaning_cn,
                first_letter=spelling_lower[0],
            )
            .on_conflict_do_nothing(index_elements=["spelling"])
        )
        await db.execute(stmt)
    await db.flush()

    # Step 2: 查 lexeme_id map
    lexeme_rows = (await db.execute(select(Lexicon))).scalars().all()
    lexeme_map = {lex.spelling: lex.id for lex in lexeme_rows}

    # Step 3: 给每门空课程插 words(带共享 lexeme_id)
    total = 0
    for course in empty_courses:
        db.add_all([
            Word(
                course_id=course.id,
                lexeme_id=lexeme_map.get(spelling.lower()),
                spelling=spelling,
                syllables=syllables,
                meaning_cn=meaning_cn,
                phonetic=phonetic,
                sample_sentence=sample_sentence,
                sample_sentence_translation=sample_sentence_translation,
                sort_order=sort_order,
            )
            for spelling, syllables, meaning_cn, phonetic, sample_sentence, sample_sentence_translation, sort_order in KET_WORDS
        ])
        total += len(KET_WORDS)
    await db.commit()
    return total
