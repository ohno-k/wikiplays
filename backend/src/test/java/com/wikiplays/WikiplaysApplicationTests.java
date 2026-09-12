package com.wikiplays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Bean 配線とセキュリティ設定が壊れていないことを確認する (Wikipedia へのアクセスは行わない)。 */
@SpringBootTest
@ActiveProfiles("test")
class WikiplaysApplicationTests {

    @Test
    void contextLoads() {
    }
}
