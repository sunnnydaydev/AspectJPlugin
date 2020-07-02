package com.sunnyday.aspectjplugin;

import android.util.Log;
import android.view.View;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

import static com.sunnyday.aspectjplugin.MainActivity.TAG;

/**
 * Create by SunnyDay on 21:45 2020/07/01
 */
@Aspect
public class HookViewClick {
    @Around("execution(* android.view.View.OnClickListener.onClick(..))")
    public void around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Log.d(TAG, "点击事件之前：触发埋点操作...");
        proceedingJoinPoint.proceed();
        Log.d(TAG, "点击事件之后...");
    }
    /**
     * 支持 ButterKnife @OnClick 注解
     *
    * @param joinPoint JoinPoint
     */
    @Before("execution(@butterknife.OnClick * *(android.view.View))")
    public void onButterknifeClickAOP(final JoinPoint joinPoint) {
        Log.d(TAG, "点击事件之前：触发埋点操作...");
    }
}
