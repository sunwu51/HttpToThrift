package com.xiaogenban1993.http2thrift;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Controller
@CrossOrigin
public class RpcController {

    @Autowired
    ClientLoader clientLoader;

    @RequestMapping("/invoke/{remote}/{className}/{functionName}")
    public ResponseEntity<Object> invoke(@RequestBody JSONObject requestObject,
                                         @PathVariable String remote,
                                         @PathVariable String className,
                                         @PathVariable String functionName) {
        String[] strs = remote.split(":");
        if (strs.length != 2) {
            throw new IllegalArgumentException("Invalid parameters " + remote);
        }
        String ip = Objects.equals(strs[0], "") ? "127.0.0.1" : strs[0];
        int port = Integer.parseInt(strs[1]);
        Object res = clientLoader.invoke(ip, port, className, functionName, requestObject);


        // 去掉一些json序列化时多余的字段 和 大小写重复的字段
        Map<String, Object> resMap = JSONObject.parseObject(JSONObject.toJSONString(res, SerializerFeature.WriteNullStringAsEmpty,
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

        return ResponseEntity.ok(finalMap);
    }

    @RequestMapping("/className")
    public ResponseEntity<List<String>> className() {
        return ResponseEntity.ok(clientLoader.getAllClassNames());
    }

    @RequestMapping("/className/{className}")
    public ResponseEntity<List<String>> functionName(@PathVariable String className) {
        return ResponseEntity.ok(clientLoader.getAllFunctions(className));
    }

    @RequestMapping("/className/{className}/functionName/{functionName}")
    public ResponseEntity<Map<String, Object>> functionName(@PathVariable String className, @PathVariable String functionName) {
        return ResponseEntity.ok(clientLoader.mock(className, functionName));
    }


    @RequestMapping("/reload")
    public ResponseEntity<String> reload() throws Exception {
        clientLoader.load();
        return ResponseEntity.ok("ok");
    }
}
