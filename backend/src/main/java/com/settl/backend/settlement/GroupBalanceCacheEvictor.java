package com.settl.backend.settlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.UUID;

import static com.settl.backend.config.CacheConfig.GROUP_BALANCES_CACHE;

@Component
public class GroupBalanceCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(GroupBalanceCacheEvictor.class);

    private final CacheManager cacheManager;

    public GroupBalanceCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evicts group_balances cache entry for groupId after current transaction commits.
     * If no transaction is active, evicts immediately.
     */
    public void evictGroupBalances(UUID groupId) {
        if (groupId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvict(groupId);
                }
            });
        } else {
            doEvict(groupId);
        }
    }

    /**
     * Evicts group_balances cache entries for multiple groupIds after current transaction commits.
     */
    public void evictGroupBalances(Collection<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (UUID id : groupIds) {
                        doEvict(id);
                    }
                }
            });
        } else {
            for (UUID id : groupIds) {
                doEvict(id);
            }
        }
    }

    /**
     * Clears all group_balances cache entries after current transaction commits.
     */
    public void clearAll() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doClear();
                }
            });
        } else {
            doClear();
        }
    }

    private void doEvict(UUID groupId) {
        try {
            Cache cache = cacheManager.getCache(GROUP_BALANCES_CACHE);
            if (cache != null) {
                cache.evict(groupId);
                log.debug("Evicted cache '{}' for groupId={}", GROUP_BALANCES_CACHE, groupId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache '{}' for groupId={}: {}", GROUP_BALANCES_CACHE, groupId, e.getMessage());
        }
    }

    private void doClear() {
        try {
            Cache cache = cacheManager.getCache(GROUP_BALANCES_CACHE);
            if (cache != null) {
                cache.clear();
                log.debug("Cleared cache '{}'", GROUP_BALANCES_CACHE);
            }
        } catch (Exception e) {
            log.warn("Failed to clear cache '{}': {}", GROUP_BALANCES_CACHE, e.getMessage());
        }
    }
}
