package com.xiaogenban1993.http2thrift;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.*;


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

	public void startWatching() throws Exception {
		watchService = FileSystems.getDefault().newWatchService();
		registerDirectoryAndSubDirectories(appPath);
		while (true) {
			WatchKey key = watchService.take();
			if (key.pollEvents().isEmpty()) {
				key.reset();
				continue;
			}
			// 监听到文件变动后，延迟3s再做处理，有批量拷贝文件进来的情况，第一个和最后一个有时间间隔，避免频繁触发
			Thread.sleep(3000);
			key.pollEvents();
			log.info("File Changed.");
			clientLoader.load();
			key.reset();
		}
	}

	private void registerDirectoryAndSubDirectories(Path start) throws IOException {
		// 注册目录
		start.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
		// 获取目录流
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(start)) {
			for (Path path : stream) {
				if (Files.isDirectory(path)) {
					registerDirectoryAndSubDirectories(path);
				}
			}
		}
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
	}
}


