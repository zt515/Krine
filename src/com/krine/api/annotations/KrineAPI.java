package com.krine.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author kiva
 * @date 2017/4/9
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface KrineAPI{
}
