package com.acmp.compute.k8s;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * K8s YAML 模板引擎：使用 Freemarker 渲染 k8s-templates 目录下的 .ftl 模板。
 * 用于生成 ResourceQuota、VolcanoJob、vLLM Deployment 等。
 */
@Slf4j
@Service
public class K8sTemplateEngine {

    private Configuration freemarkerConfig;

    @PostConstruct
    public void init() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "k8s-templates");
        freemarkerConfig.setDefaultEncoding(StandardCharsets.UTF_8.name());
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /**
     * 渲染指定模板，返回生成的 YAML 字符串。
     */
    public String render(String templateName, Map<String, Object> data) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(data, writer);
            return writer.toString();
        } catch (Exception e) {
            log.error("模板渲染失败: {}", templateName, e);
            throw new RuntimeException("模板渲染失败: " + templateName, e);
        }
    }
}
