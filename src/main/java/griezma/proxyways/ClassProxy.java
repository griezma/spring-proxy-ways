package griezma.proxyways;

import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.Unchecked;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ClassProxy {
    public static <T> T proxy(T real) {
        Callback callback = new MethodInterceptor() {
            private Map<List<Object>, Object> cache = new ConcurrentHashMap<>();

            private List<Object> getCacheKey(Method method, Object[] args) {
                List<Object> list = new ArrayList<>();
                list.add(method.getName());
                list.addAll(Arrays.asList(args));
                return list;
            }

            @Override
            public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
                log.debug("intercept {}({})", method.getName(), Arrays.toString(args));
                var key = getCacheKey(method, args);
                return cache.computeIfAbsent(key, Unchecked.function(u -> method.invoke(real, args)));
            }
        };
        return (T) Enhancer.create(real.getClass(), callback);
    }
}
