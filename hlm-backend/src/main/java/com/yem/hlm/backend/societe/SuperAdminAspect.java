package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.societe.annotation.RequiresSuperAdmin;
import com.yem.hlm.backend.common.error.ErrorCode;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SuperAdminAspect {

    @Before("@annotation(com.yem.hlm.backend.societe.annotation.RequiresSuperAdmin)")
    public void checkSuperAdmin() {
        if (!SocieteContext.isSuperAdmin()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    ErrorCode.SUPER_ADMIN_REQUIRED.name()
            );
        }
    }
}
