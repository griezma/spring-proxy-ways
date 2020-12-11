package griezma.proxyways;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassProxyTest {

    @Test
    void shouldProxyAClass() {
        ExpensiveOps proxyOps = ClassProxy.proxy(new ExpensiveOps());
        assertTrue(proxyOps.isPrime(5));

    }
}
