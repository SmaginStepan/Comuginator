// Source of the starter conversation vocabulary (original work, not copied from any AAC app).
// Line format:  [C] es | en | ru | q=search override for ARASAAC (optional)
// C = word class colour (Modified Fitzgerald Key idea): P people/pronoun, V verb, N noun,
//     D descriptor, A adverb, R preposition, Q question, G negation, S social, M misc.
// Home cells have a fixed position: "row,col [C] es | en | ru".

export const COLORS = {
  P: "PRONOUN", V: "VERB", N: "NOUN", D: "DESCRIPTOR", A: "ADVERB",
  R: "PREPOSITION", Q: "QUESTION", G: "NEGATION", S: "SOCIAL", M: "MISC",
};

export const HOME = { cols: 10, rows: 7 };

// ── Core words at fixed positions on the home page (rows 0-4) ──────────────
export const CORE = `
0,0 [P] yo | I | я
1,0 [P] tú | you | ты
2,0 [P] él | he | он
3,0 [P] ella | she | она
4,0 [P] nosotros | we | мы

0,1 [V] querer | want | хотеть
0,2 [V] ir | go | идти
0,3 [V] hacer | do | делать
1,1 [V] tener | have | иметь
1,2 [V] estar | be (state) | находиться
1,3 [V] ser | be | быть
2,1 [V] poder | can | мочь
2,2 [V] gustar | like | нравиться
2,3 [V] necesitar | need | нужно
3,1 [V] ver | see | видеть
3,2 [V] dar | give | дать
3,3 [V] ayudar | help | помогать
4,1 [V] comer | eat | есть
4,2 [V] beber | drink | пить
4,3 [V] jugar | play | играть

0,4 [Q] qué | what | что
0,5 [Q] quién | who | кто
0,6 [Q] dónde | where | где
1,4 [Q] cuándo | when | когда
1,5 [Q] por qué | why | почему
1,6 [Q] cómo | how | как
2,4 [A] aquí | here | здесь
2,5 [A] allí | there | там
2,6 [A] ahora | now | сейчас
3,4 [R] en | in | в
3,5 [R] con | with | с
3,6 [R] para | for | для
4,4 [M] y | and | и
4,5 [R] sin | without | без
4,6 [A] otra vez | again | ещё раз

0,7 [G] no | no | нет
0,8 [S] sí | yes | да
0,9 [D] más | more | больше
1,7 [S] ya está | all done | готово
1,8 [G] parar | stop | стоп
1,9 [S] por favor | please | пожалуйста
2,7 [D] bien | good | хорошо
2,8 [D] mal | bad | плохо
2,9 [S] gracias | thank you | спасибо
3,7 [D] grande | big | большой
3,8 [D] pequeño | small | маленький
3,9 [S] hola | hello | привет
4,7 [D] mucho | a lot | много
4,8 [D] poco | a little | мало
4,9 [S] adiós | goodbye | пока
`;

