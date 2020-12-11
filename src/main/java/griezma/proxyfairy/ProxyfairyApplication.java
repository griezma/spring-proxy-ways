package griezma.proxyfairy;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jooq.lambda.Unchecked;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

@Slf4j
@SpringBootApplication
@EnableCaching
public class ProxyfairyApplication {

	public static void main(String[] args) {
		log.info("running");
		SpringApplication.run(ProxyfairyApplication.class, args);
	}
}

@Slf4j
@RequiredArgsConstructor
@Component
class Runner implements CommandLineRunner {
	final ExpensiveOpsFacade opsFacade;
	final ExpensiveOpsLogged opsLogged;
	final ExpensiveOpsCached opsCached;

	ExpensiveOps classProxyOps = ClassProxy.proxy(new ExpensiveOps());

	public void run(String... args) {
		runOps(opsFacade, "with class interceptor");
		runOps(opsLogged, "with method interceptor");
		runOps(opsCached, "Cacheable");
		//runOps(classProxyOps, "class proxy");
	}

	void runOps(ExpensiveOps ops, String description) {
		log.debug("\n\n=== Running {} ({}) ====\n", description, ops.getClass().getSimpleName());
		log.debug("----CPU expensive");
		long number = 1000000005721L;
		log.info("{} is prime ?", number);
		log.info("Got: {}\n", ops.isPrime(number));
		log.info("{} is prime ?", number);
		log.info("Got: {}\n", ops.isPrime(number));
		log.info("{} is prime ?", number + 2);
		log.info("Got: {}\n", ops.isPrime(number + 2));

		log.info("----IO expensive");
		Path path = Paths.get("");

		log.debug("Get files hash {}", path.toAbsolutePath().normalize());
		log.info("MD5: {}", ops.hashAllFiles(path));
		log.debug("Get files hash {}", path.toAbsolutePath().normalize());
		log.info("MD5: {}", ops.hashAllFiles(path));

		path = Paths.get("src");
		log.debug("Get files hash {}", path.toAbsolutePath().normalize());
		log.info("MD5: {}", ops.hashAllFiles(path));
	}
}

@Slf4j
class ExpensiveOps {

	@Timed
	public boolean isPrime(final long number) {
		log.info("Compute isPrime({})", number);
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
			log.debug("Inner duration isPrime: {}mms", duration / 1000);
		}
	}

	@SneakyThrows
	@Timed
	public String hashAllFiles(Path folder) {
		log.info("Compute hashAllFiles({})", folder);
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
			log.debug("Inner duration hashAllFiles(): {}mms", duration / 1000);
		}
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
class MyAspect {
	@Around("execution( * *(..)) && within(griezma.proxyfairy.Facade)")
	public Object executeAroundWithinFacade(ProceedingJoinPoint jp) throws Throwable {
		log.debug("Intercepted within Facade {}({})", jp.getSignature().getName(), Arrays.toString(jp.getArgs()));
		return jp.proceed();
	}

	@Around("execution( * *(..)) && @annotation(griezma.proxyfairy.Logged)")
	public Object executeAroundLogged(ProceedingJoinPoint jp) throws Throwable {
		log.debug("Intercepted Logged {}({})", jp.getSignature().getName(), Arrays.toString(jp.getArgs()));
		return jp.proceed();
	}

	@Around("execution( * *(..)) && @annotation(griezma.proxyfairy.Timed)")
	public Object executeAroundTimed(ProceedingJoinPoint jp) throws Throwable {
		log.debug("Intercepted Timed {}({})", jp.getSignature().getName(), Arrays.toString(jp.getArgs()));
		long before = System.nanoTime();
		Object result = jp.proceed();
		long duration = System.nanoTime() - before;
		log.debug("{}({}) took {}mms", jp.getSignature().getName(), Arrays.toString(jp.getArgs()), duration / 1_000);
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

