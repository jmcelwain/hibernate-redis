package org.hibernate.cache.redis;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.redis.util.RedisTool;
import org.hibernate.cache.spi.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis Client 입니다. <br/>
 * HINT: https://github.com/xetorthio/jedis/wiki/AdvancedUsage
 *
 * @author sunghyouk.bae@gmail.com
 * @since 13. 4. 5. 오후 1:30
 */
public class RedisClient extends RedisTemplate<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(RedisClient.class);
    private static final boolean isTranceEnabled = log.isTraceEnabled();
    private static final boolean isDebugEnabled = log.isDebugEnabled();

    @Getter
    @Setter
    private int expiry = 120;

    protected String getEntityName(Object key) {
        return ((CacheKey) key).getEntityOrRoleName();
    }

    public Long dbSize() {
        return execute(new RedisCallback<Long>() {
            @Override
            public Long doInRedis(RedisConnection connection) throws DataAccessException {
                Long dbSize = connection.dbSize();
                if (isTranceEnabled)
                    log.trace("db size 를 구했습니다. db size=[{}]", dbSize);
                return dbSize;
            }
        });
    }

    public boolean exists(final Object key) {
        boolean result = boundSetOps(getEntityName(key)).isMember(key);
        // boolean result = hasKey(key);
        if (isTranceEnabled)
            log.trace("exists key=[{}], result=[{}]", key, result);
        return result;
    }

    /**
     * Get value
     */
    public Object get(final Object key) {
        Object value = opsForValue().get(key);
        if (isTranceEnabled)
            log.trace("get key=[{}] ... return value=[{}]", key, value);
        return value;
    }

    public Set keysInRegion(String regionName) {
        if (isTranceEnabled)
            log.trace("get all keysInRegion in region [{}]", regionName);
        return boundSetOps(regionName).members();
    }

    /**
     * Multi Get
     */
    public List<Object> mget(final Collection<Object> keys) {
        if (isTranceEnabled)
            log.trace("multi get... keys=[{}]", keys);

        return opsForValue().multiGet(keys);
    }

    public void set(final Object key, final Object value) {
        set(key, value, expiry, TimeUnit.SECONDS);
    }

    public void set(final Object key, final Object value, final long timeout, final TimeUnit unit) {
        if (isTranceEnabled)
            log.trace("set key=[{}], value=[{}], timeout=[{}], unit=[{}]", key, value, timeout, unit);

        RedisTool.withinTx(this, new SessionCallback<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void execute(RedisOperations operations) throws DataAccessException {
                boundSetOps(getEntityName(key)).add(key);
                boundValueOps(key).set(value, timeout, unit);
                return null;
            }
        });

    }

    @Override
    public void delete(final Object key) {
        if (isTranceEnabled)
            log.trace("delete... key=[{}]", key);

        RedisTool.withinTx(this, new SessionCallback<Void>() {
            @Override
            @SuppressWarnings("unchecked")
            public Void execute(RedisOperations operations) throws DataAccessException {
                operations.boundSetOps(getEntityName(key)).remove(key);
                operations.delete(key);
                return null;
            }
        });
    }

    /**
     * Region 에 해당하는 모든 항목을 삭제합니다.
     */
    public void deleteRegion(final String regionName) throws CacheException {
        if (isDebugEnabled)
            log.debug("Region을 Clear 합니다... Region=[{}]", regionName);

        try {
            RedisTool.withinTx(this, new SessionCallback<Void>() {
                @Override
                @SuppressWarnings("unchecked")
                public Void execute(RedisOperations operations) throws DataAccessException {
                    Set keys = operations.boundSetOps(regionName).members();
                    operations.delete(keys);
                    return null;
                }
            });
            if (isDebugEnabled)
                log.debug("Region을 Clear 했습니다. Region=[{}]", regionName);
        } catch (Exception e) {
            log.error("Region을 삭제하는데 실패했습니다.", e);
            throw new CacheException(e);
        }
    }

    public void flushDb() {
        if (isDebugEnabled)
            log.debug("DB 전체를 (영역에 상관없이) Flush 합니다...");

        execute(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                connection.flushDb();
//                Jedis jedis = (Jedis) connection.getNativeConnection();
//                jedis.flushDB();
                return null;
            }
        });
    }
}
