# 高并发秒杀系统（Seckill System）

一个覆盖「用户 → 商品 → 秒杀 → 订单 → 搜索」完整业务闭环的高并发秒杀电商后端项目。
核心目标：**库存不超卖、系统不被打垮、数据最终一致**。

## 技术栈

| 分类 | 技术 |
|---|---|
| 语言/基础 | Java 21（JavaSE、JUC、JVM） |
| 框架 | Spring Boot 3.5、MyBatis-Plus 3.5 |
| 存储 | MySQL 8、Redis、Elasticsearch 8.15 + IK 中文分词 |
| 消息 | RocketMQ 5.3 |
| 构建 | Maven |

## 功能架构

```
浏览器/客户端
     │ HTTP
     ▼
SpringBoot 应用（8080）
     │
     ├── 用户模块   注册/登录（MD5+盐加密）
     ├── 商品模块   列表/详情（Redis 缓存：防穿透/击穿/雪崩）
     ├── 秒杀模块   Redis Lua 原子扣库存 + JUC 限流 + 防重复抢购
     ├── 订单模块   RocketMQ 异步建单 + 乐观锁扣 MySQL 库存
     └── 搜索模块   Elasticsearch + IK 分词（名称/描述全文检索）
```

## 核心设计亮点

1. **库存不超卖（双保险）**
   - 秒杀链路：Redis Lua 脚本「读库存-判断-扣减」整体原子执行，天然防超卖
   - 订单链路：MySQL 乐观锁 `UPDATE product SET stock = stock - 1 WHERE id = ? AND stock > 0` 兜底
   - 压测 50 并发抢 3 库存：成功恰为 3，库存归零不为负

2. **削峰填谷**
   - 抢购成功立即返回，订单创建通过 RocketMQ 异步消费，按消费能力建单，数据库不被瞬时打爆

3. **最终一致**
   - Redis 库存（秒杀期以 Redis 为准）+ RocketMQ 异步同步 MySQL 库存
   - 消费失败自动重试（RECONSUME_LATER），Redis/MySQL 库存最终收敛一致

4. **缓存三兄弟**
   - 穿透：缓存空值（3 分钟）
   - 击穿：Redis 互斥锁（SETNX + 双检 + 重试）
   - 雪崩：过期时间加随机值，错峰过期

5. **中文搜索**
   - Elasticsearch 索引使用 `ik_max_word` / `ik_smart` 分析器，支持「搜华为出华为手机」

6. **工程规范**
   - Controller-Service-Mapper 分层；统一返回 `Result{code,message,data}`；全局异常处理；密码加盐加密

## 快速开始

### 环境要求
- JDK 21、Maven（IDEA 自带）
- MySQL 8、Redis、RocketMQ 5.3、Elasticsearch 8.15 + IK 插件

### 1. 初始化数据库
```sql
CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4;
USE seckill;
-- 建表 SQL 见文末附录
```

### 2. 启动依赖服务（四件套）
| 服务 | 启动方式 | 端口 |
|---|---|---|
| MySQL | 安装为 Windows 服务，自动启动 | 3306 |
| Redis | `redis-server.exe` | 6379 |
| RocketMQ | 先 `mqnamesrv.cmd`，再 `mqbroker.cmd -n localhost:9876 autoCreateTopicEnable=true` | 9876 / 10911 |
| Elasticsearch | `elasticsearch.bat`（需 `xpack.security.enabled: false`） | 9200 |

> 提示：RocketMQ 与 ES 默认内存较大，学习环境建议调小（`runserver.cmd`/`runbroker.cmd` 改为 256m；ES `jvm.options` 改为 512m）。

### 3. 配置应用
`src/main/resources/application.properties` 中修改数据库密码为自己的 MySQL 密码。

### 4. 启动应用
在 IDEA 中运行 `SeckillApplication`，访问 `http://localhost:8080`。

### 5. 接口自测清单
```text
# 注册 / 登录
GET /user/register?username=bob&password=123456
GET /user/login?username=bob&password=123456

# 商品
GET /product/list
GET /product/1

# 秒杀（先预热库存，再抢购）
GET /seckill/prepare/1
GET /seckill/1?userId=1

# 搜索（先同步商品到 ES）
GET /search/sync
GET /search?keyword=华为
```

## 接口文档

| 接口 | 方法 | 说明 |
|---|---|---|
| `/user/register` | GET | 注册（用户名已存在返回 400） |
| `/user/login` | GET | 登录（成功返回用户信息，不含密码） |
| `/product/list` | GET | 商品列表 |
| `/product/{id}` | GET | 商品详情（Redis 缓存） |
| `/seckill/prepare/{productId}` | GET | 把 MySQL 库存预热到 Redis |
| `/seckill/{productId}` | GET | 秒杀抢购（Lua 原子扣库存） |
| `/search/sync` | GET | 同步全部商品到 ES |
| `/search?keyword=xxx` | GET | 全文搜索（名称+描述） |

统一返回格式：`{"code":200,"message":"成功","data":...}`；业务失败 `code=400`，系统异常 `code=500`。

## 压测结果

场景：50 个线程并发抢购 3 件库存商品（线程池 + CountDownLatch 发令枪）。

| 指标 | 结果 |
|---|---|
| 抢购成功 | 3 |
| 抢购失败 | 47 |
| Redis 库存 | 0（无负数，未超卖） |
| MySQL 库存 | 0（MQ 异步同步，最终一致） |
| 订单数 | 3 |

## 项目结构

```
com.seckill
├── SeckillApplication.java      启动类（@MapperScan）
├── common/                      统一返回、业务异常、全局异常处理
├── config/                      RocketMQ 生产者、ES JSON 映射
├── controller/                  Controller 层
├── entity/                      User / Product / Order 实体
├── mapper/                      MyBatis-Plus Mapper
├── mq/                          RocketMQ 消费者（异步建单）
├── service/                     业务层（接口 + 实现）
├── test/                        并发压测（SeckillStressTest）
└── util/                        密码工具（MD5 + 随机盐）
```

## JVM 调优

IDEA 运行配置 VM options：
```
-Xms256m -Xmx256m -Xlog:gc*
```
开启 GC 日志可观察年轻代回收，符合秒杀场景「短生命周期对象多」的特点。

## 演进规划

- [ ] 登录态（Redis Token）
- [ ] RocketMQ 延迟消息实现订单超时关单 + 库存回补
- [ ] Redisson 分布式锁（可重入 + 看门狗）
- [ ] 订单表分库分表
- [ ] 接口幂等与参数校验（@Valid）
- [ ] Docker 容器化部署 + CI/CD

## 附录：建表 SQL

```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `salt` VARCHAR(32) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `stock` INT NOT NULL,
  `description` VARCHAR(500) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(32) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `product_name` VARCHAR(100) NOT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
