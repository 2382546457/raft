package com.alipay.sofa.jraft.util;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface SPI {

    String name() default "";

    int priority() default 0;
}