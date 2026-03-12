1.单个对象转json
ShopType shop = ShopType.builder().id(1).name("meishi").sort(1).build();

String json = JSONUtil.toJSONStr(shop);

{"id":1,"name":"美食","sort":1}


List<ShopType> list = new ArrayList<>();
list.add(shop1);
list.add(shop2);

String jsonArray = JSONUtil.toJSONStr(list);

结果：[{"id":1,"name":"美食"},{"id":2,"name":"电影"}]


Map<String,Object> map = new HashMap<>();
map.put("name","张三");
map.put("age",13);
String jsonMap = JSONUtil.toJsonStr(map);

结果:{"name":"张三","age":18}

2. JSON->原来的类型

String json = "{\"id\":1,\"name\":\"美食\"}";
ShopType shop = JSONUtil.toBean(json,ShopType.class);


// 情况2：JSON字符串 → List列表
String jsonArray = "[{\"id\":1,\"name\":\"美食\"},{\"id\":2,\"name\":\"电影\"}]";
//先转JsonArray,再转List
JSONArray array = JSONUtil.parseArray(jsonArray);
List<ShopType> list = JSONUtil.toList(array,SHopType.class);


// 情况3：JSON字符串 → Map
String jsonMap = "{\"name\":\"张三\",\"age\":18}";
Map<String, Object> map = JSONUtil.toBean(jsonMap, Map.class);





