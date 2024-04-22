package com.xiaogenban1993.http2thrift;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.TServiceClientFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

@Component
@Configuration
@Slf4j
public class ClientLoader {

    Map<String, TServiceClientFactory> serviceMap = new TreeMap<>();

    Map<TServiceClientFactory, Map<String, Method>> methodMap = new TreeMap<>(Comparator.comparing(a -> a.getClass().getName()));

    URLClassLoader classLoader;

    private void loadAll() throws Exception {
        if (classLoader != null) classLoader.close();
        // services目录下所有的jar包下中，SPI注入的ThriftClientFactory。
        classLoader = loadJars("./services");
        ServiceLoader<TServiceClientFactory> serviceLoader = ServiceLoader.load(TServiceClientFactory.class, classLoader);
        Iterator<TServiceClientFactory> it =  serviceLoader.iterator();
        while (it.hasNext()) {
            TServiceClientFactory factory = it.next();
            // 找到client类，把所有rpc方法注册
            Class<?> cliCls = factory.getClass().getDeclaringClass();
            String className = cliCls.getDeclaringClass().getName();
            log.info("className is loaded: " + className);
            // 工厂类注册到map里
            serviceMap.put(className, factory);
            for (Method m : cliCls.getDeclaredMethods()) {
                if (m.getName().startsWith("send_") || m.getName().startsWith("recv_")) continue;
                methodMap.computeIfAbsent(factory, k -> new TreeMap<>())
                        .put(m.getName(), m)
                ;
            }
        }
    }

    public Object invoke(String ip, int port, String className, String functionName, JSONObject arg0) {
        try (TTransport transport = new TSocket(ip, port)) {
            transport.open();
            TServiceClientFactory factory = serviceMap.get(className);
            if (factory == null) {
                throw new RuntimeException(String.format("该类名未注册，请先注册%s", className));
            }
            Method m = methodMap.getOrDefault(factory, new TreeMap<>()).get(functionName);
            if (m == null) {
                throw new RuntimeException(String.format("方法%s不存在于%s", functionName, className));
            }
            Object result = null;
            try {
                TServiceClient client = factory.getClient(new TBinaryProtocol(transport));
                Class inputClass = m.getParameterTypes()[0];
                Object arg0Object = JSONObject.parseObject(arg0.toString(), inputClass);
                result = m.invoke(client, arg0Object);
            } catch (Exception e) {
                throw new RuntimeException(String.format("调用%s.%s失败，参数%s", className, functionName, arg0), e);
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getAllClassNames() {
        return new ArrayList<>(serviceMap.keySet());
    }

    public List<String> getAllFunctions(String className) {
        TServiceClientFactory factory =  serviceMap.get(className);
        if (factory == null) {
            throw  new RuntimeException("className不存在，可能未注册 " + className);
        }
        return new ArrayList<>(methodMap.getOrDefault(factory, new TreeMap<>()).keySet());
    }

    public Map<String, Object> mock(String className, String functionName) {
        TServiceClientFactory factory =  serviceMap.get(className);
        if (factory == null) {
            throw  new RuntimeException("className不存在，可能未注册 " + className);
        }
        Method m = methodMap.getOrDefault(factory, new TreeMap<>()).get(functionName);
        if (m == null) {
            throw  new RuntimeException("functionName不存在于当前className " + functionName);
        }
        Class<?> inputCls = m.getParameterTypes()[0];

        try {
            Map<String, Object> resMap = JSONObject.parseObject(JSONObject.toJSONString(inputCls.newInstance(), SerializerFeature.WriteNullStringAsEmpty,
                    SerializerFeature.WriteNullStringAsEmpty,
                    SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteNullListAsEmpty,
                    SerializerFeature.IgnoreNonFieldGetter,
                    SerializerFeature.IgnoreErrorGetter
            ));

            Map<String, String> lowerKeys = new HashMap<>();

            resMap.forEach((key, val) -> {
                if (!lowerKeys.containsKey(key.toLowerCase())
                        || lowerKeys.get(key.toLowerCase()).compareTo(key) < 0
                ) {
                    lowerKeys.put(key.toLowerCase(),key);
                }
            });

            TreeMap<String, Object> finalMap = new TreeMap<>();

            for (String s : lowerKeys.keySet()) {
                finalMap.put(lowerKeys.get(s), resMap.get(lowerKeys.get(s)));
            }
            return finalMap;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static URLClassLoader loadJars(String directoryPath) throws Exception {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar"));
        URL[] urls;

        if (files != null) {
            urls = new URL[files.length];
            for (int i = 0; i < files.length; i++) {
                urls[i] = files[i].toURI().toURL();
            }
        } else {
            urls = new URL[0];
        }
        return new URLClassLoader(urls);
    }

	public synchronized void load() throws Exception {
		log.info("Start to load thrift files");
        // 创建一个ProcessBuilder实例
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("./"));
        builder.command("/bin/sh", "-c", "./gen.sh"); // 设置要运行的命令

        try {
            Process process = builder.start(); // 启动进程

            // 获取输入流
            InputStream is = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            // 读取命令的输出
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // 等待进程结束
            int exitCode = process.waitFor();
            System.out.println("Maven package completed with exit code " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
		try {
			Thread.sleep(1000L);
			loadAll();
			log.info("Load thrift files finish");
		} catch (Exception e) {
			log.error("Error loading", e);
		}
	}
}
