package com.jolumn.vtslkgs;

import com.jolumn.vtslkgs.util.Base62Util;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Base62UtilTest {

    @Test
    void randomKey_lengthIs6() {
        String key = Base62Util.randomKey(6);
        assertEquals(6, key.length());
    }

    @Test
    void randomKey_charsetIsValid() {
        String charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < 100; i++) {
            String key = Base62Util.randomKey(6);
            for (char c : key.toCharArray()) {
                assertTrue(charset.indexOf(c) >= 0, "Invalid char: " + c);
            }
        }
    }

    @Test
    void randomKey_1000KeysHaveHighUniqueness() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            keys.add(Base62Util.randomKey(6));
        }
        assertTrue(keys.size() > 990, "Expected >990 unique keys out of 1000, got " + keys.size());
    }

    @Test
    void randomKey_spaceIsLargerThan63() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            keys.add(Base62Util.randomKey(6));
        }
        assertTrue(keys.size() > 4900, "Go defect: only 63 possible keys. Java fix should produce >4900 unique out of 5000. Got " + keys.size());
    }
}
