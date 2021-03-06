package com.nx.redis;

import com.nx.TestContextConfig;
import org.aspectj.lang.annotation.AfterThrowing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

import java.util.List;
import java.util.Set;

/**
 * Created by Neal on 2014/11/10.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
public class RedisTest {
    @Autowired
    RedisTemplate<Long, Long> redisTemplate;

    @Autowired
    JedisPool jedisPool;

    @Test
    public void jredis() throws InterruptedException {
        Jedis redis = new Jedis("localhost", 6379);
        redis.flushAll();
        redis.set("name", "neal");
        System.out.println(redis.get("name"));

//        redis.setex("ex1",3,"timeout");
//        Thread.sleep(4000);
//        System.out.println(redis.get("ex1"));

        redis.lpush("list1", "1");
        redis.lpush("list1", "2");
        redis.lpush("list1", "3");
        redis.lpush("list1", "4");
        redis.lpush("list1", "5");
        redis.lpush("list1", "6");
        System.out.println(redis.llen("list1"));


        Transaction multi = redis.multi();
        multi.rpush("list1", "7");
        multi.exec();
        System.out.println(redis.llen("list1"));
    }

    @Test
    public void test1Normal() {
        Jedis jedis = new Jedis("localhost");
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            String result = jedis.set("n" + i, "n" + i);
        }
        long end = System.currentTimeMillis();
        System.out.println("Simple SET: " + ((end - start) / 1000.0) + " seconds");
        jedis.disconnect();
    }

    @Test
    public void test3Pipelined() {
        Jedis jedis = new Jedis("localhost");
        Pipeline pipeline = jedis.pipelined();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            pipeline.set("p" + i, "p" + i);
        }
        List<Object> results = pipeline.syncAndReturnAll();
        long end = System.currentTimeMillis();
        System.out.println(results.size() + " Pipelined SET: " + ((end - start) / 1000.0) + " seconds");
        jedis.disconnect();
    }


    @Test
    public void test4combPipelineTrans() {
        Jedis jedis = new Jedis("localhost");
        long start = System.currentTimeMillis();
        Pipeline pipeline = jedis.pipelined();
        pipeline.multi();
        for (int i = 0; i < 100000; i++) {
            pipeline.set("" + i, "" + i);
        }
        pipeline.exec();
        List<Object> results = pipeline.syncAndReturnAll();
        long end = System.currentTimeMillis();
        System.out.println("Pipelined transaction: " + ((end - start) / 1000.0) + " seconds");
        jedis.disconnect();
    }


    @Test
    public void addRedisTemplateData() {

        for (int i = 0; i < 10000; i++) {
            redisTemplate.opsForList().rightPush(10086L, Integer.toUnsignedLong(i));
        }
        System.out.println(redisTemplate.opsForList().size(10086L));

    }


    @Test
    @AfterThrowing("JedisDataException")
    public void redisTemplate() throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            redisTemplate.opsForList().leftPop(10086L);
        }
        System.out.println(redisTemplate.opsForList().size(10086L));


        //error multi watch
//        redisTemplate.watch(1L);
//        redisTemplate.multi();
//        redisTemplate.opsForValue().set(1L,3L);
//        Thread.sleep(5000);
//        redisTemplate.exec();


//        SessionCallback<Long> sessionCallback = new SessionCallback<Long>() {
//            @Override
//            public Long execute(RedisOperations operations) throws DataAccessException {
//                operations.watch(1L);
//                operations.multi();
//                operations.opsForValue().set(1L, 2L);
//                try {
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                operations.exec();
//                return null;
//            }
//        };
        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.watch(1L);
                operations.multi();
                operations.opsForValue().set(1L, 2L);
//                try {
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                operations.exec();
                operations.unwatch();
                return null;
            }
        });

        Jedis jedis = new Jedis("localhost");
        Transaction multi = jedis.multi();
        multi.set("1", "6");
        multi.exec();
        System.out.println(jedis.get("1"));
    }

    @Test
    public void poolRedisTest() throws InterruptedException {
        for (int j = 0; j < 10; j++) {
            new Thread(() -> {
                Jedis redis = jedisPool.getResource();
                for (int i = 0; i < 10000; i++) {
                    redis.lpush(Thread.currentThread().getName(), String.valueOf(i));
                }
                System.out.println(Thread.activeCount());
                jedisPool.returnResource(redis);
            }).start();
        }
        Thread.sleep(100000);
    }

    @Test
    public void testJedisTemplate() throws InterruptedException {
        JedisTemplate<Long> jedisTemplate = new JedisTemplate<>(jedisPool);
        for (int j = 0; j < 10; j++) {
            new Thread(() -> {
                Long execute = jedisTemplate.execute(jedis -> {
                    for (int i = 0; i < 10000; i++) {
                        jedis.lpush(Thread.currentThread().getName(), String.valueOf(i));
                    }
                    System.out.println(jedis.llen(Thread.currentThread().getName()));
                    return 1L;
                });

            }).start();
        }
        Thread.sleep(100000);
    }

    @Test
    public void testJedisTemplate2() throws InterruptedException {
        JedisTemplate<Long> jedisTemplate = new JedisTemplate<>(jedisPool);
        for (int j = 0; j < 10; j++) {
            new Thread(() -> {
                jedisTemplate.execute(jedis -> {
                    for (int i = 0; i < 10000; i++) {
                        jedis.lpush(Thread.currentThread().getName(), String.valueOf(i));
                    }
                    System.out.println(jedis.llen(Thread.currentThread().getName()));
                    return null;
                });

            }).start();
        }
        Thread.sleep(100000);
    }

    @Test
    public void getAllKey() {
        JedisTemplate<Set<String>> jedisTemplate = new JedisTemplate<>(jedisPool);
        Set<String> execute = jedisTemplate.execute(jedis -> {
            Set<String> keys = jedis.keys("*");
            return keys;
        });
        execute.forEach(System.out::println);

        JedisTemplate<List<String>> jedisTemplate2 = new JedisTemplate<>(jedisPool);
        if (!execute.isEmpty()) {
            String key1 = execute.iterator().next();
            List<String> execute1 = jedisTemplate2.execute(jedis -> {
                List<String> lrange = jedis.lrange(key1, 0L, -1L);
                return lrange;
            });
            execute1.forEach(System.out::println);
        }
    }


}
