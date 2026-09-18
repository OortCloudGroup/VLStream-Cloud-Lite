package com.ruoyi.wvp.conf;

import org.junit.Assert;
import org.junit.Test;

public class DynamicTaskTest {

    @Test
    public void destroyCancelsScheduledTasks() {
        DynamicTask dynamicTask = new DynamicTask();
        dynamicTask.DynamicTask();
        try {
            dynamicTask.startDelay("destroy-test", () -> { }, 60000);
            Assert.assertTrue(dynamicTask.contains("destroy-test"));
        } finally {
            dynamicTask.destroy();
        }
        Assert.assertFalse(dynamicTask.contains("destroy-test"));
        Assert.assertTrue(dynamicTask.getAllKeys().isEmpty());
    }
}