// ── Category folders (rows 5-6 of the home page), each with its own page ───
// key | position | es | en | ru | default colour
export const FOLDERS = [
  { key: "gente", at: "5,0", es: "Gente", en: "People", ru: "Люди", color: "P", words: `
mamá | mom | мама
papá | dad | папа
abuela | grandma | бабушка
abuelo | grandpa | дедушка
hermano | brother | брат
hermana | sister | сестра
bebé | baby | малыш
niño | boy | мальчик
niña | girl | девочка
hombre | man | мужчина
mujer | woman | женщина
amigo | friend | друг
profesor | teacher | учитель
compañero | classmate | одноклассник
médico | doctor | врач
policía | police officer | полицейский
bombero | firefighter | пожарный
familia | family | семья
vecino | neighbor | сосед
tío | uncle | дядя
tía | aunt | тётя
primo | cousin | двоюродный брат
` },
  { key: "cosas", at: "5,1", es: "Cosas", en: "Things", ru: "Вещи", color: "N", words: `
pelota | ball | мяч
libro | book | книга
coche | car | машина
teléfono | phone | телефон
tableta | tablet | планшет
televisión | TV | телевизор
ordenador | computer | компьютер
juguete | toy | игрушка
muñeca | doll | кукла
peluche | stuffed toy | мягкая игрушка
cama | bed | кровать
silla | chair | стул
mesa | table | стол
mochila | backpack | рюкзак
llave | key | ключ
dinero | money | деньги
lápiz | pencil | карандаш
papel | paper | бумага
cuento | story | сказка
foto | photo | фото
regalo | gift | подарок
reloj | clock | часы
luz | light | свет
puerta | door | дверь
ventana | window | окно
paraguas | umbrella | зонт
bicicleta | bike | велосипед
` },
  { key: "comida", at: "5,2", es: "Comida", en: "Food", ru: "Еда", color: "N", words: `
agua | water | вода
leche | milk | молоко
zumo | juice | сок
pan | bread | хлеб
galleta | cookie | печенье
fruta | fruit | фрукты
manzana | apple | яблоко
plátano | banana | банан
naranja | orange | апельсин | q=naranja fruta
uvas | grapes | виноград
fresa | strawberry | клубника
queso | cheese | сыр
jamón | ham | ветчина
huevo | egg | яйцо
pollo | chicken | курица
carne | meat | мясо
pescado | fish | рыба
pasta | pasta | макароны
arroz | rice | рис
sopa | soup | суп
verduras | vegetables | овощи
patata | potato | картошка
tomate | tomato | помидор
zanahoria | carrot | морковь
helado | ice cream | мороженое
chocolate | chocolate | шоколад
pizza | pizza | пицца
yogur | yogurt | йогурт
cereales | cereal | хлопья
desayuno | breakfast | завтрак
comida | lunch | обед | q=almuerzo
cena | dinner | ужин
` },
  { key: "sitios", at: "5,3", es: "Sitios", en: "Places", ru: "Места", color: "N", words: `
casa | home | дом
colegio | school | школа
parque | park | парк
tienda | shop | магазин
supermercado | supermarket | супермаркет
hospital | hospital | больница
baño | bathroom | туалет
cocina | kitchen | кухня
habitación | bedroom | спальня | q=dormitorio
salón | living room | гостиная
calle | street | улица
playa | beach | пляж
piscina | pool | бассейн
campo | countryside | деревня
jardín | garden | сад
biblioteca | library | библиотека
restaurante | restaurant | ресторан
cine | cinema | кино
zoo | zoo | зоопарк
farmacia | pharmacy | аптека
aeropuerto | airport | аэропорт
autobús | bus | автобус
tren | train | поезд
avión | plane | самолёт
ciudad | city | город
` },
  { key: "acciones", at: "5,4", es: "Acciones", en: "Actions", ru: "Действия", color: "V", words: `
abrir | open | открыть
cerrar | close | закрыть
poner | put | положить
quitar | take off | снять
coger | take | взять
dejar | leave | оставить
venir | come | прийти
salir | go out | выйти
entrar | go in | войти
sentarse | sit down | сесть
levantarse | stand up | встать
correr | run | бежать
andar | walk | ходить
saltar | jump | прыгать
dormir | sleep | спать
lavar | wash | мыть
limpiar | clean | убирать
vestirse | get dressed | одеваться
cantar | sing | петь
bailar | dance | танцевать
leer | read | читать
escribir | write | писать
dibujar | draw | рисовать
pintar | paint | красить
cortar | cut | резать
escuchar | listen | слушать
mirar | look | смотреть
esperar | wait | ждать
empezar | start | начать
terminar | finish | закончить
llevar | carry | нести
tirar | throw | бросить
empujar | push | толкать
abrazar | hug | обнимать
pensar | think | думать
saber | know | знать
entender | understand | понимать
decir | say | сказать
llamar | call | звонить
comprar | buy | купить
cocinar | cook | готовить
llorar | cry | плакать
reír | laugh | смеяться
` },
  { key: "describir", at: "5,5", es: "Describir", en: "Describe", ru: "Описать", color: "D", words: `
caliente | hot | горячий
frío | cold | холодный
rápido | fast | быстрый
lento | slow | медленный
bonito | pretty | красивый
feo | ugly | некрасивый
limpio | clean | чистый
sucio | dirty | грязный
nuevo | new | новый
viejo | old | старый
fuerte | strong | сильный
bueno | good | хороший
malo | bad | плохой
igual | same | такой же
diferente | different | разный
lleno | full | полный
vacío | empty | пустой
todo | all | всё
nada | nothing | ничего
otro | other | другой
alto | tall | высокий
bajo | short | низкий
duro | hard | твёрдый
blando | soft | мягкий
mojado | wet | мокрый
seco | dry | сухой
abierto | open | открытый
cerrado | closed | закрытый
arriba | up | вверх
abajo | down | вниз
dentro | inside | внутри
fuera | outside | снаружи
cerca | near | близко
lejos | far | далеко
fácil | easy | лёгкий
difícil | difficult | трудный
ruidoso | loud | громкий
callado | quiet | тихий
` },
  { key: "hablar", at: "5,6", es: "Hablar", en: "Talk", ru: "Разговор", color: "S", words: `
hola | hello | привет
adiós | goodbye | пока
buenos días | good morning | доброе утро
buenas noches | good night | спокойной ночи
por favor | please | пожалуйста
gracias | thank you | спасибо
perdón | sorry | извини
de nada | you're welcome | не за что
sí | yes | да
no | no | нет
no sé | I don't know | не знаю
mira | look! | смотри
escucha | listen! | слушай
espera | wait! | подожди
ven aquí | come here | иди сюда
me gusta | I like it | мне нравится
no me gusta | I don't like it | мне не нравится
quiero | I want | я хочу
no quiero | I don't want | я не хочу
otra vez | again | ещё раз
más despacio | slower | медленнее
repite | say it again | повтори
es mi turno | my turn | моя очередь | q=mi turno
es tu turno | your turn | твоя очередь | q=tu turno
enhorabuena | congratulations | поздравляю
te quiero | I love you | я тебя люблю
qué bien | great | здорово
` },
  { key: "ayuda", at: "5,7", es: "Ayuda", en: "Help", ru: "Помощь", color: "S", words: `
necesito ayuda | I need help | мне нужна помощь | q=ayuda
me duele | it hurts | мне больно | q=dolor
dolor de cabeza | headache | болит голова
dolor de barriga | tummy ache | болит живот
tengo hambre | I'm hungry | я хочу есть | q=hambre
tengo sed | I'm thirsty | я хочу пить | q=sed
tengo sueño | I'm sleepy | я хочу спать | q=sueño
tengo frío | I'm cold | мне холодно | q=frío
tengo calor | I'm hot | мне жарко | q=calor
quiero ir al baño | I need the bathroom | мне нужно в туалет | q=ir al baño
medicina | medicine | лекарство
médico | doctor | врач
enfermo | sick | больной
me he hecho daño | I hurt myself | я ушибся | q=herida
no me encuentro bien | I feel unwell | мне плохо | q=malestar
estoy perdido | I'm lost | я потерялся | q=perdido
no entiendo | I don't understand | я не понимаю
déjame | leave me alone | оставь меня | q=dejar en paz
quiero descansar | I want to rest | я хочу отдохнуть | q=descansar
tengo miedo | I'm scared | мне страшно | q=miedo
` },
  { key: "colegio", at: "5,8", es: "Colegio", en: "School", ru: "Школа", color: "N", words: `
profesor | teacher | учитель
compañero | classmate | одноклассник
clase | class | класс
mochila | backpack | рюкзак
libro | book | учебник
cuaderno | notebook | тетрадь
lápiz | pencil | карандаш
bolígrafo | pen | ручка
goma | eraser | ластик
regla | ruler | линейка
tijeras | scissors | ножницы
pegamento | glue | клей
pintura | paint | краски
lápices de colores | crayons | цветные карандаши
folio | sheet of paper | лист
pizarra | board | доска
ordenador | computer | компьютер
recreo | break | перемена
deberes | homework | домашнее задание
examen | test | контрольная
contar | count | считать
número | number | число
letra | letter | буква
patio | playground | двор
comedor | canteen | столовая
autobús escolar | school bus | школьный автобус
` },
  { key: "actividades", at: "5,9", es: "Actividades", en: "Activities", ru: "Занятия", color: "V", words: `
jugar | play | играть
dibujar | draw | рисовать
pintar | paint | красить
cantar | sing | петь
bailar | dance | танцевать
leer | read | читать
ver la tele | watch TV | смотреть телевизор | q=ver la televisión
[N] música | music | музыка
[N] fútbol | soccer | футбол
[N] baloncesto | basketball | баскетбол
[N] bicicleta | bike | велосипед
nadar | swim | плавать
[N] columpio | swing | качели
[N] tobogán | slide | горка
[N] puzzle | puzzle | пазл
[N] construcciones | blocks | конструктор
cocinar | cook | готовить
pasear | go for a walk | гулять
[N] cuento | story | сказка
[N] película | movie | фильм
[N] juego de mesa | board game | настольная игра
[N] videojuego | video game | видеоигра
[N] manualidades | crafts | поделки
[N] plastilina | play dough | пластилин
[N] burbujas | bubbles | пузыри
` },
  { key: "estados", at: "6,0", es: "Estados", en: "Feelings", ru: "Чувства", color: "D", words: `
contento | happy | весёлый
triste | sad | грустный
enfadado | angry | злой
asustado | scared | испуганный
cansado | tired | уставший
aburrido | bored | скучно
nervioso | nervous | нервничаю
sorprendido | surprised | удивлён
enfermo | sick | больной
hambre | hungry | голодный | q=tener hambre
sed | thirsty | хочу пить | q=tener sed
sueño | sleepy | сонный | q=tener sueño
tranquilo | calm | спокойный
avergonzado | embarrassed | стесняюсь
solo | lonely | одинокий | q=soledad
orgulloso | proud | горжусь
preocupado | worried | беспокоюсь
celoso | jealous | ревную
emocionado | excited | взволнован
frustrado | frustrated | разочарован
con dolor | in pain | больно | q=dolor
` },
  { key: "diversion", at: "6,1", es: "Diversión", en: "Fun", ru: "Веселье", color: "N", words: `
globo | balloon | шарик
fiesta | party | праздник
cumpleaños | birthday | день рождения
regalo | gift | подарок
tarta | cake | торт
vela | candle | свеча
canción | song | песня
baile | dance | танец
payaso | clown | клоун
circo | circus | цирк
magia | magic | фокус
feria | fair | ярмарка
parque de atracciones | amusement park | парк аттракционов
disfraz | costume | костюм
juegos | games | игры
cine | cinema | кино
` },
  { key: "tiempo", at: "6,2", es: "Tiempo", en: "Time", ru: "Время", color: "A", words: `
hoy | today | сегодня
ayer | yesterday | вчера
mañana | tomorrow | завтра | q=mañana día
ahora | now | сейчас
después | later | потом
antes | before | раньше
por la mañana | morning | утро | q=mañana parte del día
tarde | afternoon | день | q=tarde parte del día
noche | night | ночь
día | day | сутки | q=día
semana | week | неделя
mes | month | месяц
año | year | год
lunes | Monday | понедельник
martes | Tuesday | вторник
miércoles | Wednesday | среда
jueves | Thursday | четверг
viernes | Friday | пятница
sábado | Saturday | суббота
domingo | Sunday | воскресенье
primero | first | сначала
luego | then | затем
siempre | always | всегда
nunca | never | никогда
pronto | soon | скоро
calendario | calendar | календарь
[N] reloj | clock | часы
[N] hora | time | время | q=hora
[N] primavera | spring | весна
[N] verano | summer | лето
[N] otoño | autumn | осень
[N] invierno | winter | зима
[N] sol | sun | солнце
[N] lluvia | rain | дождь
[N] viento | wind | ветер
[N] nieve | snow | снег
[N] nube | cloud | облако
` },
  { key: "palabritas", at: "6,3", es: "Palabritas", en: "Little words", ru: "Слова-связки", color: "M", words: `
y | and | и
o | or | или
pero | but | но
porque | because | потому что
si | if | если
[R] de | of | из
[R] a | to | к
[R] en | in | в
[R] con | with | с
[R] sin | without | без
[R] para | for | для
[R] por | by | по
que | that | что | q=que conjunción
este | this | этот
ese | that | тот
mío | mine | мой
tuyo | yours | твой
[A] muy | very | очень
[A] también | also | тоже
[A] ya | already | уже
[A] todavía | still | ещё
[A] solo | only | только | q=solamente
[A] casi | almost | почти
[R] entre | between | между
[R] sobre | on | на
[R] debajo | under | под
[R] detrás | behind | за
[R] delante | in front | перед
` },
  { key: "preguntas", at: "6,4", es: "Preguntas", en: "Questions", ru: "Вопросы", color: "Q", words: `
qué | what | что
quién | who | кто
dónde | where | где
cuándo | when | когда
por qué | why | почему
cómo | how | как
cuál | which | какой
cuánto | how much | сколько
para qué | what for | зачем
qué es esto | what is this | что это | q=qué es esto
puedo | can I | можно | q=poder
me ayudas | will you help me | поможешь | q=ayudar
` },
  { key: "cuerpo", at: "6,5", es: "Cuerpo", en: "Body", ru: "Тело", color: "N", words: `
cabeza | head | голова
ojos | eyes | глаза
nariz | nose | нос
boca | mouth | рот
orejas | ears | уши
pelo | hair | волосы
cara | face | лицо
cuello | neck | шея
hombro | shoulder | плечо
brazo | arm | рука
mano | hand | ладонь
dedo | finger | палец
barriga | tummy | живот
espalda | back | спина
pierna | leg | нога
rodilla | knee | колено
pie | foot | стопа
dientes | teeth | зубы
lengua | tongue | язык
corazón | heart | сердце
piel | skin | кожа
garganta | throat | горло
` },
  { key: "ropa", at: "6,6", es: "Ropa", en: "Clothes", ru: "Одежда", color: "N", words: `
camiseta | T-shirt | футболка
pantalón | trousers | штаны
vestido | dress | платье
falda | skirt | юбка
jersey | sweater | свитер
chaqueta | jacket | куртка
abrigo | coat | пальто
zapatos | shoes | обувь
zapatillas | sneakers | кроссовки
calcetines | socks | носки
ropa interior | underwear | бельё
pijama | pajamas | пижама
gorro | hat | шапка
gafas | glasses | очки
guantes | gloves | перчатки
bufanda | scarf | шарф
bañador | swimsuit | плавки
botas | boots | сапоги
cinturón | belt | ремень
pañal | diaper | подгузник
[V] cambiarse | change clothes | переодеться
` },
  { key: "animales", at: "6,7", es: "Animales", en: "Animals", ru: "Животные", color: "N", words: `
perro | dog | собака
gato | cat | кошка
pájaro | bird | птица
pez | fish | рыба
caballo | horse | лошадь
vaca | cow | корова
cerdo | pig | свинья
oveja | sheep | овца
gallina | hen | курица
conejo | rabbit | кролик
tortuga | turtle | черепаха
león | lion | лев
elefante | elephant | слон
mono | monkey | обезьяна
oso | bear | медведь
serpiente | snake | змея
mariposa | butterfly | бабочка
abeja | bee | пчела
araña | spider | паук
pato | duck | утка
ratón | mouse | мышь
jirafa | giraffe | жираф
delfín | dolphin | дельфин
` },
  { key: "casa", at: "6,8", es: "Casa", en: "Home", ru: "Дом", color: "N", words: `
sofá | sofa | диван
cama | bed | кровать
mesa | table | стол
silla | chair | стул
nevera | fridge | холодильник
lavadora | washing machine | стиральная машина
ducha | shower | душ
bañera | bath | ванна
inodoro | toilet | унитаз
lavabo | sink | раковина
cepillo de dientes | toothbrush | зубная щётка
pasta de dientes | toothpaste | зубная паста
toalla | towel | полотенце
jabón | soap | мыло
peine | comb | расчёска
espejo | mirror | зеркало
lámpara | lamp | лампа
alfombra | rug | ковёр
escalera | stairs | лестница
horno | oven | духовка
plato | plate | тарелка
vaso | glass | стакан
cuchara | spoon | ложка
tenedor | fork | вилка
cuchillo | knife | нож
taza | cup | чашка
botella | bottle | бутылка
basura | trash | мусор
` },
  { key: "colores", at: "6,9", es: "Colores y números", en: "Colors & numbers", ru: "Цвета и числа", color: "D", words: `
rojo | red | красный
azul | blue | синий
amarillo | yellow | жёлтый
verde | green | зелёный
naranja | orange | оранжевый | q=color naranja
rosa | pink | розовый | q=color rosa
morado | purple | фиолетовый
marrón | brown | коричневый
negro | black | чёрный
blanco | white | белый
gris | grey | серый
[N] círculo | circle | круг
[N] cuadrado | square | квадрат
[N] triángulo | triangle | треугольник
[N] rectángulo | rectangle | прямоугольник
[N] estrella | star | звезда
[N] corazón | heart | сердце
[N] uno | one | один
[N] dos | two | два
[N] tres | three | три
[N] cuatro | four | четыре
[N] cinco | five | пять
[N] seis | six | шесть
[N] siete | seven | семь
[N] ocho | eight | восемь
[N] nueve | nine | девять
[N] diez | ten | десять
` },
];
