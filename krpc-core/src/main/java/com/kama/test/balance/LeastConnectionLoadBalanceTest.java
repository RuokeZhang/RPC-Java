package com.kama.test.balance;

import com.kama.client.servicecenter.balance.impl.LeastConnectionLoadBalance;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LeastConnectionLoadBalanceTest {

    private LeastConnectionLoadBalance loadBalance;

    @Before
    public void setUp() {
        loadBalance = new LeastConnectionLoadBalance();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBalance_WithEmptyList() {
        loadBalance.balance(Arrays.asList());
    }

    @Test
    public void testBalance_SelectsLeastConnections() {
        List<String> addresses = Arrays.asList("s1", "s2", "s3");

        // 首轮应依次挑选 s1、s2、s3（初始连接数全为 0）
        assertEquals("s1", loadBalance.balance(addresses));
        assertEquals("s2", loadBalance.balance(addresses));
        assertEquals("s3", loadBalance.balance(addresses));

        // 释放 s1，下一次应再次优先选择 s1
        loadBalance.releaseConnection("s1");
        assertEquals("s1", loadBalance.balance(addresses));
    }

    @Test
    public void testCleanupStaleNodes() {
        List<String> addresses = Arrays.asList("s1", "s2");
        loadBalance.balance(addresses); // 引入 s1、s2

        // 下一轮只保留 s2，确认不会因 s1 计数影响选择
        List<String> newList = Arrays.asList("s2");
        String selected = loadBalance.balance(newList);
        assertEquals("s2", selected);
        assertEquals(1, loadBalance.getConnectionCount("s2"));
        // s1 应该已被清理或不再影响
        assertTrue(loadBalance.getConnectionCount("s1") == 0);
    }
}

