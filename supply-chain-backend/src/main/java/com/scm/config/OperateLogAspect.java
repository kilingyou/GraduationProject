package com.scm.config;

import com.scm.module.system.entity.SysOperateLog;
import com.scm.module.system.mapper.SysOperateLogMapper;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

/**
 * Records mutating HTTP calls into {@code sys_operate_log}. Skips GET/HEAD/OPTIONS.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperateLogAspect {

    private static final int MAX_PARAMS_LEN = 2000;
    private static final int MAX_ERR_LEN = 1000;

    private final SysOperateLogMapper sysOperateLogMapper;

    @Around("execution(* com.scm.module..controller..*(..))")
    public Object aroundController(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return pjp.proceed();
        }
        HttpServletRequest request = attrs.getRequest();
        String httpMethod = request.getMethod();
        if ("GET".equalsIgnoreCase(httpMethod)
                || "HEAD".equalsIgnoreCase(httpMethod)
                || "OPTIONS".equalsIgnoreCase(httpMethod)) {
            return pjp.proceed();
        }

        SysOperateLog row = new SysOperateLog();
        row.setMethod(httpMethod + " " + request.getRequestURI());
        row.setIp(clientIp(request));
        row.setOperation(pjp.getSignature().toShortString());
        row.setParams(summarizeArgs(pjp.getArgs()));

        try {
            Object result = pjp.proceed();
            fillUser(row);
            row.setResultStatus(1);
            row.setOperationTime(LocalDateTime.now());
            insertQuietly(row);
            return result;
        } catch (Throwable ex) {
            fillUser(row);
            row.setResultStatus(0);
            row.setErrorMsg(truncate(ex.getMessage(), MAX_ERR_LEN));
            row.setOperationTime(LocalDateTime.now());
            insertQuietly(row);
            throw ex;
        }
    }

    private static void fillUser(SysOperateLog row) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            LoginUser u = (LoginUser) authentication.getPrincipal();
            row.setUserId(u.getUserId());
            row.setUsername(u.getUsername());
        }
    }

    private void insertQuietly(SysOperateLog row) {
        try {
            sysOperateLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("Failed to persist operate log: {}", e.getMessage());
        }
    }

    private static String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            if (a == null) {
                continue;
            }
            if (a instanceof HttpServletRequest || a instanceof HttpServletResponse) {
                continue;
            }
            if (a instanceof MultipartFile) {
                MultipartFile f = (MultipartFile) a;
                sb.append("MultipartFile{name=").append(f.getOriginalFilename())
                        .append(",size=").append(f.getSize()).append("};");
            } else {
                sb.append(a.getClass().getSimpleName()).append(";");
            }
        }
        return truncate(sb.toString(), MAX_PARAMS_LEN);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
