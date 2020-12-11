package griezma.proxyways;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jooq.lambda.Unchecked;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@SpringBootApplication
@EnableCaching
public class ProxywaysApplication {

	public static void main(String[] args) {
		log.debug("running");
		SpringApplication.run(ProxywaysApplication.class, args);
	}
}

@Slf4j
@RequiredArgsConstructor
@Component
class Runner implements CommandLineRunner {
	final ExpensiveOpsFacade opsFacade;
	final ExpensiveOpsLogged opsLogged;
	final ExpensiveOpsCached opsCached;

	@Autowired @WithCache
	IExpensiveOps opsDecorated;

	ExpensiveOps opsClassProxy = ClassProxy.proxy(new ExpensiveOps());

	final ExpensiveOpsBppCached opsBppCached;

	public void run(String... args) {
		runOps(opsDecorated, "with cache decorator");
		runOps(opsFacade, "with class interceptor");
		runOps(opsLogged, "with method interceptor");
		runOps(opsCached, "Cacheable");
		runOps(opsClassProxy, "class proxy");
		runOps(opsBppCached, "Bean post processed proxy");
	}

	void runOps(IExpensiveOps ops, String description) {
		log.debug("\n\n=== Running {} ({}) ====\n", description, ops.getClass().getSimpleName());
		log.debug("----CPU expensive");
		long number = 1000000005721L;
		log.debug("{} is prime ?", number);
		log.debug("Got: {}\n", ops.isPrime(number));
		log.debug("{} is prime ?", number);
		log.debug("Got: {}\n", ops.isPrime(number));
		log.debug("{} is prime ?", number + 2);
		log.debug("Got: {}\n", ops.isPrime(number + 2));

		log.debug("----IO expensive");
		Path path = Paths.get("");

		log.debug("Get files hash {}", path.toAbsolutePath().normalize());
		log.debug("MD5: {}", ops.hashAllFiles(path));
		log.debug("Get files hash {}", path.toAbsolutePath().normalize());
		log.debug("MD5: {}", ops.hashAllFiles(path));

		path = Paths.get("src");
		log.debug("Get files hash {}", path.toAbsolutePath().normalize());
		log.debug("MD5: {}", ops.hashAllFiles(path));
	}
}

interface IExpensiveOps {
	boolean isPrime(long number);
	String hashAllFiles(Path folder);
}

@Slf4j
@Service @Primary
class ExpensiveOps implements IExpensiveOps {

	@Override
	@Timed
	public boolean isPrime(final long number) {
		log.debug("Compute isPrime({})", number);
		long before = System.nanoTime();
		try {
			BigInteger n = BigInteger.valueOf(number);

			if (n.compareTo(BigInteger.valueOf(3)) <= 0) {
				log.debug("leq 3");
				return true;
			}

			var remainder = n.remainder(BigInteger.TWO);

			if (remainder.equals(BigInteger.ZERO)) {
				log.debug("divisor 2 remainder eq 0");
				return false;
			}

			BigInteger divisor;

			for (divisor = BigInteger.valueOf(3);
				 divisor.compareTo(n.divide(divisor)) < 0;
				 divisor = divisor.add(BigInteger.TWO)) {
				 if (n.remainder(divisor).equals(BigInteger.ZERO)) {
					log.debug("with divisor {} remainder eq 0", divisor);
					return false;
				 }
			}
			log.debug("found, {} (divisor) gte {} (number by divisor)", divisor, n.divide(divisor));
			return true;
		} finally {
			long duration = System.nanoTime() - before;
			log.debug("Inner duration isPrime: {}mu", duration / 1000);
		}
	}

	@Override
	@SneakyThrows
	@Timed
	public String hashAllFiles(Path folder) {
		log.debug("Compute hashAllFiles({})", folder);
		long before = System.nanoTime();
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			Files.walk(folder)
					//					.peek(System.out::println)
					.filter(Files::isRegularFile)
					.filter(Files::isReadable)
					.filter(p -> !p.getFileName().toString().endsWith("lock"))
					.map(Unchecked.function(Files::readAllBytes))
					.forEach(md::update);

			return BytesToHex.bytesToHex(md.digest());
		} finally {
			long duration = System.nanoTime() - before;
			log.debug("Inner duration hashAllFiles(): {}mu", duration / 1000);
		}
	}
}

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@interface WithCache {}

@Service @WithCache
@RequiredArgsConstructor
class ExpensiveOpsWithCache implements IExpensiveOps {

	final ExpensiveOps delegate;

	private final Map<Long, Boolean> primeCache = new ConcurrentHashMap<>();

	@Override @Logged
	public boolean isPrime(long number) {
		return primeCache.computeIfAbsent(number, delegate::isPrime);
	}

	@Override
	public String hashAllFiles(Path folder) {
		return delegate.hashAllFiles(folder);
	}
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@interface Facade {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Logged {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
@interface Timed {}

@Slf4j
@Aspect
@Component
class MyAspects {
	@Around("execution( * *(..)) && within(griezma.proxyways.Facade)")
	public Object executeAroundWithinFacade(ProceedingJoinPoint jp) throws Throwable {
		log.debug("Intercepted within Facade {}({})", jp.getSignature().getName(), Arrays.toString(jp.getArgs()));
		return jp.proceed();
	}

	@Around("execution( * *(..)) && @annotation(griezma.proxyways.Logged)")
	public Object executeAroundLogged(ProceedingJoinPoint jp) throws Throwable {
		log.debug("Intercepted Logged {}({})", jp.getSignature().getName(), Arrays.toString(jp.getArgs()));
		return jp.proceed();
	}

	@Around("execution( * *(..)) && @annotation(griezma.proxyways.Timed)")
	public Object executeAroundTimed(ProceedingJoinPoint jp) throws Throwable {
		log.debug("Intercepted Timed {}({})", jp.getSignature().getName(), Arrays.toString(jp.getArgs()));
		long before = System.nanoTime();
		Object result = jp.proceed();
		long duration = System.nanoTime() - before;
		log.debug("{}({}) took {}mu", jp.getSignature().getName(), Arrays.toString(jp.getArgs()), duration / 1_000);
		return result;
	}
}

@Facade
class ExpensiveOpsFacade extends ExpensiveOps {
	@Override @Timed
	public boolean isPrime(long number) {
		return super.isPrime(number);
	}

	@Override @Timed
	public String hashAllFiles(Path folder) {
		return super.hashAllFiles(folder);
	}
}

@Component
class ExpensiveOpsLogged extends ExpensiveOps {
	@Override
	@Logged @Timed
	public boolean isPrime(long number) {
		return super.isPrime(number);
	}
}

@Component
class ExpensiveOpsCached extends ExpensiveOps {
	@Override
	@Cacheable("primes")
	public boolean isPrime(long number) {
		return super.isPrime(number);
	}

	@Override
	@Cacheable("files")
	public String hashAllFiles(Path folder) {
		return super.hashAllFiles(folder);
	}
}

@Component
@WithCacheBpp
class ExpensiveOpsBppCached extends ExpensiveOps {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface WithCacheBpp {}

@Slf4j
@Configuration
class CacheCAugmentation {
	@Bean @Logged
	public BeanPostProcessor cacheAugmentor() {
		return new BeanPostProcessor() {
			@Override @Logged
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if (bean.getClass().isAnnotationPresent(WithCacheBpp.class)) {
					log.debug("Wrapping in a proxy " + bean);
					return ClassProxy.proxy(bean);
				} else {
					return bean;
				}
			}
		};
	}
}

