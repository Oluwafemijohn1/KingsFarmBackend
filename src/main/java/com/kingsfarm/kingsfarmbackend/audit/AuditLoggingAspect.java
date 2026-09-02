package com.kingsfarm.kingsfarmbackend.audit;

import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import com.kingsfarm.kingsfarmbackend.systemlog.SystemLogService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns any {@code @Audited} method into a system_logs row on successful
 * return. Deliberately fails open: if evaluating the detail expression or
 * writing the log throws, that's logged and swallowed here rather than
 * propagated — a broken audit trail must never take down the actual
 * operation it was trying to describe.
 */
@Aspect
@Component
public class AuditLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingAspect.class);

    private final SystemLogService systemLogService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditLoggingAspect(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            AuthenticatedPrincipal principal = currentPrincipal();
            Long userId = principal != null ? principal.userId() : null;
            String username = principal != null ? principal.username() : "system";
            String detail = evaluateDetail(audited.detail(), joinPoint, result);

            if (audited.type() == LogType.AUDIT) {
                systemLogService.logAudit(userId, username, audited.module(), audited.action(), detail);
            } else {
                systemLogService.logActivity(userId, username, audited.module(), audited.action(), detail);
            }
        } catch (Exception ex) {
            log.warn("Audit logging failed for {}: {}", joinPoint.getSignature(), ex.getMessage());
        }
    }

    private AuthenticatedPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String evaluateDetail(String expression, JoinPoint joinPoint, Object result) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        context.setVariable("result", result);
        return parser.parseExpression(expression).getValue(context, String.class);
    }
}
