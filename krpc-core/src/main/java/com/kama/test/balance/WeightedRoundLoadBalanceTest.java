package com.kama.test.balance;

import com.kama.client.servicecenter.balance.impl.WeightedRoundLoadBalance;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeightedRoundLoadBalanceTest {

    private WeightedRoundLoadBalance loadBalance;

    @Before
    public void setUp() {
        loadBalance = new WeightedRoundLoadBalance();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBalance_WithEmptyList() {
        loadBalance.balance(Arrays.asList());
    }

    @Test
    public void testBalance_WithWeightsTwoNodes() {
        String a = "serverA";
        String b = "serverB";
        loadBalance.setWeight(a, 3);
        loadBalance.setWeight(b, 1);

        List<String> addressList = Arrays.asList(a, b);
        int aCount = 0;
        int bCount = 0;

        // 8 次选择，平滑加权轮询在权重 3:1 下应形成 6:2 的分布
        for (int i = 0; i < 8; i++) {
            String selected = loadBalance.balance(addressList);
            if (selected.equals(a)) {
                aCount++;
            } else if (selected.equals(b)) {
                bCount++;
            }
        }

        assertEquals(6, aCount);
        assertEquals(2, bCount);
    }

    @Test
    public void testBalance_WeightFallbackToDefault() {
        // 未显式设置权重，默认 1:1
        String a = "serverA";
        String b = "serverB";
        List<String> addressList = Arrays.asList(a, b);

        boolean sawA = false;
        boolean sawB = false;
        for (int i = 0; i < 4; i++) {
            String selected = loadBalance.balance(addressList);
            if (selected.equals(a)) {
                sawA = true;
            }
            if (selected.equals(b)) {
                sawB = true;
            }
        }
        assertTrue(sawA);
        assertTrue(sawB);
    }
}

