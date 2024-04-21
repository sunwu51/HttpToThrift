package com.xiaogenban1993.http2thrift;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;


@SpringBootApplication
@Slf4j
public class Application implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Autowired
	private ClientLoader clientLoader;

	private final Path appPath = Paths.get("./app");;

	private WatchService watchService;

	private final AtomicBoolean fileChanged = new AtomicBoolean(false);

	public void startWatching() throws Exception {
		watchService = FileSystems.getDefault().newWatchService();
		registerAll(appPath);
		while (true) {
			WatchKey key = watchService.take();
			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();
				if (fileChanged.compareAndSet(false, true)) {
					log.info("Watch changed will reload all jars");
					load();
					Thread.sleep(5000L);
					fileChanged.set(false);
				}
			}
			boolean valid = key.reset();
			if (!valid) {
				break;
			}
		}
	}

	private void registerAll(final Path start) throws IOException {
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
				dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Override
	public void run(String... args) throws Exception {
		new Thread(() -> {
			try {
				startWatching();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).start();
		load();
	}

	private synchronized void load() throws Exception {
		log.info("Start to load thrift files");
		Process process = Runtime.getRuntime().exec("sh -c ./gen.sh");
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			byte[] buffer = new byte[4096];
			int len = process.getInputStream().read(buffer);
			log.error("process exit: {}, info: {}", exitCode, new String(buffer, 0, len));
			return;
		}
		try {
			Thread.sleep(1000L);
			clientLoader.loadAll();
			log.info("Load thrift files finish");
		} catch (Exception e) {
			log.error("Error loading", e);
		}
	}
}


